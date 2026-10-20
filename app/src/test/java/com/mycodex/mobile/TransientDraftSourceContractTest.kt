package com.mycodex.mobile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientDraftSourceContractTest {
    @Test
    fun sensitiveDraftHoldersDoNotUseSavedStateOrPersistentStores() {
        val files = listOf(
            "src/main/java/com/mycodex/mobile/coding/CodingDraftViewModel.kt",
            "src/main/java/com/mycodex/mobile/devices/PairingPageViewModel.kt",
            "src/main/java/com/mycodex/mobile/settings/WindowsSettingsViewModel.kt",
        )
        files.forEach { path ->
            val source = readProjectFile(path)
            listOf(
                "rememberSaveable",
                "SavedStateHandle",
                "SharedPreferences",
                "DevicePreferencesStore",
                "ThemePreferenceStore",
            ).forEach { forbidden ->
                assertFalse("$path must not use $forbidden", source.contains(forbidden))
            }
        }
    }

    @Test
    fun draftHoldersAreActivityRetainedViewModels() {
        val coding = readProjectFile(
            "src/main/java/com/mycodex/mobile/coding/CodingDraftViewModel.kt",
        )
        val pairing = readProjectFile(
            "src/main/java/com/mycodex/mobile/devices/PairingPageViewModel.kt",
        )
        val windows = readProjectFile(
            "src/main/java/com/mycodex/mobile/settings/WindowsSettingsViewModel.kt",
        )
        assertTrue(coding.contains(": ViewModel()"))
        assertTrue(pairing.contains(": ViewModel()"))
        assertTrue(windows.contains(": ViewModel()"))
        assertTrue(coding.contains("mutableStateOf(\"\")"))
        assertTrue(pairing.contains("var pairingUri by mutableStateOf(\"\")"))
    }

    @Test
    fun sensitivePairingAndSettingsStillHaveAnExplicitTransientClearBoundary() {
        val app = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val pairing = readProjectFile(
            "src/main/java/com/mycodex/mobile/devices/PairingPageViewModel.kt",
        )
        val windows = readProjectFile(
            "src/main/java/com/mycodex/mobile/settings/WindowsSettingsViewModel.kt",
        )
        assertTrue(app.contains("pairingTransient.clearTransientState()"))
        assertTrue(app.contains("windowsSettingsViewModel.clearTransientState()"))
        assertTrue(pairing.contains("fun clearTransientState()"))
        assertTrue(pairing.contains("controller.clearLocalState()"))
        assertTrue(windows.contains("fun clearTransientState()"))
    }

    @Test
    fun unsubmittedSessionAndForkTitlesNeverEnterSaveableState() {
        val app = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        listOf(
            "codingSessionCreateTitleText",
            "officeSessionCreateTitleText",
            "codingFullForkTitleText",
            "codingFullDeriveTitleText",
        ).forEach { removedSaveableProperty ->
            assertFalse(app.contains(removedSaveableProperty))
        }
        assertTrue(app.contains("codingTransient.sessionCreateTitle"))
        assertTrue(app.contains("officeTransient.sessionCreateTitle"))
        assertTrue(app.contains("codingTransient.forkTitle"))
    }

    private fun readProjectFile(relativePath: String): String {
        val file = listOf(File(relativePath), File("app/$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("Could not find $relativePath")
        return file.readText(Charsets.UTF_8)
    }
}
