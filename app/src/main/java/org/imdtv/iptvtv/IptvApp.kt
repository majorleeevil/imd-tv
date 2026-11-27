package org.imdtv.iptvtv

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.imdtv.iptvtv.data.db.login

class IptvApp : Application(), coil.ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(org.imdtv.iptvtv.ui.AppLanguage.wrap(base))

    override fun onCreate() {
        super.onCreate()
        org.imdtv.iptvtv.data.support.CrashReport.install(this)
        container = AppContainer(this)
        // Once per old playlist: encrypt its login and take it out of saved links (no-op afterwards).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            org.imdtv.iptvtv.data.support.LogCollector.start(this@IptvApp)
            container.sync.start()
            container.search // created here, not lazily on the main thread (it registers a Room observer)
            runCatching { container.providerRepository.sealOldRows() }.onFailure { android.util.Log.e("IptvApp", "Sealing logins failed", it) }
            // A fresh process runs no recording: alarms are set again (boot and updates clear them) and cut-off rows closed.
            runCatching { container.recordingScheduler.rearm { container.recordings.running.value.keys } }
                .onFailure { android.util.Log.e("IptvApp", "Setting recording alarms failed", it) }
        }
    }

    /** Coil's loader with one change: Plex image links get their server's token back (saved with PASS_TOKEN, see PlexSource). */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).okHttpClient {
        okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request()
            if (r.url.queryParameter("X-Plex-Token") != org.imdtv.iptvtv.data.security.PASS_TOKEN) return@addInterceptor chain.proceed(r)
            val login = plexLogins(r.url.host)
            chain.proceed(r.newBuilder().url(org.imdtv.iptvtv.data.security.withLogin(r.url.toString(), login)).build())
        }.build()
    }.build()

    @Volatile private var plexByHost: Map<String, org.imdtv.iptvtv.data.security.Login> = emptyMap()
    /** On Coil's IO thread; re-reads the Plex playlists when a host is new (a server just added). */
    private fun plexLogins(host: String) = plexByHost[host] ?: kotlinx.coroutines.runBlocking {
        container.db.providerDao().all().filter { it.type == org.imdtv.iptvtv.data.db.ProviderType.PLEX }
            .mapNotNull { p -> p.login()?.let { (p.url.toHttpUrlOrNull()?.host ?: return@mapNotNull null) to it } }.toMap()
    }.also { plexByHost = it }[host]
}
