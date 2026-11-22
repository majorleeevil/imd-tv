package org.imdtv.iptvtv.data.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.imdtv.iptvtv.data.model.Kind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlobalFavouritesTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test fun favouritesSpanPlaylistsInUse() = runTest {
        val a = db.insertTestProvider("a"); val b = db.insertTestProvider("b")
        fun ch(p: Long, id: String) = ChannelEntity(providerId = p, categoryId = null, remoteId = id, name = id, logo = null, number = null, tvgId = null, streamUrl = "http://x/$id")
        db.channelDao().upsert(listOf(ch(a, "a1"), ch(b, "b1")))
        db.channelDao().byProvider(a).single().let { db.favouriteDao().add(FavouriteEntity(a, Kind.LIVE, it.id, 1)) }
        db.channelDao().byProvider(b).single().let { db.favouriteDao().add(FavouriteEntity(b, Kind.LIVE, it.id, 2)) }
        suspend fun favs(active: Long) = db.channelDao().observeFavourites(active).first().map { it.remoteId }.toSet()
        assertEquals(setOf("a1", "b1"), favs(a))
        db.providerDao().setEnabled(b, false) // a playlist not in use drops out...
        assertEquals(setOf("a1"), favs(a))
        assertEquals(setOf("a1", "b1"), favs(b)) // ...unless it is the active one
    }
}
