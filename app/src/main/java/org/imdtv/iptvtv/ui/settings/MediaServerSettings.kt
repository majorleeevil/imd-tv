package org.imdtv.iptvtv.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.AppContainer
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.db.ProviderEntity
import org.imdtv.iptvtv.data.db.ProviderType
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.model.StreamFormat
import org.imdtv.iptvtv.data.source.JellyfinException
import org.imdtv.iptvtv.data.source.JfAuth
import org.imdtv.iptvtv.data.source.PlexException
import org.imdtv.iptvtv.data.source.PlexServer
import org.imdtv.iptvtv.data.source.jellyfinBase
import org.imdtv.iptvtv.data.source.plexSkipByDefault
import org.imdtv.iptvtv.data.source.uiText

/** Where Settings › Media servers › Add Plex / Jellyfin server is up to. */
sealed interface ServerStep {
    data class Code(val code: String, @StringRes val expl: Int, val qr: String? = null) : ServerStep
    data class Working(@StringRes val text: Int) : ServerStep
    data class Servers(val list: List<PlexServer>) : ServerStep // Plex
    data object Address : ServerStep // Jellyfin
    data class SignIn(val name: String, val user: String = "", val password: String = "", val error: String? = null) : ServerStep // Jellyfin
    /** [provider] is the playlist to add, its skipLibraries filled in from [skip] by add(). */
    data class Libraries(val name: String, val libs: List<Triple<String, String, Kind>>, val skip: Set<String>, val provider: ProviderEntity) : ServerStep
    data class Adding(val progress: String) : ServerStep
    data class Done(val text: String) : ServerStep
    data class Failed(val text: String) : ServerStep
}

/** Sign in → libraries → a media-server playlist (Movies + Series only), synced but not made the one in use. */
abstract class ServerAddFlow(protected val c: AppContainer, private val scope: CoroutineScope, private val tag: String) {
    val step = MutableStateFlow<ServerStep?>(null)
    private var job: Job? = null

    abstract fun start()

    protected fun launchStep(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.w(tag, "Add $tag server failed", e)
                step.value = ServerStep.Failed(e.uiText(c.appContext) ?: e.toString())
            }
        }
    }

    protected fun libraries(name: String, libs: List<Triple<String, String, Kind>>, type: ProviderType, url: String, user: String, token: String) {
        step.value = ServerStep.Libraries(name, libs, libs.filter { plexSkipByDefault(it.second) }.map { it.first }.toSet(),
            ProviderEntity(name = name, type = type, url = url, username = user, password = token, userAgent = null, streamFormat = StreamFormat.TS,
                epgOverrideUrls = emptyList(), lastSync = null, contentOff = Kind.LIVE.name))
    }

    fun toggle(key: String) {
        val l = step.value as? ServerStep.Libraries ?: return
        step.value = l.copy(skip = if (key in l.skip) l.skip - key else l.skip + key)
    }

    fun add() {
        val l = step.value as? ServerStep.Libraries ?: return
        launchStep {
            step.value = ServerStep.Adding("")
            val id = c.providerRepository.add(l.provider.copy(skipLibraries = l.skip.joinToString(",")))
            try {
                val r = c.providerRepository.syncNow(id) { step.value = ServerStep.Adding(it) }
                step.value = ServerStep.Done(r.summary())
            } catch (e: Exception) { c.providerRepository.remove(id); throw e } // no half-added server
        }
    }

    fun cancel() { job?.cancel(); step.value = null }
}

/** Link code (plex.tv/link) → server → libraries. */
class PlexAddFlow(c: AppContainer, scope: CoroutineScope) : ServerAddFlow(c, scope, "Plex") {
    override fun start() = launchStep {
        val auth = c.plexAuth
        var token: String? = null
        while (token == null) { // a new code when one expires (15 min)
            val pin = auth.createPin()
            step.value = ServerStep.Code(pin.code, R.string.set_b_plex_code_expl, "https://plex.tv/link")
            token = try {
                var t: String? = null
                while (t == null) { delay(2_000); t = auth.pollPin(pin.id) }
                t
            } catch (e: PlexException) { if (e.code == 404) null else throw e }
        }
        step.value = ServerStep.Working(R.string.set_b_plex_working)
        val all = auth.servers(token)
        // Servers already added aren't offered again (the playlist's username is the server's client id).
        val added = c.db.providerDao().all().filter { it.type == ProviderType.PLEX }.mapTo(HashSet()) { it.username }
        val servers = all.filter { it.clientId !in added }
        step.value = when {
            all.isEmpty() -> ServerStep.Failed(c.appContext.getString(R.string.set_b_plex_no_servers))
            servers.isEmpty() -> ServerStep.Failed(c.appContext.getString(R.string.set_b_plex_all_added))
            else -> ServerStep.Servers(servers)
        }
    }

    fun pick(s: PlexServer) = launchStep {
        step.value = ServerStep.Working(R.string.set_b_plex_working)
        val uri = c.plexAuth.reachable(s) ?: return@launchStep run { step.value = ServerStep.Failed(c.appContext.getString(R.string.set_b_plex_unreachable, s.name)) }
        libraries(s.name, c.plexAuth.libraries(uri, s.token), ProviderType.PLEX, uri, s.clientId, s.token)
    }
}

/** A flow with a code step and a typed sign-in as the other way in (Jellyfin, Stremio). */
interface PasswordSignIn {
    @get:StringRes val usePasswordRes: Int
    @get:StringRes val userRes: Int
    @get:StringRes val signInExplRes: Int
    fun usePassword()
    fun setUser(u: String)
    fun setPassword(p: String)
    fun signIn()
}

/** Address → Quick Connect code (or username + password when it's off) → libraries. */
class JellyfinAddFlow(c: AppContainer, scope: CoroutineScope) : ServerAddFlow(c, scope, "Jellyfin"), PasswordSignIn {
    override val usePasswordRes = R.string.set_b_jf_use_password
    override val userRes = R.string.set_b_username
    override val signInExplRes = R.string.set_b_jf_sign_in_expl
    private var base = ""
    private var name = ""
    private val auth get() = c.jellyfinAuth

    override fun start() { step.value = ServerStep.Address }

    fun connect(typed: String) = launchStep {
        step.value = ServerStep.Working(R.string.set_b_jf_connecting)
        val typedBase = jellyfinBase(typed)
        val (b, n) = try { auth.resolve(typedBase) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.w("Jellyfin", "No Jellyfin at $typedBase", e)
            step.value = ServerStep.Failed(c.appContext.getString(R.string.set_b_plex_unreachable, typedBase)); return@launchStep
        }
        base = b; name = n
        if (!auth.quickConnectEnabled(base)) { step.value = ServerStep.SignIn(name); return@launchStep }
        var a: JfAuth? = null
        while (a == null) { // a new code when one expires
            val qc = auth.initiate(base)
            step.value = ServerStep.Code(qc.code, R.string.set_b_jf_code_expl)
            a = try {
                while (!auth.approved(base, qc.secret)) delay(2_000)
                auth.withQuickConnect(base, qc.secret)
            } catch (e: JellyfinException) { if (e.code == 404) null else throw e }
        }
        signedIn(a)
    }

    override fun usePassword() = launchStep { step.value = ServerStep.SignIn(name) } // also stops the Quick Connect polling
    override fun setUser(u: String) { (step.value as? ServerStep.SignIn)?.let { step.value = it.copy(user = u, error = null) } }
    override fun setPassword(p: String) { (step.value as? ServerStep.SignIn)?.let { step.value = it.copy(password = p, error = null) } }

    override fun signIn() {
        val s = step.value as? ServerStep.SignIn ?: return
        launchStep {
            step.value = ServerStep.Working(R.string.set_b_jf_connecting)
            val a = try { auth.withPassword(base, s.user, s.password) } catch (e: JellyfinException) {
                if (e.code != 401) throw e
                step.value = s.copy(error = c.appContext.getString(R.string.set_b_jf_wrong_login)); return@launchStep // try again, address kept
            }
            signedIn(a)
        }
    }

    private suspend fun signedIn(a: JfAuth) {
        step.value = ServerStep.Working(R.string.set_b_jf_connecting)
        libraries(name, auth.libraries(base, a), ProviderType.JELLYFIN, base, a.user.id, a.token)
    }
}

/** Link code (link.stremio.com, or its QR) or email + password → the account's add-on catalogs. */
class StremioAddFlow(c: AppContainer, scope: CoroutineScope) : ServerAddFlow(c, scope, "Stremio"), PasswordSignIn {
    override val usePasswordRes = R.string.set_b_stremio_use_password
    override val userRes = R.string.set_b_email
    override val signInExplRes = R.string.set_b_stremio_sign_in_expl
    private val auth get() = c.stremioAuth

    override fun start() = launchStep {
        var key: String? = null
        while (key == null) { // a new code every 10 minutes
            val l = auth.createLink()
            step.value = ServerStep.Code(l.code, R.string.set_b_stremio_code_expl, l.url)
            repeat(300) { if (key == null) { delay(2_000); key = auth.readLink(l.code) } }
        }
        signedIn(key!!)
    }

    override fun usePassword() = launchStep { step.value = ServerStep.SignIn("Stremio") }
    override fun setUser(u: String) { (step.value as? ServerStep.SignIn)?.let { step.value = it.copy(user = u, error = null) } }
    override fun setPassword(p: String) { (step.value as? ServerStep.SignIn)?.let { step.value = it.copy(password = p, error = null) } }

    override fun signIn() {
        val s = step.value as? ServerStep.SignIn ?: return
        launchStep {
            step.value = ServerStep.Working(R.string.set_b_jf_connecting)
            val key = try { auth.withPassword(s.user.trim(), s.password) } catch (e: org.imdtv.iptvtv.data.source.StremioException) {
                step.value = s.copy(error = e.uiText(c.appContext) ?: e.text); return@launchStep // try again, email kept
            }
            signedIn(key)
        }
    }

    private suspend fun signedIn(key: String) {
        step.value = ServerStep.Working(R.string.set_b_jf_connecting)
        val cats = auth.catalogs(auth.addons(key))
        if (cats.isEmpty()) { step.value = ServerStep.Failed(c.appContext.getString(R.string.set_b_stremio_no_catalogs)); return }
        libraries("Stremio", cats, ProviderType.STREMIO, "https://api.strem.io", "", key)
    }
}

/** The media server kinds, each a page of Settings › Media servers. */
val SERVER_TYPES = listOf(ProviderType.PLEX, ProviderType.JELLYFIN, ProviderType.STREMIO)
private fun ProviderType.serverName() = when (this) { ProviderType.PLEX -> "Plex"; ProviderType.JELLYFIN -> "Jellyfin"; else -> "Stremio" }
@StringRes private fun ProviderType.addRes() = when (this) { ProviderType.PLEX -> R.string.set_b_plex_add; ProviderType.JELLYFIN -> R.string.set_b_jf_add; else -> R.string.set_b_stremio_add }
@StringRes private fun ProviderType.addExplRes() = when (this) { ProviderType.PLEX -> R.string.set_b_plex_add_expl; ProviderType.JELLYFIN -> R.string.set_b_jf_add_expl; else -> R.string.set_b_stremio_add_expl }

/** Settings › Media servers: Plex ›, Jellyfin › (the servers added, or what adding one gives), then the update interval. */
@Composable
fun MediaServerRows(vm: SettingsViewModel, first: Modifier, openers: Map<ProviderType, FocusRequester>, onOpen: (ProviderType) -> Unit) {
    val providers by vm.providers.collectAsState()
    SERVER_TYPES.forEachIndexed { i, t ->
        val names = providers.filter { it.type == t }.joinToString { it.name }
        SettingRow(t.serverName(), names.ifEmpty { stringResource(t.addExplRes()) }, "›",
            (if (i == 0) first else Modifier).focusRequester(openers.getValue(t))) { onOpen(t) }
    }
    PlaylistUpdateRow(vm)
}

/** Settings › Media servers › Plex or Jellyfin: Add … server, then the servers added (hidden while adding one). Back closes it. */
@Composable
fun MediaServerPage(vm: SettingsViewModel, type: ProviderType, modifier: Modifier, first: FocusRequester, focusFirst: Boolean,
                    openers: MutableMap<Long, FocusRequester>, onOpen: (Long) -> Unit, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val providers by vm.providers.collectAsState()
    val flow = when (type) { ProviderType.PLEX -> vm.plex; ProviderType.JELLYFIN -> vm.jellyfin; else -> vm.stremio }
    val step by flow.step.collectAsState()
    androidx.compose.runtime.LaunchedEffect(type) { if (focusFirst) { withFrameNanos { }; runCatching { first.requestFocus() } } }
    androidx.compose.foundation.layout.Column(modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        androidx.tv.material3.Text(type.serverName(), fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        ServerFlowRows(flow, first, stringResource(type.addRes()), stringResource(type.addExplRes()))
        if (type == ProviderType.STREMIO && step == null) androidx.tv.material3.Text(stringResource(R.string.set_b_stremio_limit), fontSize = 12.sp,
            color = org.imdtv.iptvtv.ui.theme.LocalTheme.current.colors.muted, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        if (step == null || step is ServerStep.Done || step is ServerStep.Failed) providers.filter { it.type == type }.forEach { p ->
            SettingRow(p.name, updatedLabel(p.lastSync), "›", Modifier.focusRequester(openers.getOrPut(p.id) { FocusRequester() })) { onOpen(p.id) }
        }
    }
}

@Composable
private fun ServerFlowRows(flow: ServerAddFlow, top: FocusRequester, addTitle: String, addExpl: String) {
    val step by flow.step.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(flow) { onDispose { if (flow.step.value !is ServerStep.Adding) flow.cancel() } } // stop polling on leaving
    // One top row for every step, so the focused row is never removed (focus would jump to the groups and leave).
    fun stay(then: () -> Unit) { runCatching { top.requestFocus() }; then() }
    val s = step
    val (title, expl) = when (s) {
        null -> addTitle to addExpl
        is ServerStep.Code -> stringResource(R.string.set_b_plex_code, s.code) to stringResource(s.expl)
        is ServerStep.Working -> stringResource(s.text) to ""
        is ServerStep.Servers -> stringResource(R.string.set_b_plex_pick_server) to ""
        ServerStep.Address -> stringResource(R.string.set_b_jf_address) to stringResource(R.string.set_b_jf_address_expl)
        is ServerStep.SignIn -> stringResource(R.string.set_b_jf_sign_in_title, s.name) to (s.error ?: stringResource((flow as PasswordSignIn).signInExplRes))
        is ServerStep.Libraries -> stringResource(R.string.set_b_plex_pick_libraries, s.name) to ""
        is ServerStep.Adding -> stringResource(R.string.set_b_plex_adding) to s.progress
        is ServerStep.Done -> stringResource(R.string.set_b_plex_added) to s.text
        is ServerStep.Failed -> stringResource(R.string.set_b_plex_failed_title) to s.text
    }
    SettingRow(title, expl, null, Modifier.focusRequester(top), busy = s is ServerStep.Working || s is ServerStep.Adding) {
        when (s) {
            null -> flow.start()
            is ServerStep.Code, ServerStep.Address, is ServerStep.SignIn, is ServerStep.Done, is ServerStep.Failed -> flow.cancel()
            else -> Unit
        }
    }
    if (s is ServerStep.Code && s.qr != null) androidx.compose.foundation.Image(qrImage(s.qr), contentDescription = s.qr,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color.White).padding(10.dp).size(150.dp))
    when (s) {
        is ServerStep.Servers -> s.list.forEach { srv -> SettingRow(srv.name, "", null) { stay { (flow as PlexAddFlow).pick(srv) } } }
        // Save closes the box (focus back on its row) first; a frame later focus goes to the top row before the box's row goes.
        ServerStep.Address -> EditRow(stringResource(R.string.set_b_jf_address_row), "", null, "http://", saveLabel = stringResource(R.string.set_b_jf_connect)) { typed ->
            scope.launch { withFrameNanos { }; stay { (flow as JellyfinAddFlow).connect(typed) } }
        }
        is ServerStep.Code -> if (flow is PasswordSignIn) SettingRow(stringResource(flow.usePasswordRes), "", null) { stay { flow.usePassword() } }
        is ServerStep.SignIn -> {
            val jf = flow as PasswordSignIn
            EditRow(stringResource(jf.userRes), "", s.user.ifEmpty { null }, s.user) { jf.setUser(it) }
            EditRow(stringResource(R.string.set_b_password), "", if (s.password.isEmpty()) null else "••••", "", secret = true) { jf.setPassword(it) }
            SettingRow(stringResource(R.string.set_b_jf_sign_in), "", null) { stay { jf.signIn() } }
        }
        is ServerStep.Libraries -> {
            s.libs.forEach { (key, title, kind) ->
                SwitchRow(title, stringResource(if (kind == Kind.MOVIE) R.string.set_a_kind_movies else R.string.set_a_kind_series), key !in s.skip) { flow.toggle(key) }
            }
            SettingRow(stringResource(R.string.set_b_plex_add_server), stringResource(R.string.set_b_plex_add_server_expl), null) { stay { flow.add() } }
        }
        else -> Unit
    }
    if (s != null && s !is ServerStep.Adding && s !is ServerStep.Done && s !is ServerStep.Failed)
        SettingRow(stringResource(R.string.live_cancel), "", null, Modifier.padding(top = 4.dp)) { stay { flow.cancel() } }
}
