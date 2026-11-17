package org.imdtv.iptvtv.data.epg

import org.junit.Assert.assertEquals
import org.junit.Test
import org.imdtv.iptvtv.data.db.EpgChannelEntity

class SourceChannelChoicesTest {
    private fun row(source: Long, id: String, name: String) = EpgChannelEntity(source, id, name, name.lowercase(), null)
    private val labels = mapOf(1L to "Mine", 2L to "Other")
    private val rows = listOf(row(1, "bbc1.uk", "BBC One"), row(1, "bbc1.uk", "BBC 1 London"), row(2, "bbc1.alt", "BBC 1 HD"),
        row(1, "bbc2.uk", "BBC Two"), row(2, "itv.uk", "ITV"))

    @Test fun blankQueryOffersSameNameChannels() {
        val got = sourceChannelChoices("UK: BBC One HD", rows, labels, " ")
        assertEquals(listOf(SourceChannel(1, "bbc1.uk", "BBC One", "Mine"), SourceChannel(2, "bbc1.alt", "BBC 1 HD", "Other")), got)
    }

    @Test fun queryNeedsEveryWordAndSortsByName() {
        assertEquals(listOf("bbc1.alt", "bbc1.uk"),
            sourceChannelChoices("x", rows, labels, "bbc 1").map { it.xmltvId })
        assertEquals(listOf("BBC 1 HD", "BBC 1 London", "BBC Two"), sourceChannelChoices("x", rows, labels, "BBC").map { it.name })
    }

    @Test fun oneRowPerSourceAndXmltvIdAndLimitApplies() {
        assertEquals(1, sourceChannelChoices("x", rows, labels, "london").size)
        assertEquals(2, sourceChannelChoices("x", rows, labels, "bbc", limit = 2).size)
        assertEquals("", sourceChannelChoices("x", listOf(row(9, "a", "A")), labels, "a").single().source)
    }
}
