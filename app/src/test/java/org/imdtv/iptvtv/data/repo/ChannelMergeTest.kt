package org.imdtv.iptvtv.data.repo

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.inMemoryDb
import org.imdtv.iptvtv.data.db.insertTestProvider
import org.imdtv.iptvtv.data.settings.MergeBy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChannelMergeTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    @Test fun keysAndRanks() {
        assertEquals("bbc 1", mergeKey("BBC One HD"))
        assertEquals("bbc 1", mergeKey("BBC One HEVC"))
        assertEquals("bbc 1", mergeKey("BBC One 4K"))
        assertEquals("uk sky atlantic", mergeKey("UK: Sky Atlantic ᴴᴰ"))
        assertTrue(mergeKey("BBC Two HD") != mergeKey("BBC One HD"))
        assertEquals("", mergeKey("HD"))
        assertEquals(mergeKey("BBC ONE EAST MIDLANDS ◉"), mergeKey("BBC ONE EAST MIDLANDS ᴿᴬᵂ"))
        assertEquals(mergeKey("BBC One"), mergeKey("BBC Oneᴴᴰ"))
        assertEquals(3, qualityRank("BBC ONE EAST MIDLANDS ᴿᴬᵂ"))
        assertEquals(4, qualityRank("Sky Cinema ⁴ᴷ"))
        assertEquals("BBC One", baseName("BBC One HD"))
        assertEquals("UK: BBC One", baseName("UK: BBC One (4K)"))
        assertEquals("HD", baseName("HD"))
        assertEquals(mergeKey("Channel 1"), mergeKey("Channel One HD"))
        assertEquals(mergeKey("BBC One", listOf("UK:")), mergeKey("UK: BBC One", listOf("UK:")))
        assertEquals(listOf(4, 3, 2, 1, 0), listOf("BBC One 4K", "BBC One HEVC", "BBC One HD", "BBC One", "BBC One SD").map(::qualityRank))
    }

    @Test fun autoMergesPerCategoryAndUndoes() = runTest {
        val p = db.insertTestProvider()
        CatalogueSync(db).sync(p, FakeSource(liveCats = listOf(live("5", "UK"), live("6", "UK 4K")), channels = listOf(
            channel("1", "BBC One HD", number = 1), channel("2", "BBC One HEVC", number = 2), channel("3", "BBC One 4K", number = 3),
            channel("4", "BBC One 4K", cat = "6"), channel("5", "BBC Two HD", number = 5))))
        val dao = db.channelDao()
        val on = MergeOptions(on = true, across = false)
        ChannelMerge.applyAuto(db, p, on)

        val byRemote = dao.byProvider(p).associateBy { it.remoteId }
        val head = byRemote.getValue("3") // by Resolution the 4K one is kept, not the first in the guide
        assertTrue(head.mergeHead)
        assertEquals("BBC One", head.displayName(emptyList()))
        assertEquals(setOf("1", "2"), dao.byProvider(p).filter { it.mergedInto == head.id }.map { it.remoteId }.toSet())
        assertEquals(null, byRemote.getValue("4").mergedInto) // other category: left alone
        // Resolution picks the 4K source; no network involved.
        assertEquals("3", ChannelMerge.pick(db, OkHttpClient(), head, MergeBy.RESOLUTION, null, null).remoteId)

        // Across categories: the 4K copy in the other category joins too.
        ChannelMerge.applyAuto(db, p, on.copy(across = true))
        assertEquals(head.id, dao.get(byRemote.getValue("4").id)!!.mergedInto)
        ChannelMerge.applyAuto(db, p, on)
        assertEquals(null, dao.get(byRemote.getValue("4").id)!!.mergedInto)

        // A manual merge survives turning auto off; auto ones go.
        ChannelMerge.merge(db, byRemote.getValue("5"), byRemote.getValue("4"))
        db.providerDao().setMergeOn(p, false) // this playlist's own switch off
        ChannelMerge.applyAuto(db, p, on)
        val after = dao.byProvider(p).associateBy { it.remoteId }
        assertEquals(listOf("5"), after.values.filter { it.mergedInto != null }.map { it.remoteId })
        assertTrue(after.getValue("4").mergeHead && !after.getValue("1").mergeHead)

        ChannelMerge.unmerge(db, after.getValue("4"))
        assertTrue(dao.byProvider(p).none { it.mergedInto != null || it.mergeHead })
    }
}
