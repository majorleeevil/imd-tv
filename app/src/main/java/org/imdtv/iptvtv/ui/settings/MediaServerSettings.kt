package org.imdtv.iptvtv.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
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
import org.imdtv.iptvtv.data.source.PlexException
import org.imdtv.iptvtv.data.source.PlexServer
import org.imdtv.iptvtv.data.source.plexSkipByDefault
import org.imdtv.iptvtv.data.source.uiText

/** Where Settings › Media servers › Add Plex server is up to. */
sealed interface PlexStep {
    data class Code(val code: String) : PlexStep
    data object Working : PlexStep
    data class Servers(val list: List<PlexServer>) : PlexStep
    data class Libraries(val server: PlexServer, val uri: String, val libs: List<Triple<String, String, Kind>>, val skip: Set<String>) : PlexStep
    data class Adding(val progress: String) : PlexStep
    data class Done(val text: String) : PlexStep
    data class Failed(val text: String) : PlexStep
}

/** Link code → server → libraries → a Plex playlist (Movies + Series only), synced but not made the one in use. */
class PlexAddFlow(private val c: AppContainer, private val scope: CoroutineScope) {
    val step = MutableStateFlow<PlexStep?>(null)
    private var job: Job? = null

    private fun launchStep(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                android.util.Log.w("Plex", "Add Plex server failed", e)
                step.value = PlexStep.Failed(e.uiText(c.appContext) ?: e.toString())
            }
        }
    }

    fun start() = launchStep {
        val auth = c.plexAuth
        var token: String? = null
        while (token == null) { // a new code when one expires (15 min)
            val pin = auth.createPin()
            step.value = PlexStep.Code(pin.code)
            token = try {
                var t: String? = null
                while (t == null) { delay(2_000); t = auth.pollPin(pin.id) }
                t
            } catch (e: PlexException) { if (e.code == 404) null else throw e }
        }
        step.value = PlexStep.Working
        val servers = auth.servers(token)
        step.value = if (servers.isEmpty()) PlexStep.Failed(c.appContext.getString(R.string.set_b_plex_no_servers)) else PlexStep.Servers(servers)
    }

    fun pick(s: PlexServer) = launchStep {
        step.value = PlexStep.Working
        val uri = c.plexAuth.reachable(s) ?: return@launchStep run { step.value = PlexStep.Failed(c.appContext.getString(R.string.set_b_plex_unreachable, s.name)) }
        val libs = c.plexAuth.libraries(uri, s.token)
        step.value = PlexStep.Libraries(s, uri, libs, libs.filter { plexSkipByDefault(it.second) }.map { it.first }.toSet())
    }

    fun toggle(key: String) {
        val l = step.value as? PlexStep.Libraries ?: return
        step.value = l.copy(skip = if (key in l.skip) l.skip - key else l.skip + key)
    }

    fun add() {
        val l = step.value as? PlexStep.Libraries ?: return
        launchStep {
            step.value = PlexStep.Adding("")
            val p = ProviderEntity(name = l.server.name, type = ProviderType.PLEX, url = l.uri, username = l.server.clientId, password = l.server.token,
                userAgent = null, streamFormat = StreamFormat.TS, epgOverrideUrls = emptyList(), lastSync = null,
                contentOff = Kind.LIVE.name, skipLibraries = l.skip.joinToString(","))
            val id = c.providerRepository.add(p)
            try {
                val r = c.providerRepository.syncNow(id) { step.value = PlexStep.Adding(it) }
                step.value = PlexStep.Done(r.summary())
            } catch (e: Exception) { c.providerRepository.remove(id); throw e } // no half-added server
        }
    }

    fun cancel() { job?.cancel(); step.value = null }
}

/** Settings › Media servers: the Plex servers added, and Add Plex server. */
@Composable
fun MediaServerRows(vm: SettingsViewModel, first: Modifier, openers: MutableMap<Long, FocusRequester>, onOpen: (Long) -> Unit) {
    val flow = vm.plex
    val step by flow.step.collectAsState()
    val providers by vm.providers.collectAsState()
    DisposableEffect(Unit) { onDispose { if (flow.step.value !is PlexStep.Adding) flow.cancel() } } // stop polling on leaving
    val muted = Color(0xFFB0B8C8)
    when (val s = step) {
        null -> SettingRow(stringResource(R.string.set_b_plex_add), stringResource(R.string.set_b_plex_add_expl), null, first) { flow.start() }
        is PlexStep.Code -> {
            SettingRow(stringResource(R.string.set_b_plex_code, s.code), stringResource(R.string.set_b_plex_code_expl), null, first) { flow.cancel() }
        }
        PlexStep.Working -> SettingRow(stringResource(R.string.set_b_plex_working), "", null, first, busy = true) { }
        is PlexStep.Servers -> {
            Text(stringResource(R.string.set_b_plex_pick_server), fontSize = 14.sp, color = muted)
            s.list.forEachIndexed { i, srv -> SettingRow(srv.name, "", null, if (i == 0) first else Modifier) { flow.pick(srv) } }
        }
        is PlexStep.Libraries -> {
            Text(stringResource(R.string.set_b_plex_pick_libraries, s.server.name), fontSize = 14.sp, color = muted)
            s.libs.forEachIndexed { i, (key, title, kind) ->
                SwitchRow(title, stringResource(if (kind == Kind.MOVIE) R.string.set_a_kind_movies else R.string.set_a_kind_series), key !in s.skip,
                    if (i == 0) first else Modifier) { flow.toggle(key) }
            }
            SettingRow(stringResource(R.string.set_b_plex_add_server), stringResource(R.string.set_b_plex_add_server_expl), null) { flow.add() }
        }
        is PlexStep.Adding -> SettingRow(stringResource(R.string.set_b_plex_adding), s.progress, null, first, busy = true) { }
        is PlexStep.Done -> SettingRow(stringResource(R.string.set_b_plex_added), s.text, null, first) { flow.cancel() }
        is PlexStep.Failed -> SettingRow(stringResource(R.string.set_b_plex_failed_title), s.text, null, first) { flow.cancel() }
    }
    if (step != null && step !is PlexStep.Adding && step !is PlexStep.Done)
        SettingRow(stringResource(R.string.live_cancel), "", null, Modifier.padding(top = 4.dp)) { flow.cancel() }
    providers.filter { it.type == ProviderType.PLEX }.forEach { p ->
        SettingRow(p.name, updatedLabel(p.lastSync), "›", Modifier.focusRequester(openers.getOrPut(p.id) { FocusRequester() })) { onOpen(p.id) }
    }
}
