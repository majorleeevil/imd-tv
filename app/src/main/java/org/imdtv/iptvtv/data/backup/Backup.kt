package org.imdtv.iptvtv.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.AudioDecoder
import org.imdtv.iptvtv.data.settings.GuideKind
import org.imdtv.iptvtv.data.settings.HeaderSize
import org.imdtv.iptvtv.data.settings.LogoKind
import org.imdtv.iptvtv.data.settings.ThemeColour
import org.imdtv.iptvtv.data.settings.themeIdOf

const val BACKUP_VERSION = 1

class BackupException(message: String) : Exception(message)

/** Backup file (spec §7). Never holds a username or password; enums are stored by name so unknown ones read as defaults. */
@Serializable
data class Backup(val version: Int = BACKUP_VERSION, val playlists: List<BPlaylist>, val settings: BSettings)

@Serializable
data class BPlaylist(
    val name: String,
    val type: String,
    val url: String,
    val userAgent: String?,
    val streamFormat: String,
    val nameStrip: String,
    val epgSources: List<BEpgSource>,
    val categories: List<BCategory>,
    val groups: List<BGroup>,
    val favourites: List<BFavourite>,
    val categoryStrip: String = "",
    /** Where movies and episodes were left (older backups have none). */
    val progress: List<BProgress> = emptyList(),
    /** The user's channel order within live categories (older backups have none). */
    val channelOrder: List<BChannelOrder> = emptyList(),
)

/** [url] null = the playlist's own guide, whose URL carries the login: restored from the new login. */
@Serializable
data class BEpgSource(val url: String?, val name: String?, val priority: Int, val offsetMinutes: Int)

@Serializable
data class BCategory(val kind: String, val remoteId: String, val hidden: Boolean, val sortOrder: Int, val pinned: Boolean)

/** [channels] are channel remoteIds in group order. */
@Serializable
data class BGroup(val name: String, val sortOrder: Int, val channels: List<String>, val placement: String = "NORMAL")

@Serializable
data class BFavourite(val kind: String, val remoteId: String)

/** [category] is a LIVE category remoteId; [channels] are the placed channels' remoteIds in order. */
@Serializable
data class BChannelOrder(val category: String, val channels: List<String>)

/** [remoteId] of the movie or series; [episode] the episode's remoteId ("" for a movie). */
@Serializable
data class BProgress(val kind: String, val remoteId: String, val episode: String, val positionMs: Long, val durationMs: Long,
                     val updatedAt: Long, val completed: Boolean)

/** [AppSettings] minus lastLiveChannelId (a row id, meaningless after restore). */
@Serializable
data class BSettings(
    val theme: String = AppSettings().theme.name,
    val themeColour: String = AppSettings().themeColour.name,
    val autoplayOnScroll: Boolean = AppSettings().autoplayOnScroll,
    val audioDecoder: String = AppSettings().audioDecoder.name,
    val lastChannelOnStart: Boolean = AppSettings().lastChannelOnStart,
    val confirmExit: Boolean = AppSettings().confirmExit,
    val startOnBoot: Boolean = AppSettings().startOnBoot,
    val epgUpdateIntervalHours: Int = AppSettings().epgUpdateIntervalHours,
    val epgUpdateOnStart: Boolean = AppSettings().epgUpdateOnStart,
    val epgUpdateOnPlaylistChange: Boolean = AppSettings().epgUpdateOnPlaylistChange,
    val epgDaysToKeep: Int = AppSettings().epgDaysToKeep,
    val epgPastDays: Int = AppSettings().epgPastDays,
    val matchTvLanguage: Boolean = AppSettings().matchTvLanguage,
    val logoOrder: List<String> = AppSettings().logoOrder.map { it.name },
    val guideOrder: List<String> = AppSettings().guideOrder.map { it.name },
    val borrowSiblingGuide: Boolean = AppSettings().borrowSiblingGuide,
    val hideHeaderChannels: Boolean = AppSettings().hideHeaderChannels,
    val showDateTime: Boolean = AppSettings().showDateTime,
    val showPing: Boolean = AppSettings().showPing,
    val subtitlesOn: Boolean = AppSettings().subtitlesOn,
    val subtitleLanguage: String? = AppSettings().subtitleLanguage,
    val recordingOn: Boolean = AppSettings().recordingOn,
    val recordTo: String = AppSettings().recordTo.name,
    val startEarlyMin: Int = AppSettings().startEarlyMin,
    val endLateMin: Int = AppSettings().endLateMin,
    val keepAwake: Boolean = AppSettings().keepAwake,
    val smbServer: String = "",
    val smbShare: String = "",
    val smbFolder: String = "",
    val smbUser: String = "",
    val guideRows: Int = AppSettings().guideRows,
    val multiLineGuide: Boolean = AppSettings().multiLineGuide,
    val zapLeftRight: Boolean = AppSettings().zapLeftRight,
    val backToFullScreen: Boolean = AppSettings().backToFullScreen,
    val showChannelNumbers: Boolean = AppSettings().showChannelNumbers,
    val clock24h: Boolean = AppSettings().clock24h,
    val playerClock: Boolean = AppSettings().playerClock,
    val liveSubtitlesOn: Boolean = AppSettings().liveSubtitlesOn,
    val headerSize: String = AppSettings().headerSize.name,
    val previewFade: Boolean = AppSettings().previewFade,
    val themeDark: Boolean = AppSettings().themeDark,
) {
    fun toAppSettings(lastLiveChannelId: Long?): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = themeIdOf(theme),
            themeColour = ThemeColour.entries.find { it.name == themeColour } ?: d.themeColour,
            autoplayOnScroll = autoplayOnScroll,
            audioDecoder = AudioDecoder.entries.find { it.name == audioDecoder } ?: d.audioDecoder,
            lastChannelOnStart = lastChannelOnStart,
            confirmExit = confirmExit,
            zapLeftRight = zapLeftRight, backToFullScreen = backToFullScreen, showChannelNumbers = showChannelNumbers, clock24h = clock24h, playerClock = playerClock, liveSubtitlesOn = liveSubtitlesOn,
            headerSize = HeaderSize.entries.find { it.name == headerSize } ?: d.headerSize, previewFade = previewFade, themeDark = themeDark,
            startOnBoot = startOnBoot,
            epgUpdateIntervalHours = epgUpdateIntervalHours,
            epgUpdateOnStart = epgUpdateOnStart,
            epgUpdateOnPlaylistChange = epgUpdateOnPlaylistChange,
            epgDaysToKeep = epgDaysToKeep,
            epgPastDays = epgPastDays,
            lastLiveChannelId = lastLiveChannelId,
            matchTvLanguage = matchTvLanguage,
            logoOrder = logoOrder.mapNotNull { n -> LogoKind.entries.find { it.name == n } }.distinct().ifEmpty { d.logoOrder },
            guideOrder = guideOrder.mapNotNull { n -> GuideKind.entries.find { it.name == n } }.distinct().ifEmpty { d.guideOrder },
            borrowSiblingGuide = borrowSiblingGuide,
            hideHeaderChannels = hideHeaderChannels,
            showDateTime = showDateTime,
            showPing = showPing,
            guideRows = guideRows,
            multiLineGuide = multiLineGuide,
            subtitlesOn = subtitlesOn,
            subtitleLanguage = subtitleLanguage,
            recordingOn = recordingOn,
            recordTo = org.imdtv.iptvtv.recording.RecStorage.entries.find { it.name == recordTo } ?: d.recordTo,
            startEarlyMin = startEarlyMin,
            endLateMin = endLateMin,
            keepAwake = keepAwake,
            smbServer = smbServer, smbShare = smbShare, smbFolder = smbFolder, smbUser = smbUser,
        ).clamped()
    }

    companion object {
        fun of(s: AppSettings) = BSettings(
            s.theme.name, s.themeColour.name, s.autoplayOnScroll, s.audioDecoder.name, s.lastChannelOnStart, s.confirmExit,
            s.startOnBoot, s.epgUpdateIntervalHours, s.epgUpdateOnStart, s.epgUpdateOnPlaylistChange, s.epgDaysToKeep,
            s.epgPastDays, s.matchTvLanguage, s.logoOrder.map { it.name }, s.guideOrder.map { it.name },
            s.borrowSiblingGuide, s.hideHeaderChannels, s.showDateTime, s.showPing, s.subtitlesOn, s.subtitleLanguage,
            guideRows = s.guideRows, multiLineGuide = s.multiLineGuide,
            zapLeftRight = s.zapLeftRight, backToFullScreen = s.backToFullScreen, showChannelNumbers = s.showChannelNumbers, clock24h = s.clock24h, playerClock = s.playerClock, liveSubtitlesOn = s.liveSubtitlesOn,
            headerSize = s.headerSize.name, previewFade = s.previewFade, themeDark = s.themeDark,
            recordingOn = s.recordingOn, recordTo = s.recordTo.name, startEarlyMin = s.startEarlyMin, endLateMin = s.endLateMin,
            keepAwake = s.keepAwake, smbServer = s.smbServer, smbShare = s.smbShare, smbFolder = s.smbFolder, smbUser = s.smbUser,
        )
    }
}

private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

fun encodeBackup(b: Backup): String = backupJson.encodeToString(Backup.serializer(), b)

/** The version is read before the body, so a newer file is named as such even when its shape has changed. */
fun decodeBackup(s: String): Backup {
    val tree = try {
        backupJson.parseToJsonElement(s).jsonObject
    } catch (e: SerializationException) {
        throw BackupException("Not a readable backup file")
    } catch (e: IllegalArgumentException) {
        throw BackupException("Not a readable backup file")
    }
    val version = (tree["version"] as? JsonPrimitive)?.intOrNull ?: throw BackupException("Not a readable backup file")
    if (version > BACKUP_VERSION) throw BackupException("This backup was made by a newer version of the app")
    return try {
        backupJson.decodeFromJsonElement(Backup.serializer(), tree)
    } catch (e: SerializationException) {
        throw BackupException("Not a readable backup file")
    } catch (e: IllegalArgumentException) {
        throw BackupException("Not a readable backup file")
    }
}
