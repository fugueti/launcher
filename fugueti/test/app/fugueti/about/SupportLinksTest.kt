/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.about

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportLinksTest {
    private fun query(url: String): Map<String, String> = URI(url).rawQuery.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    @Test
    fun bugLinkOpensTheFormWithFieldsFilledIn() {
        val url = SupportLinks.newIssueUrl(
            "fugueti/launcher",
            IssueKind.BUG,
            mapOf("version" to "16.0.2-nightly.7 (abc1234)", "device" to "Google Pixel 9", "android" to "Android 17 (API 37)"),
        )
        assertTrue(url.startsWith("https://github.com/fugueti/launcher/issues/new?"))
        assertTrue("spaces are %20, not +", !url.contains("+"))
        val q = query(url)
        assertEquals("bug_report.yaml", q["template"])
        assertEquals("[BUG] ", q["title"])
        assertEquals("16.0.2-nightly.7 (abc1234)", q["version"])
        assertEquals("Google Pixel 9", q["device"])
        assertEquals("Android 17 (API 37)", q["android"])
    }

    @Test
    fun featureLinkUsesTheFeatureForm() {
        val q = query(SupportLinks.newIssueUrl("o/r", IssueKind.FEATURE, mapOf("version" to "16.0.1")))
        assertEquals("feature_request.yaml", q["template"])
        assertEquals("[FEATURE] ", q["title"])
        assertEquals("16.0.1", q["version"])
    }
}
