package com.mycodex.mobile.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.mycodex.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun fixedThemesResolveAllNativeColorsAndSystemBarAppearance() {
        val light = ContextThemeWrapper(context, R.style.Theme_MyCodex_Light)
        val dark = ContextThemeWrapper(context, R.style.Theme_MyCodex_Dark)

        assertNativeTheme(light, light = true)
        assertNativeTheme(dark, light = false)
    }

    @Test
    fun dayNightAliasSelectsTheMatchingNativeTheme() {
        val day = context.withNightMode(Configuration.UI_MODE_NIGHT_NO)
        val night = context.withNightMode(Configuration.UI_MODE_NIGHT_YES)

        assertNativeTheme(ContextThemeWrapper(day, R.style.Theme_MyCodex), light = true)
        assertNativeTheme(ContextThemeWrapper(night, R.style.Theme_MyCodex), light = false)
    }

    private fun assertNativeTheme(themed: Context, light: Boolean) {
        val expectedBackground = if (light) AppLightBackgroundArgb else AppDarkBackgroundArgb
        val expectedPrimary = if (light) AppLightPrimaryArgb else AppDarkPrimaryArgb
        assertEquals(expectedBackground, themed.color(android.R.attr.windowBackground))
        assertEquals(expectedBackground, themed.color(android.R.attr.colorBackground))
        assertEquals(expectedPrimary, themed.color(android.R.attr.colorAccent))
        assertEquals(expectedPrimary, themed.color(android.R.attr.colorControlActivated))
        assertEquals(light, themed.boolean(android.R.attr.windowLightStatusBar))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            assertEquals(light, themed.boolean(android.R.attr.windowLightNavigationBar))
        }
    }

    private fun Context.withNightMode(mode: Int): Context {
        val configuration = Configuration(resources.configuration)
        configuration.uiMode =
            configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or mode
        return createConfigurationContext(configuration)
    }

    private fun Context.color(attribute: Int): Long {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attribute, value, true))
        return value.data.toUInt().toLong()
    }

    private fun Context.boolean(attribute: Int): Boolean {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attribute, value, true))
        return value.data != 0
    }
}
