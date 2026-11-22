package org.imdtv.iptvtv.data.db

import kotlinx.coroutines.test.runTest
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.source.addedMs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VodSortTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test fun playlistAlphabeticalAndRecentlyAdded() = runTest {
        val p = db.insertTestProvider()
        db.categoryDao().upsert(listOf(CategoryEntity(providerId = p, kind = Kind.MOVIE, remoteId = "1", name = "Films")))
        val cat = db.categoryDao().allKinds(p).single().id
        fun m(name: String, added: Long) = MovieEntity(providerId = p, categoryId = cat, remoteId = name, name = name, poster = null, year = null, rating = null, streamUrl = null, addedAt = added)
        db.movieDao().upsert(listOf(m("B", 10), m("C", 30), m("A", 20))) // playlist order B, C, A
        suspend fun names(sort: String) = db.movieDao().inCategory(cat, 10, sort).map { it.name }
        assertEquals(listOf("B", "C", "A"), names("PLAYLIST"))
        assertEquals(listOf("A", "B", "C"), names("AZ"))
        assertEquals(listOf("C", "A", "B"), names("ADDED"))
    }

    @Test fun stalkerAddedDates() {
        assertEquals(1714588200000L, addedMs("2024-05-01 18:30:00"))
        assertNull(addedMs(null)); assertNull(addedMs("0000-00-00 00:00:00"))
    }
}
