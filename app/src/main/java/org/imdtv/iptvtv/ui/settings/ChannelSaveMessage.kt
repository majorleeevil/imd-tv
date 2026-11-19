package org.imdtv.iptvtv.ui.settings

import android.content.res.Resources
import org.imdtv.iptvtv.R

/** The info bar after Save changes in Manage channels: "5 channels hidden", "1 channel shown", "5 hidden, 3 shown". */
fun channelsSavedMessage(res: Resources, hidden: Int, shown: Int): String = when {
    hidden > 0 && shown > 0 -> res.getString(R.string.set_a_saved_hidden_shown, hidden, shown)
    hidden > 0 -> res.getQuantityString(R.plurals.set_a_channels_hidden, hidden, hidden)
    else -> res.getQuantityString(R.plurals.set_a_channels_shown, shown, shown)
}
