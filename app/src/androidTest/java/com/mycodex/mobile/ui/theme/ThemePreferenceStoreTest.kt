package com.mycodex.mobile.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemePreferenceStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences =
        context.getSharedPreferences(ThemePreferenceStore.PreferencesName, Context.MODE_PRIVATE)

    @Before
    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun storeDefaultsToSystemAndPersistsEveryMode() {
        val store = ThemePreferenceStore(context)
        assertEquals(ThemeMode.System, store.load())
        ThemeMode.entries.forEach {
            store.save(it)
            assertEquals(it, store.load())
        }
    }

    @Test
    fun corruptedValueFallsBackToSystem() {
        preferences.edit()
            .putString(ThemePreferenceStore.ThemeModeKey, "corrupted")
            .commit()
        assertEquals(ThemeMode.System, ThemePreferenceStore(context).load())
    }
}
