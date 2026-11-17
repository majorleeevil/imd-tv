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
class ChannelOrderTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private suspend fun channels(p: Long): List<ChannelEntity> {
        db.channelDao().upsert((1..3).map { ChannelEntity(providerId = p, categoryId = null, remoteId = "$it", name = "$it", logo = null,
            number = null, tvgId = null, streamUrl = "http://s/$it") })
        return db.channelDao().byProvider(p).sortedBy { it.remoteId }
    }

    @Test fun groupMembersFollowSavedOrder() = runTest {
        val p = db.insertTestProvider()
        val (a, b, c) = channels(p)
        val g = db.customGroupDao().create(p, "Mine")
        listOf(a, b, c).forEach { db.customGroupDao().addChannel(g, it.id) }
        listOf(c, a, b).forEachIndexed { n, ch -> db.customGroupDao().setMemberOrder(g, ch.id, n) }
        assertEquals(listOf("3", "1", "2"), db.customGroupDao().observeChannels(g).first().map { it.remoteId })
    }

    @Test fun favouritesFollowSavedOrderThenNewestFirst() = runTest {
        val p = db.insertTestProvider()
        val (a, b, c) = channels(p)
        listOf(a, b).forEachIndexed { i, ch -> db.favouriteDao().add(FavouriteEntity(p, Kind.LIVE, ch.id, i + 1L)) }
        assertEquals(listOf("2", "1"), db.channelDao().observeFavourites(p).first().map { it.remoteId }) // unordered: newest first
        listOf(a, b).forEachIndexed { n, ch -> db.favouriteDao().setSortOrder(p, Kind.LIVE, ch.id, n) }
        db.favouriteDao().add(FavouriteEntity(p, Kind.LIVE, c.id, 3))
        assertEquals(listOf("3", "1", "2"), db.channelDao().observeFavourites(p).first().map { it.remoteId }) // new one on top
    }
}
