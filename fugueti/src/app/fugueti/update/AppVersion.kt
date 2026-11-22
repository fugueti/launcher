/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.update

/** Fugueti release versions: BASE.MINOR.PATCH, as in the `v16.0.2` tags. */
object AppVersion {

    /** Parses `16.0.2` or `v16.0.2`; null for anything else (pre-release tags included). */
    fun parse(version: String): List<Int>? {
        val parts = version.trim().removePrefix("v").split('.')
        if (parts.size != 3) return null
        return parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    /** Whether [latest] is a newer release than [current]; false when either does not parse. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest) ?: return false
        val c = parse(current) ?: return false
        for (i in l.indices) {
            if (l[i] != c[i]) return l[i] > c[i]
        }
        return false
    }
}
