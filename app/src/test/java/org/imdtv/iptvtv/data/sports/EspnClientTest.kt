package org.imdtv.iptvtv.data.sports

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** #58: answers that can't have changed are read from the cache instead of asking ESPN again. */
class EspnClientTest {
    private val asked = mutableListOf<String>()
    private val agents = mutableListOf<String?>()
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        asked += chain.request().url.toString(); agents += chain.request().header("User-Agent")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("""{"events":[],"children":[]}""".toResponseBody("application/json".toMediaType())).build()
    }.build()
    private val espn = EspnClient(http, Json, kotlin.io.path.createTempDirectory("espn").toFile())
    private val pl = leagueById("soccer/eng.1")!!

    @Test fun pastDaysAndStandingsAreAskedOnceTodayEveryTime() = runBlocking {
        repeat(2) { espn.day(listOf(pl), LocalDate.now().minusDays(5)) }
        repeat(2) { espn.standings(pl) }
        repeat(2) { espn.day(listOf(pl), LocalDate.now()) }
        assertEquals(4, asked.size) // past day 1, standings 1, today 2
        assertTrue(agents.all { it!!.startsWith("IMD TV/") })
    }
}
