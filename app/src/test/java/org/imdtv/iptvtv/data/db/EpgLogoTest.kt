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
class EpgLogoTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test fun copiesTheLinkedGuideIconAndClearsItWhenUnlinked() = runTest {
        val p = db.insertTestProvider()
        fun ch(id: String) = ChannelEntity(providerId = p, categoryId = null, remoteId = id, name = id, logo = null, number = null, tvgId = null, streamUrl = "http://x/$id")
        db.channelDao().upsert(listOf(ch("a"), ch("b")))
        val (a, b) = db.channelDao().byProvider(p).sortedBy { it.remoteId }.map { it.id }
        val s = db.epgDao().insertSource(EpgSourceEntity(providerId = p, url = "u", priority = 0))
        db.epgDao().insertChannels(listOf(
            EpgChannelEntity(s, "x", "X", "x", "http://icons/x.png"),
            EpgChannelEntity(s, "y", "Y", "y", ""),  // an empty icon is no icon
        ))
        db.epgDao().upsertLinks(listOf(EpgLinkEntity(a, s, "x", manual = false), EpgLinkEntity(b, s, "y", manual = false)))
        db.epgDao().refreshEpgLogos(p)
        assertEquals("http://icons/x.png", db.channelDao().get(a)!!.epgLogo)
        assertNull(db.channelDao().get(b)!!.epgLogo)

        db.epgDao().deleteLink(a)
        db.epgDao().refreshEpgLogos(p)
        assertNull(db.channelDao().get(a)!!.epgLogo)
    }
}
