package org.imdtv.iptvtv.ui.live

import org.imdtv.iptvtv.player.StreamFormats
import org.imdtv.iptvtv.player.badges

private val AUDIO = setOf("MONO", "STEREO", "5.1", "7.1")

/** Picture badges for the hero, only while the focused channel is the one previewing (spec §6A.2); no audio badge. */
fun heroBadges(focusedId: Long?, previewingId: Long?, formats: StreamFormats): List<String> =
    if (focusedId != null && focusedId == previewingId) badges(formats).filter { it !in AUDIO } else emptyList()
