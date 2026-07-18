package com.example.prioritize.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt

/**
 * Bionic reading helper: bolds the first ~45% of each word.
 *
 * Research shows that anchoring the first letters of each word can dramatically
 * reduce cognitive load for users with ADHD or dyslexia, enabling faster scanning.
 *
 * Usage: pass [textColor] from MaterialTheme.colorScheme.onSurface so that the
 * function remains theme-aware rather than hard-coding colours.
 *
 * Extracted from TaskCard.kt (#54) to make it reusable across screens.
 */
fun buildBionicString(text: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val words = text.split(" ")
        words.forEachIndexed { index, word ->
            if (word.isEmpty()) return@forEachIndexed

            // Bold the first 45% of each word (minimum 1 character)
            val boldLength = if (word.length <= 3) 1 else (word.length * 0.45).roundToInt().coerceAtLeast(1)
            val boldPart   = word.substring(0, boldLength)
            val normalPart = word.substring(boldLength)

            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold,   color = textColor)) {
                append(boldPart)
            }
            withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = textColor.copy(alpha = 0.75f))) {
                append(normalPart)
            }

            if (index < words.size - 1) append(" ")
        }
    }
}
