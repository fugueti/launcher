/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.about

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.FeedBridge
import app.lawnchair.util.isPackageInstalledAndEnabled
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.net.URLEncoder

/** GitHub issue forms of this repository (see `.github/ISSUE_TEMPLATE`). */
enum class IssueKind(val template: String, val titlePrefix: String) {
    BUG("bug_report.yaml", "[BUG] "),
    FEATURE("feature_request.yaml", "[FEATURE] "),
}

object SupportLinks {
    /**
     * New issue link that opens [kind]'s form with its fields filled in: GitHub fills an issue form
     * field from the query parameter named after the field id.
     */
    fun newIssueUrl(repo: String, kind: IssueKind, fields: Map<String, String>): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val params = linkedMapOf("template" to kind.template, "title" to kind.titlePrefix) + fields
        return "https://github.com/$repo/issues/new?" +
            params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    }

    /** Version, device and Android version of this install, keyed by the issue form field ids. */
    fun deviceFields(context: Context): Map<String, String> = mapOf(
        "version" to "${BuildConfig.FUGUETI_VERSION} (${BuildConfig.COMMIT_HASH})",
        "device" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        "android" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "feed" to if (context.packageManager.isPackageInstalledAndEnabled(FeedBridge.FUGUETI_FEED_PACKAGE)) {
            "Installed"
        } else {
            "Not installed"
        },
    )

    fun open(context: Context, kind: IssueKind) {
        val fields = deviceFields(context).let { if (kind == IssueKind.FEATURE) it.filterKeys { k -> k == "version" } else it }
        FuguetiAbout.openUrl(context, newIssueUrl(BuildConfig.GITHUB_REPO, kind, fields))
    }
}

/** Support sheet: report a bug or suggest a feature on GitHub, or write an e-mail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.fg_support_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.fg_support_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(
                onClick = { SupportLinks.open(context, IssueKind.BUG); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.fg_support_report_bug)) }
            FilledTonalButton(
                onClick = { SupportLinks.open(context, IssueKind.FEATURE); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.fg_support_suggest_feature)) }
            TextButton(
                onClick = { FuguetiAbout.openSupportEmail(context); onDismiss() },
            ) { Text(stringResource(R.string.fg_support_email)) }
        }
    }
}
