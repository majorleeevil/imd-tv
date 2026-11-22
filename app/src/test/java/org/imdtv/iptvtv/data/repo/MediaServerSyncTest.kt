package org.imdtv.iptvtv.data.repo

import kotlinx.coroutines.test.runTest
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.inMemoryDb
import org.imdtv.iptvtv.data.db.insertTestProvider
import org.imdtv.iptvtv.data.model.Category
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.model.Movie
import org.imdtv.iptvtv.data.source.LibraryPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Changed-only updates for media servers: a library with nothing removed keeps its stored titles, otherwise it's fetched whole. */
@RunWith(RobolectricTestRunner::class)
class MediaServerSyncTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private class Server : org.imdtv.iptvtv.data.source.PlaylistSource by FakeSource() {
        val libs = mutableMapOf<String, MutableMap<String, Pair<String, Long>>>() // library → id → (title, updated)
        val calls = mutableListOf<Pair<String, Long?>>()
        override suspend fun vodCategories() = libs.keys.map { Category(it, it, Kind.MOVIE) }
        override suspend fun moviesIn(cat: String, since: Long?, fetched: (Int, Int?) -> Unit): LibraryPage<Movie> {
            calls += cat to since
            val all = libs.getValue(cat)
            val items = all.filter { since == null || it.value.second >= since }.map { (id, v) -> Movie(id, cat, v.first, null, null, null, "http://s/$id") }
            fetched(items.size, items.size)
            return LibraryPage(items, all.size, since != null)
        }
    }

    @Test fun onlyChangesWhenNothingWasRemoved() = runTest {
        val p = db.insertTestProvider()
        val s = Server()
        s.libs["A"] = mutableMapOf("a1" to ("A1" to 0L), "a2" to ("A2" to 0L))
        s.libs["B"] = mutableMapOf("b1" to ("B1" to 0L), "b2" to ("B2" to 0L))
        CatalogueSync(db).sync(p, s)
        assertEquals(listOf("A" to null, "B" to null), s.calls.sortedBy { it.first })

        s.calls.clear()
        s.libs.getValue("A")["a3"] = "A3" to 100L         // A: one added
        s.libs.getValue("B").remove("b2")                 // B: one removed…
        s.libs.getValue("B")["b1"] = "B1 renamed" to 100L // …and one changed
        val steps = mutableListOf<String>()
        val r = CatalogueSync(db).sync(p, s, onProgress = { steps += it }, since = 50L)

        assertEquals(listOf("A" to 50L, "B" to 50L, "B" to null), s.calls.sortedWith(compareBy({ it.first }, { it.second ?: Long.MAX_VALUE })))
        val stored = db.movieDao().byProvider(p).associate { it.remoteId to it.name }
        assertEquals(mapOf("a1" to "A1", "a2" to "A2", "a3" to "A3", "b1" to "B1 renamed"), stored)
        assertEquals(4, r.movies) // kept titles count as saved
        assertTrue(steps.toString(), steps.any { it.startsWith("Downloading movies… ") } && steps.any { it.startsWith("Saving movies… ") })
    }
}
