package com.mycodex.mobile.ui.theme

import com.mycodex.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun persistedValuesAreStableAndInvalidValuesFollowSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromPersistedValue(null))
        assertEquals(ThemeMode.System, ThemeMode.fromPersistedValue(""))
        assertEquals(ThemeMode.System, ThemeMode.fromPersistedValue("unknown"))
        ThemeMode.entries.forEach {
            assertEquals(it, ThemeMode.fromPersistedValue(it.persistedValue))
        }
    }

    @Test
    fun modesResolveDarkAndMapToActivityThemes() {
        assertFalse(ThemeMode.System.resolveDark(false))
        assertTrue(ThemeMode.System.resolveDark(true))
        assertFalse(ThemeMode.Light.resolveDark(true))
        assertTrue(ThemeMode.Dark.resolveDark(false))
        assertEquals(R.style.Theme_MyCodex_Light, ThemeMode.System.activityTheme(false))
        assertEquals(R.style.Theme_MyCodex_Dark, ThemeMode.System.activityTheme(true))
        assertEquals(R.style.Theme_MyCodex_Light, ThemeMode.Light.activityTheme(true))
        assertEquals(R.style.Theme_MyCodex_Dark, ThemeMode.Dark.activityTheme(false))
    }

    @Test
    fun selectionDoesNothingForCurrentModeAndDoesNotApplyAfterSaveFailure() {
        var saves = 0
        var applies = 0
        assertTrue(
            selectThemeMode(ThemeMode.Light, ThemeMode.Light, { saves += 1; true }) {
                applies += 1
            },
        )
        assertEquals(0, saves)
        assertEquals(0, applies)

        assertFalse(
            selectThemeMode(ThemeMode.Light, ThemeMode.Dark, { saves += 1; false }) {
                applies += 1
            },
        )
        assertEquals(1, saves)
        assertEquals(0, applies)
    }

    @Test
    fun saveFailureCannotReachPlatformThemeOrActivityRecreateDecision() {
        var platformThemeApplications = 0
        var activityRecreates = 0

        val saved = selectThemeMode(
            current = ThemeMode.Light,
            requested = ThemeMode.Dark,
            save = { false },
        ) {
            platformThemeApplications += 1
            activityRecreates += 1
        }

        assertFalse(saved)
        assertEquals(0, platformThemeApplications)
        assertEquals(0, activityRecreates)
    }
}
