package org.imdtv.iptvtv.recording

import android.content.Context
import org.imdtv.iptvtv.R
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

/** Where recordings are written. Locations are file paths or smb:// addresses (see SmbStore). */
interface RecordingStore {
    val kind: RecStorage
    /** A new file named [fileName] (or "… (2).ts" if taken): its location and an open stream. */
    fun create(fileName: String): Pair<String, OutputStream>
    fun delete(location: String)
    fun freeBytes(): Long?
}

/** Internal storage or a USB drive: a folder this app owns, so no storage permission is needed. */
class FileStore(override val kind: RecStorage, private val dir: File) : RecordingStore {
    override fun create(fileName: String): Pair<String, OutputStream> {
        dir.mkdirs()
        val f = File(dir, uniqueName(fileName) { File(dir, it).exists() })
        return f.path to FileOutputStream(f)
    }
    override fun delete(location: String) { File(location).delete() }
    override fun freeBytes(): Long? = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace?.takeIf { it > 0 }
}

fun internalDir(context: Context) = File(context.filesDir, "recordings")

/** Each mounted removable drive's folder for this app (Android/data/<app>/files/Recordings on the drive). */
fun usbDirs(context: Context): List<File> = context.getExternalFilesDirs(null).drop(1).filterNotNull()
    .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }.map { File(it, "Recordings") }

private const val BYTES_PER_HD_HOUR = 2_000_000_000.0

/** "About 3.8 GB free ≈ 1 h 54 min HD" (about 2 GB per hour of HD). */
fun freeSpaceText(context: Context, bytes: Long?): String {
    if (bytes == null) return context.getString(R.string.vod_rec_free_unknown)
    val minutes = (bytes / BYTES_PER_HD_HOUR * 60).toLong()
    val time = when {
        minutes < 60 -> context.getString(R.string.vod_minutes_short, minutes)
        minutes % 60 == 0L -> context.getString(R.string.vod_hours_short, minutes / 60)
        else -> context.getString(R.string.vod_hours_minutes_short, minutes / 60, minutes % 60)
    }
    return context.getString(R.string.vod_rec_free_space, String.format(Locale.UK, "%.1f", bytes / 1e9), time)
}

/** A recording's file (path or smb://), as opposed to a provider stream the player is showing. */
fun isRecordingLocation(url: String) = url.startsWith("/") || url.startsWith("smb://")
