package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.imdtv.iptvtv.data.model.Category
import org.imdtv.iptvtv.data.model.Channel
import org.imdtv.iptvtv.data.model.Episode
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.model.Movie
import org.imdtv.iptvtv.data.model.MovieDetail
import org.imdtv.iptvtv.data.model.Series
import org.imdtv.iptvtv.data.model.SeriesDetail
import org.imdtv.iptvtv.recording.SmbSettings
import org.imdtv.iptvtv.recording.connectShare

// --- SMB / NAS (#27): a network share's video files as Movies and Series; folders are the categories. ---

/** (server, share, folder inside it) from "\\nas\Media\Films", "nas/Media", "smb://nas/Media/Films/"; null without a share. */
fun parseShareAddress(typed: String): Triple<String, String, String>? {
    val parts = typed.trim().removePrefix("smb:").replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
    if (parts.size < 2) return null
    return Triple(parts[0], parts[1], parts.drop(2).joinToString("/"))
}

private val VIDEO = setOf("mkv", "mp4", "m4v", "avi", "ts", "m2ts", "mov", "webm", "mpg", "mpeg", "wmv")
private val EPISODE = listOf(Regex("""[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})"""), Regex("""(?<!\d)(\d{1,2})x(\d{2,3})(?!\d)"""))
private val SEASON_FOLDER = Regex("""(?i)^(season|series|staffel|saison|temporada|stagione|s)\s*\d+$""")
private val YEAR = Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""")
private val JUNK = Regex("""(?i)\b(2160p|1080p|720p|480p|4k|uhd|bluray|blu-ray|brrip|bdrip|web-?dl|webrip|hdtv|dvdrip|x264|x265|h\.?264|h\.?265|hevc|remux)\b""")

private fun clean(s: String) = s.replace('.', ' ').replace('_', ' ').replace(Regex("""\s+"""), " ").trim(' ', '-', '(', '[', ')', ']')

/** "Another.Film.2019.1080p" → ("Another Film", 2019); "Test Pattern (2024)" → ("Test Pattern", 2024). */
fun movieTitle(base: String): Pair<String, Int?> {
    val y = YEAR.findAll(base).lastOrNull { it.range.first > 0 }
    val cut = listOfNotNull(y?.range?.first, JUNK.find(base)?.range?.first?.takeIf { it > 0 }).minOrNull() ?: base.length
    return clean(base.substring(0, cut)).ifEmpty { clean(base) } to y?.value?.toInt()
}

data class ShareFile(val path: String, val added: Long?)
data class ShareEpisode(val path: String, val season: Int, val number: Int, val title: String)
data class ShareSeries(val id: String, val category: String, val name: String, val episodes: List<ShareEpisode>, val added: Long?)
data class ShareMovie(val path: String, val category: String, val title: String, val year: Int?, val added: Long?)
data class ShareLibrary(val movies: List<ShareMovie>, val series: List<ShareSeries>)

/**
 * [files]: paths inside the share's folder, "/"-separated. The first folder is the category ("" for files at the top). A file
 * named S01E02 or 1x02 is an episode of the nearest folder that isn't a season folder (else of the name before the number);
 * any other video is a movie.
 */
fun classifyShare(files: List<ShareFile>): ShareLibrary {
    val movies = ArrayList<ShareMovie>()
    val eps = LinkedHashMap<Pair<String, String>, MutableList<Pair<ShareEpisode, Long?>>>()
    for (f in files) {
        val seg = f.path.split('/')
        val name = seg.last()
        if (name.substringAfterLast('.', "").lowercase() !in VIDEO || name.startsWith(".")) continue
        val base = name.substringBeforeLast('.')
        val cat = if (seg.size > 1) seg[0] else ""
        val m = EPISODE.firstNotNullOfOrNull { it.find(base) }
        if (m == null) {
            val (t, y) = movieTitle(base)
            movies += ShareMovie(f.path, cat, t, y, f.added)
            continue
        }
        val show = seg.subList(1.coerceAtMost(seg.size - 1), seg.size - 1).lastOrNull { !SEASON_FOLDER.matches(it) }
            ?: clean(base.substring(0, m.range.first)).ifEmpty { cat.ifEmpty { base } }
        val title = clean(base.substring(m.range.last + 1)).ifEmpty { "${m.groupValues[2].toInt()}" }
        eps.getOrPut(cat to show) { ArrayList() } += ShareEpisode(f.path, m.groupValues[1].toInt(), m.groupValues[2].toInt(), title) to f.added
    }
    val series = eps.map { (k, list) ->
        ShareSeries("${k.first}/${k.second}", k.first, k.second, list.map { it.first }.sortedWith(compareBy({ it.season }, { it.number })),
            list.mapNotNull { it.second }.maxOrNull())
    }
    return ShareLibrary(movies, series)
}

/** Categories as (folder, folder, kind): a folder with movies is a movie category, one with shows a series category, or both. */
fun ShareLibrary.categories(topName: String): List<Triple<String, String, Kind>> =
    (movies.map { Triple(it.category, it.category.ifEmpty { topName }, Kind.MOVIE) } +
        series.map { Triple(it.category, it.category.ifEmpty { topName }, Kind.SERIES) }).distinct()

/** Every file under [folder] (depth and count capped, so a whole-disk share can't run away), paths relative to it. */
fun walkShare(s: SmbSettings): List<ShareFile> = connectShare(s).use { c ->
    val out = ArrayList<ShareFile>()
    fun walk(rel: String, depth: Int) {
        val dir = listOf(s.folder.trim('/'), rel).filter { it.isNotEmpty() }.joinToString("/").replace('/', '\\')
        for (e in c.share.list(dir)) {
            val n = e.fileName
            if (n == "." || n == ".." || n.startsWith(".") || n.startsWith("@") || n.startsWith("#")) continue // dot files, Synology @eaDir, #recycle
            val path = if (rel.isEmpty()) n else "$rel/$n"
            if (e.fileAttributes and 0x10L != 0L) { if (depth < MAX_DEPTH) walk(path, depth + 1) }
            else if (out.size < MAX_FILES) out += ShareFile(path, e.lastWriteTime.toEpochMillis())
        }
    }
    walk("", 0)
    out
}

private const val MAX_DEPTH = 6
private const val MAX_FILES = 50_000

/** A share as a playlist's VOD. Stream links are smb:// addresses; the player reads them with the playlist's login. */
class SmbSource(private val s: SmbSettings, private val name: String, private val skip: Set<String>) : PlaylistSource {
    private var cache: ShareLibrary? = null
    private suspend fun lib() = cache ?: withContext(Dispatchers.IO) { classifyShare(walkShare(s)) }.let { l ->
        ShareLibrary(l.movies.filter { it.category !in skip }, l.series.filter { it.category !in skip })
    }.also { cache = it }

    private fun url(path: String) = "smb://${s.server}/${s.share}/" + listOf(s.folder.trim('/'), path).filter { it.isNotEmpty() }.joinToString("/")
    private fun ShareMovie.movie() = Movie(path, category, title, null, year, null, url(path), added)
    private fun ShareSeries.series() = Series(id, category, name, null, null, null, added)

    override suspend fun liveCategories(): List<Category> = emptyList()
    override suspend fun liveChannels(): List<Channel> = emptyList()
    override fun epgUrls(): List<String> = emptyList()
    override suspend fun vodCategories() = lib().categories(name).filter { it.third == Kind.MOVIE }.map { Category(it.first, it.second, Kind.MOVIE) }
    override suspend fun seriesCategories() = lib().categories(name).filter { it.third == Kind.SERIES }.map { Category(it.first, it.second, Kind.SERIES) }
    override suspend fun movies() = lib().movies.map { it.movie() }
    override suspend fun series() = lib().series.map { it.series() }
    override suspend fun movieDetail(id: String): MovieDetail {
        val m = lib().movies.find { it.path == id } ?: throw java.io.IOException("$id is no longer on the share")
        return MovieDetail(m.movie(), null, null, null, null, null, null)
    }
    override suspend fun seriesDetail(id: String): SeriesDetail {
        val sr = lib().series.find { it.id == id } ?: throw java.io.IOException("$id is no longer on the share")
        return SeriesDetail(sr.series(), null, null, null, sr.episodes.map { Episode(it.path, it.season, it.number, it.title, null, null, null, url(it.path)) })
    }
}
