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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    /** A donation address and the networks it takes, as fugueti.app lists them. */
    data class DonationAddress(val label: String, val address: String, val darkColor: Long, val lightColor: Long)

    /**
     * Donation page of fugueti.app, opened with its donation sheet. The Google Play version links
     * here instead of showing the addresses (as Lawnchair's Play version does).
     */
    const val DONATE_URL = "https://fugueti.app/#donate"

    /** Official donation addresses, in fugueti.app's order. */
    val donationAddresses = listOf(
        DonationAddress("BITCOIN (BTC)", "bc1q6mxhcxm8wtgfrph8cs5a7vxewas4sztg00ryz4", 0xFFF08A24, 0xFFC8640A),
        DonationAddress("LIGHTNING (BTC)", "fugueti@cake.cash", 0xFF9B5CF6, 0xFF7A3FE0),
        DonationAddress("EVM (ETH/USDC/USDT • POLYGON/BASE/ARB)", "0x34260B953eC6e56e62121Aa3913db3ED89C69A2E", 0xFF7C83F0, 0xFF4F55D6),
        DonationAddress("SOLANA (SOL/USDC/USDT)", "EQSZYBNCdQ8jK3x78KeYCRQkrDN6dtMauJHpk9am8QES", 0xFF2FC58C, 0xFF14936A),
        DonationAddress("PIX (BRL)", "pix@fugueti.app", 0xFF2F9E74, 0xFF14805A),
    )

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

    fun copyDonationAddress(context: Context, donation: DonationAddress) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(donation.label, donation.address))
        Toast.makeText(context, R.string.fg_about_address_copied, Toast.LENGTH_SHORT).show()
    }

    /** Long addresses keep their start and end, as on fugueti.app. */
    fun shortAddress(address: String): String =
        if (address.length > 34) "${address.take(16)}...${address.takeLast(14)}" else address
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

/** Bottom sheet with Fugueti's donation addresses, each with a copy button (like fugueti.app). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateSheet(
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        DonateSheetContent(onClose = onDismiss)
    }
}

/** Content of [DonateSheet]: header, hint and one card per donation address. */
@Composable
fun DonateSheetContent(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Icon(painterResource(R.drawable.ic_donate), contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fg_about_donate_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.fg_about_donate_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        val copyLabel = stringResource(R.string.fg_about_copy)
        val hint = stringResource(R.string.fg_about_donate_hint, copyLabel)
        Text(
            text = buildAnnotatedString {
                val start = hint.indexOf(copyLabel)
                append(hint)
                if (start >= 0) {
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                        start,
                        start + copyLabel.length,
                    )
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        FuguetiAbout.donationAddresses.forEach { donation ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = donation.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(if (dark) donation.darkColor else donation.lightColor),
                        )
                        Text(
                            text = FuguetiAbout.shortAddress(donation.address),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(onClick = { FuguetiAbout.copyDonationAddress(context, donation) }) {
                        Text(copyLabel)
                    }
                }
            }
        }
    }
}
