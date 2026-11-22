package org.imdtv.iptvtv.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.FavouriteEntity
import org.imdtv.iptvtv.data.db.HideRuleEntity
import org.imdtv.iptvtv.data.db.ProviderEntity
import org.imdtv.iptvtv.data.db.ProviderType
import org.imdtv.iptvtv.data.db.inMemoryDb
import org.imdtv.iptvtv.data.db.insertTestProvider
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.model.Movie
import org.imdtv.iptvtv.data.model.StreamFormat
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
class CatalogueSyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var sync: CatalogueSync

    @Before fun setUp() { db = inMemoryDb(); sync = CatalogueSync(db) }
    @After fun tearDown() { db.close() }

    private suspend fun liveCat(p: Long, remoteId: String) =
        db.categoryDao().byRemoteIds(p, Kind.LIVE).single { it.remoteId == remoteId }

    private suspend fun chan(p: Long, remoteId: String) =
        db.channelDao().byProvider(p).singleOrNull { it.remoteId == remoteId }

    @Test
    fun preservesUserFieldsOnRename() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("5", "UK News")))
        sync.sync(p, src)
        db.categoryDao().update(liveCat(p, "5").copy(hidden = true, sortOrder = 9))

        src.liveCats = listOf(live("5", "UK | News"))
        sync.sync(p, src)

        val c = liveCat(p, "5")
        assertEquals("UK | News", c.name)
        assertTrue(c.hidden)
        assertEquals(9, c.sortOrder)
    }

    @Test
    fun countryRedetectedHiddenKept() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("1", "UK| News")))
        sync.sync(p, src)
        assertEquals("GB", liveCat(p, "1").country)
        db.categoryDao().update(liveCat(p, "1").copy(hidden = true, sortOrder = 9))

        src.liveCats = listOf(live("1", "US| News"))
        sync.sync(p, src)

        val c = liveCat(p, "1")
        assertEquals("US", c.country)
        assertTrue(c.hidden)
        assertEquals(9, c.sortOrder)
    }

    @Test
    fun newCategoriesFollowTvLanguageExistingKeepTheirState() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("1", "AR| News")))
        sync.sync(p, src) // setting off: shown
        val matching = CatalogueSync(db, keepLanguage = { "English" })

        src.liveCats = listOf(live("1", "AR| News"), live("2", "AR| Kids"), live("3", "UK| Kids"), live("4", "Sports"))
        matching.sync(p, src)

        assertFalse(liveCat(p, "1").hidden)
        assertTrue(liveCat(p, "2").hidden)
        assertFalse(liveCat(p, "3").hidden)
        assertFalse(liveCat(p, "4").hidden)
    }

    @Test
    fun preservesChannelCustomisation() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("5", "News")), channels = listOf(channel("101", "BBC One HD", number = 5)))
        sync.sync(p, src)
        db.channelDao().update(chan(p, "101")!!.copy(customName = "BBC1", customNumber = 1, hidden = true, epgOffsetMinutes = 30))

        src.channels = listOf(channel("101", "BBC One FHD", number = 7))
        sync.sync(p, src)

        val c = chan(p, "101")!!
        assertEquals("BBC One FHD", c.name)
        assertEquals(7, c.number)
        assertEquals("BBC1", c.customName)
        assertEquals(1, c.customNumber)
        assertTrue(c.hidden)
        assertEquals(30, c.epgOffsetMinutes)
        assertEquals(liveCat(p, "5").id, c.categoryId)
    }

    @Test
    fun logoFieldsSurviveSync() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("5", "News")), channels = listOf(channel("101", "BBC One", logo = "old.png")))
        sync.sync(p, src)
        db.channelDao().update(chan(p, "101")!!.copy(customLogo = "c", packLogo = "p", builtinLogo = "b"))

        src.channels = listOf(channel("101", "BBC One", logo = "new.png"))
        sync.sync(p, src)

        val c = chan(p, "101")!!
        assertEquals("new.png", c.logo)
        assertEquals("c", c.customLogo); assertEquals("p", c.packLogo); assertEquals("b", c.builtinLogo)
    }

    @Test
    fun channelMovesCategory() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("5", "News"), live("6", "Sport")), channels = listOf(channel("101", "X", cat = "5")))
        sync.sync(p, src)
        src.channels = listOf(channel("101", "X", cat = "6"))
        sync.sync(p, src)
        assertEquals(liveCat(p, "6").id, chan(p, "101")!!.categoryId)
    }

    @Test
    fun deletesRemoved() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("5", "News")), channels = listOf(channel("101", "A"), channel("102", "B")))
        sync.sync(p, src)
        val id101 = chan(p, "101")!!.id
        db.favouriteDao().add(FavouriteEntity(p, Kind.LIVE, id101, addedAt = 1))
        db.favouriteDao().add(FavouriteEntity(p, Kind.LIVE, chan(p, "102")!!.id, addedAt = 2))

        src.channels = listOf(channel("102", "B"))
        sync.sync(p, src)

        assertNull(chan(p, "101"))
        assertEquals(listOf(chan(p, "102")!!.id), db.favouriteDao().list(p, Kind.LIVE).map { it.itemId })
    }

    @Test
    fun hideRuleAppliesToNewOnly() = runTest {
        val p = db.insertTestProvider()
        db.ruleDao().insertHideRule(HideRuleEntity(providerId = p, kind = Kind.LIVE, regex = """^(AR|FR)\b"""))
        val src = FakeSource(liveCats = listOf(live("1", "AR Sports"), live("2", "UK News")))
        sync.sync(p, src)
        db.categoryDao().update(liveCat(p, "1").copy(hidden = false)) // user un-hides

        src.liveCats = listOf(live("1", "AR Sports"), live("2", "UK News"), live("3", "FR Kids"))
        val r = sync.sync(p, src)

        assertTrue(liveCat(p, "3").hidden)
        assertFalse(liveCat(p, "1").hidden)
        assertFalse(liveCat(p, "2").hidden)
        assertEquals(1, r.newlyHidden)
    }

    @Test
    fun newCategoriesSortAfterExisting() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("1", "A"), live("2", "B")))
        sync.sync(p, src)
        db.categoryDao().update(liveCat(p, "1").copy(sortOrder = 20))
        src.liveCats = listOf(live("1", "A"), live("2", "B"), live("3", "C"))
        sync.sync(p, src)
        assertEquals(21, liveCat(p, "3").sortOrder)
    }

    @Test
    fun syncResultCountsAndMovies() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(
            liveCats = listOf(live("5", "News")),
            channels = listOf(channel("101", "A"), channel("101", "A dup"), channel("102", "B")),
            vodCats = listOf(org.imdtv.iptvtv.data.model.Category("9", "Films", Kind.MOVIE)),
            movieList = listOf(Movie("501", "9", "Film", null, 2020, 7.0, "http://h/movie/u/p/501.mkv")),
        )
        val r = sync.sync(p, src)
        assertEquals(SyncResult(categories = 2, channels = 2, movies = 1, series = 0, newlyHidden = 0), r)
        assertEquals("Films", db.categoryDao().byRemoteIds(p, Kind.MOVIE).single().name)
        assertEquals(db.categoryDao().byRemoteIds(p, Kind.MOVIE).single().id, db.movieDao().byProvider(p).single().categoryId)
    }

    @Test
    fun kindSwitchedOffDuringItsFetchIsRemovedAfterwards() = runTest {
        val p = db.insertTestProvider()
        var asked = 0 // on when the movies step starts, off by the time it ends (switched off mid-sync)
        sync.sync(p, FakeSource(movieList = listOf(Movie("501", null, "Film", null, null, null, "http://h/m.mkv"))),
            loads = { it != Kind.MOVIE || asked++ == 0 })
        assertTrue(db.movieDao().byProvider(p).isEmpty())
    }

    @Test
    fun kindSwitchedOffIsRemovedAndNotFetched() = runTest {
        val p = db.insertTestProvider()
        sync.sync(p, FakeSource(channels = listOf(channel("101", "A"))))
        val r = sync.sync(p, FakeSource(channels = listOf(channel("101", "A"))), loads = { it != Kind.LIVE })
        assertEquals(0, r.channels)
        assertTrue(db.channelDao().byProvider(p).isEmpty())
        assertTrue(db.categoryDao().byRemoteIds(p, Kind.LIVE).isEmpty())
    }

    @Test
    fun reportsProgressPerKind() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(channels = listOf(channel("101", "A"), channel("102", "B")))
        val steps = mutableListOf<String>()
        val kinds = mutableListOf<Kind>()
        sync.sync(p, src, onProgress = { steps += it }, onKind = { kinds += it })
        assertEquals(listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES), kinds)
        assertEquals(
            listOf(
                "Downloading channels…",
                "Downloading movies… · 2 channels saved",
                "Downloading series… · 2 channels, 0 movies saved",
            ),
            steps,
        )
    }

    @Test
    fun seriesDeletionRemovesItsProgressOnly() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(seriesList = listOf(
            org.imdtv.iptvtv.data.model.Series("s1", null, "One", null, null, null),
            org.imdtv.iptvtv.data.model.Series("s2", null, "Two", null, null, null),
        ))
        sync.sync(p, src)
        val s1 = db.seriesDao().byProvider(p).single { it.remoteId == "s1" }
        val s2 = db.seriesDao().byProvider(p).single { it.remoteId == "s2" }
        fun row(id: Long, ep: String) = org.imdtv.iptvtv.data.db.ProgressEntity(p, Kind.SERIES, id, 10, 100, 1, false, ep)
        db.progressDao().upsert(row(s1.id, "e1"))
        db.progressDao().upsert(row(s1.id, "e2"))
        db.progressDao().upsert(row(s2.id, "e1"))
        db.favouriteDao().add(FavouriteEntity(p, Kind.SERIES, s1.id, 1))

        src.seriesList = src.seriesList.filter { it.remoteId == "s2" }
        sync.sync(p, src)

        assertEquals(listOf(s2.id to "e1"), db.progressDao().all(p).map { it.itemId to it.episode })
        assertTrue(db.favouriteDao().list(p, Kind.SERIES).isEmpty())
    }

    @Test
    fun uncategorisedItemsGetSyntheticCategory() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(
            liveCats = listOf(live("5", "News")),
            channels = listOf(channel("101", "In news", cat = "5"), channel("102", "No group", cat = null), channel("103", "Unknown group", cat = "99")),
        )
        val r = sync.sync(p, src)

        val cats = db.categoryDao().observeVisible(p, Kind.LIVE).first()
        val unc = cats.single { it.remoteId == CatalogueSync.UNCATEGORISED }
        assertEquals("Uncategorised", unc.name)
        assertEquals(listOf("102", "103"), db.channelDao().observeByCategory(unc.id).first().map { it.remoteId })
        assertEquals(2, r.categories)

        src.channels = listOf(channel("101", "In news", cat = "5"))
        sync.sync(p, src)
        assertEquals(listOf("5"), db.categoryDao().byRemoteIds(p, Kind.LIVE).map { it.remoteId })
    }

    @Test
    fun skippedCountReported() = runTest {
        val p = db.insertTestProvider()
        val src = FakeSource(liveCats = listOf(live("5", "News")), skippedCount = 3)
        assertEquals(3, sync.sync(p, src).skipped)
    }

    // --- ProviderRepository ---

    private fun TestScope.repo(source: FakeSource, now: () -> Long = { 0L }) = ProviderRepository(
        db = db,
        sync = sync,
        sourceFor = { source },
        prefs = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("prefs.preferences_pb") },
        now = now,
    )

    private fun m3uProvider(epg: List<String>) = ProviderEntity(
        name = "M", type = ProviderType.M3U, url = "http://h/list.m3u", username = null, password = null,
        userAgent = null, streamFormat = StreamFormat.TS, epgOverrideUrls = epg, lastSync = null,
    )

    @Test
    fun addCreatesEpgSourcesAndDefaultRules() = runTest {
        val repo = repo(FakeSource())
        val id = repo.add(m3uProvider(listOf("http://e/1.xml", "http://e/2.xml.gz")))

        val sources = db.epgDao().sources(id)
        assertEquals(listOf("http://e/1.xml", "http://e/2.xml.gz"), sources.map { it.url })
        assertEquals(listOf(0, 1), sources.map { it.priority })
        assertEquals(listOf(0, 0), sources.map { it.offsetMinutes })
        val rules = db.ruleDao().nameRules(id)
        assertEquals(5, rules.size)
        assertTrue(rules.all { it.enabled })
        assertEquals(listOf(0, 1, 2, 3, 4), rules.map { it.order })
        assertEquals(org.imdtv.iptvtv.data.epg.BuiltinRule.entries.map { it.name }, rules.map { it.pattern })
        assertEquals(id, repo.activeProviderId.first())
    }

    @Test
    fun addUsesSourceEpgUrlsWhenNoOverride() = runTest {
        val repo = repo(FakeSource(epg = listOf("http://h/xmltv.php")))
        val id = repo.add(m3uProvider(emptyList()))
        assertEquals(listOf("http://h/xmltv.php"), db.epgDao().sources(id).map { it.url })
    }

    @Test
    fun syncIfStaleOnlySyncsOldCatalogues() = runTest {
        var clock = 0L
        val src = FakeSource(liveCats = listOf(live("5", "News")))
        val repo = repo(src) { clock }
        val id = repo.add(m3uProvider(emptyList()))

        clock = 1_000
        repo.syncIfStale(id) // never synced → syncs
        assertEquals(1_000L, db.providerDao().get(id)!!.lastSync)

        src.liveCats = listOf(live("5", "News"), live("6", "Sport"))
        clock = 1_000 + 1.hours.inWholeMilliseconds
        repo.syncIfStale(id) // fresh → skipped
        assertEquals(1, db.categoryDao().byRemoteIds(id, Kind.LIVE).size)

        clock = 1_000 + 25.hours.inWholeMilliseconds
        repo.syncIfStale(id)
        assertEquals(2, db.categoryDao().byRemoteIds(id, Kind.LIVE).size)
    }

    @Test
    fun removingDeletesItsWholeCatalogue() = runTest {
        val src = FakeSource(liveCats = listOf(live("5", "News")), channels = listOf(channel("1", "BBC")),
            vodCats = listOf(org.imdtv.iptvtv.data.model.Category("9", "Films", Kind.MOVIE)), movieList = listOf(Movie("501", "9", "Film", null, null, null, "http://h/m.mkv")),
            seriesList = listOf(org.imdtv.iptvtv.data.model.Series("s1", null, "One", null, null, null)))
        val repo = repo(src)
        val keep = repo.add(m3uProvider(emptyList()))
        val id = repo.add(m3uProvider(emptyList()))
        repo.syncNow(keep); repo.syncNow(id)
        repo.remove(id)
        assertEquals(listOf(0, 0, 0, 0), listOf(db.channelDao().byProvider(id).size, db.movieDao().byProvider(id).size, db.seriesDao().byProvider(id).size,
            db.categoryDao().byRemoteIds(id, Kind.LIVE).size))
        assertEquals(1, db.channelDao().byProvider(keep).size) // the other playlist untouched
        assertEquals(emptySet<Long>(), repo.removing.value)
    }

    @Test
    fun removingTheActiveProviderClearsActive() = runTest {
        val repo = repo(FakeSource())
        val id = repo.add(m3uProvider(emptyList()))
        repo.remove(id)
        assertNull(db.providerDao().get(id))
        assertNull(repo.activeProviderId.first())
    }
}
