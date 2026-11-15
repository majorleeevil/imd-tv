package org.imdtv.iptvtv.data.catalogue

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.db.inMemoryDb
import org.imdtv.iptvtv.data.db.insertTestProvider
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.repo.RailRepository

@RunWith(RobolectricTestRunner::class)
class CategoryNameTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private fun cat(name: String) = CategoryEntity(providerId = 1, kind = Kind.LIVE, remoteId = "1", name = name)

    @Test fun categoryNameStripsAndRenames() =
        assertEquals("GENERAL HEVC", categoryName(cat("UK| GENERAL ʰᵉᵛᶜ"), listOf("UK|", "hevc=HEVC")))

    @Test fun categoryNameNeverEmpty() {
        assertEquals("UK|", categoryName(cat("UK|"), listOf("UK|")))
        assertEquals("Uncategorised", categoryName(cat(""), emptyList()))
    }

    @Test fun railUsesCategoryStrip() = runTest {
        val p = db.insertTestProvider()
        db.categoryDao().upsert(listOf(CategoryEntity(providerId = p, kind = Kind.LIVE, remoteId = "1", name = "UK| News")))
        val c = db.categoryDao().byRemoteIds(p, Kind.LIVE).single().id
        db.channelDao().upsert(listOf(ChannelEntity(providerId = p, categoryId = c, remoteId = "a", name = "a", logo = null,
            number = null, tvgId = null, streamUrl = "http://s/a")))
        val repo = RailRepository(db)
        assertEquals("UK| News", repo.rail(p).first().last().entry.title)
        db.providerDao().setCategoryStrip(p, "UK|")
        assertEquals("News", repo.rail(p).first().last().entry.title)
    }
}
