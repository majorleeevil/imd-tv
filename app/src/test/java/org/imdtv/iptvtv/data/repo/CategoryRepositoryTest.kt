package org.imdtv.iptvtv.data.repo

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
import org.imdtv.iptvtv.data.db.inMemoryDb
import org.imdtv.iptvtv.data.db.insertTestProvider
import org.imdtv.iptvtv.data.model.Kind

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository

    @Before fun setUp() { db = inMemoryDb(); repo = CategoryRepository(db) }
    @After fun tearDown() { db.close() }

    private suspend fun order(p: Long) = db.categoryDao().observeVisible(p, Kind.LIVE).first().map { it.remoteId }
    private suspend fun id(p: Long, r: String) = db.categoryDao().byRemoteIds(p, Kind.LIVE).single { it.remoteId == r }.id

    @Test
    fun moveRenumbersAndClamps() = runTest {
        val p = db.insertTestProvider()
        db.categoryDao().upsert(listOf(
            CategoryEntity(providerId = p, kind = Kind.LIVE, remoteId = "c1", name = "A", sortOrder = 0),
            CategoryEntity(providerId = p, kind = Kind.LIVE, remoteId = "c2", name = "B", sortOrder = 0),
            CategoryEntity(providerId = p, kind = Kind.LIVE, remoteId = "c3", name = "C", sortOrder = 5),
            CategoryEntity(providerId = p, kind = Kind.MOVIE, remoteId = "m", name = "M", sortOrder = 0),
        ))

        repo.move(p, Kind.LIVE, id(p, "c3"), -1)
        assertEquals(listOf("c1", "c3", "c2"), order(p))

        repo.move(p, Kind.LIVE, id(p, "c1"), -5)
        assertEquals(listOf("c1", "c3", "c2"), order(p))
        repo.move(p, Kind.LIVE, id(p, "c1"), 9)
        assertEquals(listOf("c3", "c2", "c1"), order(p))
        assertEquals(listOf(0, 1, 2), db.categoryDao().observeVisible(p, Kind.LIVE).first().map { it.sortOrder })
    }

    @Test
    fun hiddenAndOrderSurviveSync() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("1", "A"), live("2", "B"), live("3", "C")),
            channels = listOf(channel("x", "X", cat = "1")))
        val sync = CatalogueSync(db)
        sync.sync(p, src)
        db.categoryDao().setHidden(id(p, "2"), true)
        repo.move(p, Kind.LIVE, id(p, "3"), -2)

        sync.sync(p, src)

        assertEquals(listOf("3", "1"), order(p))
    }

    @Test
    fun matchLanguageHidesOthersAndOffShowsAll() = runTest {
        val p = db.insertTestProvider()
        fun c(kind: Kind, r: String, country: String?) = CategoryEntity(providerId = p, kind = kind, remoteId = r, name = r, country = country)
        db.categoryDao().upsert(listOf(c(Kind.LIVE, "gb", "GB"), c(Kind.LIVE, "sa", "SA"), c(Kind.SERIES, "en", "lang:English"),
            c(Kind.SERIES, "tr", "lang:Turkish"), c(Kind.MOVIE, "none", null)))

        repo.matchLanguage(p, "English")
        val shown = { k: Kind -> kotlinx.coroutines.runBlocking { db.categoryDao().observeVisible(p, k).first().map { it.remoteId } } }
        assertEquals(listOf("gb"), shown(Kind.LIVE))
        assertEquals(listOf("en"), shown(Kind.SERIES))
        assertEquals(listOf("none"), shown(Kind.MOVIE))

        repo.matchLanguage(p, null)
        assertEquals(listOf("tr"), shown(Kind.SERIES) - "en")
        assertEquals(2, shown(Kind.LIVE).size)
    }

    @Test
    fun moveChannelRenumbersKeepsHiddenAndSurvivesSync() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("1", "A")),
            channels = listOf(channel("a", "A", cat = "1", number = 1), channel("b", "B", cat = "1", number = 2), channel("c", "C", cat = "1", number = 3)))
        val sync = CatalogueSync(db)
        sync.sync(p, src)
        val cat = id(p, "1")
        val chs = db.channelDao().byProvider(p).associate { it.remoteId to it.id }
        db.channelDao().setHidden(chs.getValue("b"), true)
        suspend fun all() = db.channelDao().allByCategory(cat).map { it.remoteId }
        suspend fun shown() = db.channelDao().observeByCategory(cat).first().map { it.remoteId }

        repo.moveChannel(cat, chs.getValue("c"), -1)
        assertEquals(listOf("a", "c", "b"), all())
        repo.moveChannel(cat, chs.getValue("c"), -1)
        assertEquals(listOf("c", "a", "b"), all())
        repo.moveChannel(cat, chs.getValue("c"), -1) // clamped at the top
        assertEquals(listOf("c", "a"), shown())

        // A new channel from the provider goes after the placed ones.
        sync.sync(p, FakeSource(liveCats = src.liveCats, channels = src.channels + channel("d", "D", cat = "1", number = 0)))
        assertEquals(listOf("c", "a", "b", "d"), all())
    }
}
