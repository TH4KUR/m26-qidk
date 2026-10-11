package com.megathon.incident.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/** The language every screen is drawn in. It follows the Hindi switch on Home and changes at once, with no restart. */
enum class AppLanguage { EN, HI }

val LocalLanguage = compositionLocalOf { AppLanguage.EN }

@Composable
@ReadOnlyComposable
fun isHindi(): Boolean = LocalLanguage.current == AppLanguage.HI

/**
 * The text for the current language. Every user-visible literal on a screen goes through this (or through a
 * [com.megathon.incident.session.HindiLabels] lookup), so a screen is wholly English or wholly Hindi, never a mix.
 * Pass the Hindi as real Devanagari; do not build sentences by joining halves.
 */
@Composable
@ReadOnlyComposable
fun tr(en: String, hi: String): String = if (LocalLanguage.current == AppLanguage.HI) hi else en
