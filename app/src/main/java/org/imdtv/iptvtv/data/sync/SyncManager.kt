package org.imdtv.iptvtv.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.imdtv.iptvtv.data.backup.BackupRepository
import org.imdtv.iptvtv.data.security.Secrets
import org.imdtv.iptvtv.data.support.lanAddress
import kotlin.coroutines.resume

enum class SyncRole { OFF, MASTER, FOLLOWER }

/** [key] is the hex key, encrypted with [Secrets]. */
@Serializable
data class FollowerBox(val id: String, val name: String, val key: String, val lastSynced: Long? = null)

@Serializable
data class MasterBox(val name: String, val host: String, val port: Int, val followerId: String, val key: String,
                     val lastSynced: Long? = null, val lastHash: String? = null)

/** This box's sync role and pairings. Per box: never in a backup or a snapshot. */
@Serializable
data class SyncState(val role: SyncRole = SyncRole.OFF, val followers: List<FollowerBox> = emptyList(), val master: MasterBox? = null)

/** A master found on the network. */
data class FoundBox(val name: String, val host: String, val port: Int)

private const val TAG = "IptvSync"
/** Android 14's advertiser wants names unique across service types, and a TV's own services already use its name. */
private const val SERVICE_SUFFIX = " (IMD TV)"
private val stateJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * LAN settings sync (plan 2026-11-17): a master serves its backup + logins, sealed per follower; followers pull it and
 * mirror it on start, on return to the app and every 6 h.
 */
class SyncManager(
    private val context: Context,
    private val prefs: DataStore<Preferences>,
    private val backup: BackupRepository,
    private val afterMirror: suspend (org.imdtv.iptvtv.data.backup.MirrorResult) -> Unit,
    private val scope: CoroutineScope,
) {
    private val stateKey = stringPreferencesKey("sync_state")
    val state: StateFlow<SyncState> = prefs.data.map { stored(it) }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, SyncState())

    /** Master: the pairing whose code is on screen. */
    val pairing = MutableStateFlow<Pairing?>(null)
    /** Follower: the last pull's problem, null when it went fine. */
    val problem = MutableStateFlow<String?>(null)
    val pulling = MutableStateFlow(false)

    private var server: Server? = null
    private val pullLock = Mutex()
    private val nsd get() = context.getSystemService(NsdManager::class.java)
    val boxName: String get() = android.provider.Settings.Global.getString(context.contentResolver, "device_name")
        ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL

    private fun stored(p: Preferences) = p[stateKey]?.let { runCatching { stateJson.decodeFromString(SyncState.serializer(), it) }.getOrNull() } ?: SyncState()
    private suspend fun current() = stored(prefs.data.first())
    private suspend fun edit(f: (SyncState) -> SyncState) = prefs.edit { p -> p[stateKey] = stateJson.encodeToString(SyncState.serializer(), f(stored(p))) }

    /** Once per process: the master's server follows the role, as does the follower's 6 h worker. */
    fun start() {
        scope.launch {
            // The stored role, not [state]'s placeholder Off first (that would cancel the follower's worker).
            prefs.data.map { stored(it).role }.distinctUntilChanged().collect { role ->
                if (role == SyncRole.MASTER) startServer() else stopServer()
                // runCatching: JVM tests boot the app without WorkManager.
                runCatching { if (role == SyncRole.FOLLOWER) org.imdtv.iptvtv.work.SyncWorker.schedule(context) else org.imdtv.iptvtv.work.SyncWorker.cancel(context) }
                    .onFailure { Log.w(TAG, "Sync worker not (un)scheduled", it) }
            }
        }
    }

    /** The app came to the front: a master re-advertises if its address changed; a follower pulls. */
    fun onForeground() {
        scope.launch {
            when (current().role) {
                SyncRole.MASTER -> if (server?.addr != lanAddress()) { stopServer(); startServer() }
                SyncRole.FOLLOWER -> pull()
                SyncRole.OFF -> Unit
            }
        }
    }

    /** A new role forgets the old pairings. */
    suspend fun setRole(r: SyncRole) = edit { if (it.role == r) it else SyncState(role = r) }
    suspend fun removeFollower(id: String) = edit { s -> s.copy(followers = s.followers.filter { it.id != id }) }
    suspend fun forgetMaster() = edit { it.copy(master = null) }

    // --- Master ---

    private fun startServer() {
        if (server != null) return
        val addr = lanAddress() ?: return Log.w(TAG, "No LAN address; not serving").let { }
        server = runCatching { Server(addr) }.onFailure { Log.w(TAG, "Sync server failed", it) }.getOrNull()
    }

    private fun stopServer() { server?.stop(); server = null; pairing.value = null }

    private inner class Server(val addr: InetAddress) {
        private val socket = ServerSocket(0, 4, addr)
        private val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Log.i(TAG, "Advertised as ${info.serviceName}").let { }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Log.w(TAG, "NSD register failed $code").let { }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }

        init {
            Thread({ loop() }, "sync-server").apply { isDaemon = true }.start()
            nsd.registerService(NsdServiceInfo().apply { serviceName = boxName + SERVICE_SUFFIX; serviceType = SYNC_SERVICE; port = socket.localPort },
                NsdManager.PROTOCOL_DNS_SD, reg)
        }

        fun stop() { runCatching { nsd.unregisterService(reg) }; runCatching { socket.close() } }

        private fun loop() {
            while (!socket.isClosed) {
                try { socket.accept().use { handle(it) } } catch (e: IOException) { /* closed, or a client went away */ }
                catch (e: Exception) { Log.w(TAG, "Sync request failed", e) }
            }
        }

        private fun handle(c: Socket) {
            c.soTimeout = 5000
            val inp = c.getInputStream()
            val head = StringBuilder()
            while (head.length < 8192 && !head.endsWith("\r\n\r\n")) { val b = inp.read(); if (b < 0) break; head.append(b.toChar()) }
            val out = c.getOutputStream()
            fun reply(code: String, body: ByteArray = ByteArray(0), extra: String = "") {
                out.write("HTTP/1.1 $code\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n$extra\r\n".toByteArray())
                out.write(body); out.flush()
            }
            val r = parseSyncRequest(head.toString()) ?: return reply("404 Not Found")
            when (r.path) {
                SyncPath.HELLO -> {
                    pairing.value = Pairing(cleanName(runCatching { java.net.URLDecoder.decode(r.headers["x-name"].orEmpty(), "UTF-8") }.getOrNull()))
                    reply("200 OK")
                }
                SyncPath.PAIR -> {
                    val p = pairing.value
                    if (p == null || !p.tryCode(r.headers["x-code"].orEmpty())) {
                        if (p != null && !p.open) pairing.value = null
                        return reply("403 Forbidden")
                    }
                    pairing.value = null
                    val id = newFollowerId()
                    val key = newSyncKey().hex()
                    runBlocking { edit { s -> s.copy(followers = s.followers + FollowerBox(id, p.followerName, Secrets.encrypt(key)!!)) } }
                    reply("200 OK", "$id $key".toByteArray())
                }
                SyncPath.SNAPSHOT -> {
                    val f = runBlocking { current() }.followers.find { it.id == r.headers["x-follower"] } ?: return reply("403 Forbidden")
                    val key = Secrets.decrypt(f.key)?.unhex() ?: return reply("403 Forbidden")
                    val json = runBlocking { encodeSnapshot(backup.syncSnapshot()) }.toByteArray()
                    val hash = sha256(json)
                    runBlocking { edit { s -> s.copy(followers = s.followers.map { if (it.id == f.id) it.copy(lastSynced = System.currentTimeMillis()) else it }) } }
                    if (hash == r.headers["x-hash"]) reply("304 Not Modified")
                    else reply("200 OK", seal(key, json), "Content-Type: application/octet-stream\r\nX-Hash: $hash\r\n")
                }
            }
        }
    }

    // --- Follower ---

    /** Masters advertising on the network, resolved one at a time (older Android allows one resolve at once). */
    fun browse(): Flow<List<FoundBox>> = callbackFlow {
        val found = linkedMapOf<String, FoundBox>()
        val queue = kotlinx.coroutines.channels.Channel<NsdServiceInfo>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val resolver = launch {
            for (info in queue) resolve(info)?.let { if (it.name != boxName) synchronized(found) { found[it.name] = it; trySend(found.values.toList()) } }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { queue.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) = synchronized(found) { if (found.remove(info.serviceName.removeSuffix(SERVICE_SUFFIX)) != null) trySend(found.values.toList()) }.let { }
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { Log.w(TAG, "NSD discovery failed $code"); close() }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        trySend(emptyList())
        nsd.discoverServices(SYNC_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { resolver.cancel(); runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    @Suppress("DEPRECATION") // resolveService: the replacement needs API 34
    private suspend fun resolve(info: NsdServiceInfo): FoundBox? = withTimeoutOrNull(5000) {
        suspendCancellableCoroutine { k ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) { if (k.isActive) k.resume(null) }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val host = i.host?.hostAddress
                    if (k.isActive) k.resume(host?.let { FoundBox(i.serviceName.removeSuffix(SERVICE_SUFFIX), it, i.port) })
                }
            })
        }
    }

    private suspend fun request(host: String, port: Int, path: SyncPath, headers: Map<String, String>): Pair<Int, Pair<ByteArray, String?>> =
        withContext(Dispatchers.IO) {
            val c = URL("http", host, port, path.path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = path.method; c.connectTimeout = 5000; c.readTimeout = 30_000; c.useCaches = false
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (path.method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
                val code = c.responseCode
                code to ((if (code == 200) c.inputStream.use { it.readBytes() } else ByteArray(0)) to c.getHeaderField("X-Hash"))
            } finally { c.disconnect() }
        }

    /** Asks [m] to show a pairing code. */
    suspend fun hello(m: FoundBox): Boolean = runCatching { request(m.host, m.port, SyncPath.HELLO, mapOf("X-Name" to java.net.URLEncoder.encode(boxName, "UTF-8"))).first == 200 }.getOrDefault(false)

    /** Sends the code typed in; on success [m] becomes this box's master and a first pull runs. */
    suspend fun pair(m: FoundBox, code: String): Boolean {
        val (status, body) = runCatching { request(m.host, m.port, SyncPath.PAIR, mapOf("X-Name" to boxName, "X-Code" to code)) }.getOrNull() ?: return false
        if (status != 200) return false
        val (id, key) = String(body.first).split(' ').takeIf { it.size == 2 } ?: return false
        edit { it.copy(master = MasterBox(m.name, m.host, m.port, id, Secrets.encrypt(key)!!)) }
        problem.value = null
        scope.launch { pull() }
        return true
    }

    /** Pulls and mirrors the master's snapshot; quiet when the master can't be reached (the problem shows on its row). [force]: even if unchanged. */
    suspend fun pull(force: Boolean = false) = pullLock.withLock {
        val m = current().master?.takeIf { current().role == SyncRole.FOLLOWER } ?: return@withLock
        val key = Secrets.decrypt(m.key)?.unhex() ?: return@withLock run { problem.value = "Pair with the master again" }
        pulling.value = true
        try {
            val t0 = System.currentTimeMillis()
            val headers = mapOf("X-Follower" to m.followerId, "X-Hash" to (m.lastHash?.takeIf { !force } ?: ""))
            var at = m
            var notCopied: String? = null
            val res = runCatching { request(m.host, m.port, SyncPath.SNAPSHOT, headers) }.getOrNull()
                ?: run {
                    // The master's address or port changed (DHCP, app restart): find it again by name.
                    val f = withTimeoutOrNull(8000) { browse().first { l -> l.any { it.name == m.name } }.first { it.name == m.name } }
                        ?: return@withLock run { problem.value = "${m.name} isn't reachable: is it on with IMD TV open?" }
                    at = m.copy(host = f.host, port = f.port)
                    runCatching { request(f.host, f.port, SyncPath.SNAPSHOT, headers) }.getOrNull()
                        ?: return@withLock run { problem.value = "${m.name} isn't reachable: is it on with IMD TV open?" }
                }
            val (status, body) = res
            when (status) {
                304 -> Unit
                200 -> {
                    val t1 = System.currentTimeMillis()
                    val snap = decodeSnapshot(String(open(key, body.first)))
                    val r = backup.mirror(snap) {}
                    val t2 = System.currentTimeMillis()
                    afterMirror(r)
                    Log.i(TAG, "Pull ${body.first.size} B: fetch ${t1 - t0} ms, apply ${t2 - t1} ms, after ${System.currentTimeMillis() - t2} ms (guide changed: ${r.guideChanged})")
                    // A playlist left out is tried again when the master changes or on Sync now (a forced pull).
                    at = at.copy(lastHash = body.second)
                    if (r.failed.isNotEmpty()) notCopied = "Not copied: ${r.failed.joinToString()}"
                }
                403 -> return@withLock run { problem.value = "${m.name} no longer allows this box: choose it again to pair" }
                else -> return@withLock run { problem.value = "Sync failed (${m.name} answered $status)" }
            }
            val done = at.copy(lastSynced = System.currentTimeMillis())
            edit { s -> if (s.master?.followerId == m.followerId) s.copy(master = done) else s }
            problem.value = notCopied
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Sync pull failed", e)
            problem.value = "Sync failed: ${e.message ?: e::class.simpleName}"
        } finally {
            pulling.value = false
        }
    }
}

/** Shown on the master's screen: printable, one line, short. */
internal fun cleanName(s: String?) = s.orEmpty().filter { !it.isISOControl() }.trim().take(40).ifEmpty { "A box" }
