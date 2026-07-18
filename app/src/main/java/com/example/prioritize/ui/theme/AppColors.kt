package com.example.prioritize.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralized colour palette for the Prioritize app.
 *
 * All hex literals used across UI files are defined here as named constants.
 * Replace any raw Color(0xFFXXXXXX) usages with the corresponding name below.
 *
 * Colour naming convention:
 *  - Background*  : full-screen / scaffold backgrounds (darkest tones)
 *  - Surface*     : card / sheet / dialog surfaces
 *  - Border*      : dividers and outlines
 *  - Text*        : text content tones
 *  - Accent*      : brand / interactive highlights
 *  - Status*      : semantic signals (urgency, importance, deadlines, warnings)
 */
object AppColors {

    // ── Backgrounds ──────────────────────────────────────────────────────────
    /** Deepest app background — used on Scaffold and root containers. */
    val BackgroundDeepest     = Color(0xFF0F0F1A)

    /** Dark navy app background — alternate deep bg for some screens. */
    val BackgroundNavy        = Color(0xFF0F172A)

    /** Dark charcoal background — used in BrainScreen and model screens. */
    val BackgroundCharcoal    = Color(0xFF0F1115)

    /** Dark background for the navigation bar container. */
    val BackgroundNavBar      = Color(0xFF151522)

    /** Medium-dark background — used for card / input backgrounds. */
    val BackgroundMedium      = Color(0xFF1E1E2C)

    /** Deep purple-black — primary card background in FocusListScreen etc. */
    val BackgroundCard        = Color(0xFF181B22)

    /** Slightly lighter card variant — used in BreakdownDialog. */
    val BackgroundCardAlt     = Color(0xFF1F1B24)

    // ── Surfaces & Borders ────────────────────────────────────────────────────
    /** Surface/border for interactive elements (sliders, input outlines). */
    val SurfaceElevated       = Color(0xFF28283C)

    /** Standard divider and border tone across all screens. */
    val Border                = Color(0xFF323246)

    /** Slightly lighter border — used for pill chips and tag outlines. */
    val BorderLight           = Color(0xFF42425A)

    // ── Text & Content ────────────────────────────────────────────────────────
    /** Steel-blue muted text — secondary labels, dates, metadata. */
    val TextSecondary         = Color(0xFF5C7F9E)

    /** Slate muted text — timestamps and de-emphasized content. */
    val TextMuted             = Color(0xFF6F7682)

    /** Slate-300 equivalent — body text on dark backgrounds. */
    val TextBody              = Color(0xFF94A3B8)

    /** Near-white — high-emphasis body text. */
    val TextHighEmphasis      = Color(0xFFE2E8F0)

    /** White — maximum contrast text / icons on coloured surfaces. */
    val TextOnAccent          = Color(0xFFF8FAFC)

    // ── Accent & Brand ────────────────────────────────────────────────────────
    /** Primary brand purple — buttons, highlights, FAB. */
    val AccentPurple          = Color(0xFFBB86FC)

    /** Secondary brand teal — secondary actions, teal chips. */
    val AccentTeal            = Color(0xFF03DAC6)

    /** Deep purple — pressed / ripple state for primary actions. */
    val AccentDeepPurple      = Color(0xFF3700B3)

    // ── Status / Semantic ─────────────────────────────────────────────────────
    /** Green — task complete badges, low-urgency indicators. */
    val StatusGreen           = Color(0xFF6BA384)

    /** Amber — moderate urgency / importance warnings. */
    val StatusAmber           = Color(0xFFF59E0B)

    /** Warm orange — high urgency overdue warnings. */
    val StatusOrange          = Color(0xFFFFB74D)

    /** Red — critical urgency / delete actions / past deadline. */
    val StatusRed             = Color(0xFFEF4444)

    /** Muted rose — secondary importance indicators (matrix quadrant colours). */
    val StatusRose            = Color(0xFFCF6679)
}
