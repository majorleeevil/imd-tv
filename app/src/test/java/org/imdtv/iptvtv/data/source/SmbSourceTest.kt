package org.imdtv.iptvtv.data.source

import org.imdtv.iptvtv.data.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbSourceTest {
    @Test fun addressesInEveryShape() {
        assertEquals(Triple("172.16.0.253", "claude", ""), parseShareAddress("""172.16.0.253\claude"""))
        assertEquals(Triple("nas", "Media", "Films/HD"), parseShareAddress("""\\nas\Media\Films\HD\"""))
        assertEquals(Triple("nas", "Media", "Films"), parseShareAddress("smb://nas/Media/Films/"))
        assertEquals(Triple("nas", "Media", ""), parseShareAddress("nas/Media"))
        assertNull(parseShareAddress("nas"))
        assertNull(parseShareAddress(""))
    }

    @Test fun movieTitlesAndYears() {
        assertEquals("Test Pattern" to 2024, movieTitle("Test Pattern (2024)"))
        assertEquals("Another Film" to 2019, movieTitle("Another.Film.2019.1080p"))
        assertEquals("Blade Runner 2049" to 2017, movieTitle("Blade Runner 2049 (2017)"))
        assertEquals("2012" to null, movieTitle("2012"))
        assertEquals("Home Video" to null, movieTitle("Home_Video.720p.x264"))
    }

    @Test fun foldersBecomeCategoriesAndEpisodesGroupIntoShows() {
        val lib = classifyShare(listOf(
            ShareFile("Movies/Test Pattern (2024).mkv", 5), ShareFile("Movies/Another.Film.2019.1080p.mkv", 6),
            ShareFile("Movies/poster.jpg", 1), ShareFile("Movies/.hidden.mkv", 1),
            ShareFile("TV Shows/Test Show/Season 1/Test Show S01E02 The Second One.mp4", 8),
            ShareFile("TV Shows/Test Show/Season 1/Test Show S01E01.mp4", 7),
            ShareFile("TV Shows/Other.Show.1x03.mkv", 9), ShareFile("loose.mp4", 3),
        ))
        assertEquals(listOf("Test Pattern", "Another Film", "loose"), lib.movies.map { it.title })
        assertEquals(listOf("Movies", "Movies", ""), lib.movies.map { it.category })
        assertEquals(listOf("TV Shows/Test Show", "TV Shows/Other Show"), lib.series.map { it.id })
        val show = lib.series.first()
        assertEquals(listOf(1 to 1, 1 to 2), show.episodes.map { it.season to it.number })
        assertEquals(listOf("1", "The Second One"), show.episodes.map { it.title })
        assertEquals(8L, show.added)
        assertEquals(listOf(Triple("Movies", "Movies", Kind.MOVIE), Triple("", "claude", Kind.MOVIE), Triple("TV Shows", "TV Shows", Kind.SERIES)),
            lib.categories("claude"))
    }

    @Test fun aCategoryFolderThatIsTheShow() {
        val s = classifyShare(listOf(ShareFile("Test Show/Season 2/S02E05.mkv", null))).series.single()
        assertEquals("Test Show", s.name)
        assertEquals(2 to 5, s.episodes.single().let { it.season to it.number })
    }
}
