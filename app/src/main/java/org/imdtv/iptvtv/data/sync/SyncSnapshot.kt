package org.imdtv.iptvtv.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.imdtv.iptvtv.data.backup.Backup

/** One login per playlist, by [Backup.playlists] index; null = the playlist has none. */
@Serializable
data class SLogin(val username: String, val password: String)

/** What a master sends a follower: the backup plus the logins a backup never holds, and [active] = the playlist in use (index). Only ever sent encrypted. */
@Serializable
data class SyncSnapshot(val backup: Backup, val logins: List<SLogin?>, val active: Int? = null)

private val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun encodeSnapshot(s: SyncSnapshot): String = syncJson.encodeToString(SyncSnapshot.serializer(), s)
fun decodeSnapshot(s: String): SyncSnapshot = syncJson.decodeFromString(SyncSnapshot.serializer(), s)
