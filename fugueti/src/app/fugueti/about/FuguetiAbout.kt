/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.lawnchair.ui.preferences.about.LawnchairLink
import app.lawnchair.ui.preferences.about.Role
import app.lawnchair.ui.preferences.about.TeamMember
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

/** Fugueti Launcher's own entries of the About screen (shown above Lawnchair's). */
object FuguetiAbout {
    const val SUPPORT_EMAIL = "victor.walter+fugueti@gmail.com"

    /** EVM address that receives donations (Ethereum and compatible networks). */
    const val DONATION_ADDRESS = "0x34260B953eC6e56e62121Aa3913db3ED89C69A2E"

    val githubUrl: String get() = "https://github.com/${BuildConfig.GITHUB_REPO}"

    /** Download page of the Fugueti Feed bridge app (separate repository). */
    const val FEED_RELEASES_URL = "https://github.com/fugueti/feed/releases/latest"

    val team = listOf(
        TeamMember(
            name = "Victor Walter",
            role = Role.Development,
            photoUrl = "https://github.com/victorwalter.png",
            socialUrl = "https://github.com/victorwalter",
            githubUsername = "victorwalter",
        ),
    )

    fun openSupportEmail(context: Context) {
        val subject = context.getString(R.string.fg_about_support_subject, BuildConfig.FUGUETI_VERSION)
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_EMAIL".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.fg_about_no_email_app, Toast.LENGTH_SHORT).show()
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    fun copyDonationAddress(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.fg_about_donate_address_label), DONATION_ADDRESS))
        Toast.makeText(context, R.string.fg_about_address_copied, Toast.LENGTH_SHORT).show()
    }
}

/** Support, GitHub and Donate buttons of the Fugueti header. */
@Composable
fun FuguetiLinksRow(
    onSupport: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LawnchairLink(
            iconResId = R.drawable.ic_help,
            label = stringResource(id = R.string.support),
            url = "${FuguetiAbout.githubUrl}/issues",
            modifier = Modifier.weight(1f),
            onClick = onSupport,
        )
        LawnchairLink(
            iconResId = R.drawable.ic_github,
            label = stringResource(id = R.string.github),
            url = FuguetiAbout.githubUrl,
            modifier = Modifier.weight(1f),
            onClick = { FuguetiAbout.openUrl(context, FuguetiAbout.githubUrl) },
        )
        LawnchairLink(
            iconResId = R.drawable.ic_donate,
            label = stringResource(id = R.string.donate),
            url = "",
            modifier = Modifier.weight(1f),
            onClick = onDonate,
        )
    }
}

/** Bottom sheet with the donation address as a QR code, plain text and a copy button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateSheet(
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        DonateSheetContent()
    }
}

/** Content of [DonateSheet]: title, explanation, QR code, address and copy button. */
@Composable
fun DonateSheetContent(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.fg_about_donate_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.fg_about_donate_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // The QR always sits on white (with its own quiet zone) so it scans in dark mode too.
        Surface(shape = RoundedCornerShape(20.dp), color = androidx.compose.ui.graphics.Color.White) {
            Image(
                painter = painterResource(R.drawable.fg_donate_qr),
                contentDescription = stringResource(R.string.fg_about_donate_qr_description),
                modifier = Modifier.padding(12.dp).size(220.dp),
            )
        }
        Text(
            text = stringResource(R.string.fg_about_donate_address_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = FuguetiAbout.DONATION_ADDRESS,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        FilledTonalButton(onClick = { FuguetiAbout.copyDonationAddress(context) }) {
            Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.fg_about_copy_address))
        }
    }
}
