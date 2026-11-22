package org.imdtv.iptvtv.data.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChangeNumberTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test fun takingAUsedNumberSwapsAndNoNumberMovesPastTheEnd() = runTest {
        val p = db.insertTestProvider()
        fun ch(id: String, n: Int?) = ChannelEntity(providerId = p, categoryId = null, remoteId = id, name = id, logo = null, number = n, tvgId = null, streamUrl = "http://x/$id")
        val dao = db.channelDao()
        dao.upsert(listOf(ch("a", 1), ch("b", 389), ch("c", null)))
        fun shown(e: ChannelEntity) = e.customNumber ?: e.number
        suspend fun get(r: String) = dao.byProvider(p).first { it.remoteId == r }

        dao.changeNumber(get("b"), 1) // swap: b → 1, a → 389
        assertEquals(1, shown(get("b"))); assertEquals(389, shown(get("a")))

        dao.changeNumber(get("b"), 389) // back again: both on their playlist numbers, stored as none
        assertNull(get("a").customNumber); assertNull(get("b").customNumber)

        dao.changeNumber(get("c"), 1) // c had no number: a moves past the last one
        assertEquals(1, shown(get("c"))); assertEquals(390, shown(get("a")))
    }
}
