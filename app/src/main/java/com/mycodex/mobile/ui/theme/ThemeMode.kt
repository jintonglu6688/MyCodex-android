package com.mycodex.mobile.ui.theme

import android.content.Context
import com.mycodex.mobile.R

enum class ThemeMode(val persistedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    fun activityTheme(systemDark: Boolean): Int = when (this) {
        System -> if (systemDark) R.style.Theme_MyCodex_Dark else R.style.Theme_MyCodex_Light
        Light -> R.style.Theme_MyCodex_Light
        Dark -> R.style.Theme_MyCodex_Dark
    }

    companion object {
        fun fromPersistedValue(value: String?): ThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: System
    }
}

class ThemePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun load(): ThemeMode = ThemeMode.fromPersistedValue(preferences.getString(ThemeModeKey, null))

    fun save(mode: ThemeMode): Boolean =
        preferences.edit().putString(ThemeModeKey, mode.persistedValue).commit()

    companion object {
        internal const val PreferencesName = "ui_preferences"
        internal const val ThemeModeKey = "theme_mode"
    }
}

internal inline fun selectThemeMode(
    current: ThemeMode,
    requested: ThemeMode,
    save: (ThemeMode) -> Boolean,
    apply: (ThemeMode) -> Unit,
): Boolean {
    if (current == requested) return true
    if (!save(requested)) return false
    apply(requested)
    return true
}
