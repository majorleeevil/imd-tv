package org.imdtv.iptvtv.ui.settings

/** The info bar after Save changes in Manage channels: "5 channels hidden", "1 channel shown", "5 hidden, 3 shown". */
fun channelsSavedMessage(hidden: Int, shown: Int): String = when {
    hidden > 0 && shown > 0 -> "$hidden hidden, $shown shown"
    hidden > 0 -> "$hidden ${if (hidden == 1) "channel" else "channels"} hidden"
    else -> "$shown ${if (shown == 1) "channel" else "channels"} shown"
}
