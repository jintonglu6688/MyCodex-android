package com.mycodex.mobile.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WindowsSettingsControllerTest {
    @Test
    fun unsupportedAdvancedSettingsCannotBeEditedOrSent() = runTest {
        val repository = FakeDataSource()
        repository.nextLoadResult = WindowsSettingsLoadResult(
            state = WindowsSettingsLoadState.Loaded,
            settings = repository.snapshot.copy(official = null),
        )
        val controller = loadedController(repository)

        controller.updateOfficial { it.copy(serviceTier = "priority") }
        controller.saveOfficial {}
        advanceUntilIdle()

        assertNull(controller.state.value.draft!!.official)
        assertEquals(0, repository.updateCount)
        assertNull(controller.state.value.errorText)
    }

    @Test
    fun prefixEditorIsMemoryOnlyAndFreshControllerStartsEmpty() = runTest {
        val first = WindowsSettingsController(this, FakeDataSource())
        first.openPrefixEditor(null, "")
        first.updatePrefixEditor("sentinel command")
        assertEquals("sentinel command", first.prefixEditor?.value)

        val fresh = WindowsSettingsController(this, FakeDataSource())
        assertNull(fresh.prefixEditor)
    }

    @Test
    fun explicitLeaveClearsLoadedDraftPrefixEditorAndAutomaticLoadState() = runTest {
        val repository = FakeDataSource()
        val controller = loadedController(repository)
        controller.openPrefixEditor(null, "sentinel")

        controller.clearTransientState()

        assertEquals(WindowsSettingsUiState(), controller.state.value)
        assertNull(controller.prefixEditor)
        controller.loadIfNeeded()
        advanceUntilIdle()
        assertEquals(2, repository.loadCount)
    }

    @Test
    fun load_replacesSnapshotAndDraft() = runTest {
        val repository = FakeDataSource()
        val controller = WindowsSettingsController(this, repository)

        controller.load()
        advanceUntilIdle()

        assertSame(repository.snapshot, controller.state.value.snapshot)
        assertEquals(repository.snapshot.defaults, controller.state.value.draft!!.defaults)
        assertFalse(controller.state.value.loading)
    }

    @Test
    fun selectionMethodsNeverSave() = runTest {
        val repository = FakeDataSource()
        val controller = loadedController(repository)

        controller.selectApprovalMode(WindowsApprovalMode.Custom)
        controller.setEnabledPermission("GitInspect", false)
        controller.addCommandPrefix("dotnet test")
        controller.updateOfficial { it.copy(serviceTier = "priority") }
        advanceUntilIdle()

        assertEquals(0, repository.updateCount)
    }

    @Test
    fun successfulSaveReplacesServerSnapshotAndInvokesCallback() = runTest {
        val repository = FakeDataSource()
        val controller = loadedController(repository)
        controller.selectApprovalMode(WindowsApprovalMode.Custom)
        var saved = false

        controller.saveDefaults { saved = true }
        advanceUntilIdle()

        assertEquals(1, repository.updateCount)
        assertEquals(WindowsApprovalMode.Custom, repository.lastUpdate!!.defaults!!.approvalMode)
        assertSame(repository.updatedSnapshot, controller.state.value.snapshot)
        assertEquals(repository.updatedSnapshot.defaults, controller.state.value.draft!!.defaults)
        assertTrue(saved)
        assertNull(controller.state.value.errorText)
    }

    @Test
    fun loadIfNeededAfterSuccessfulSaveDoesNotReloadFreshSnapshot() = runTest {
        val repository = FakeDataSource()
        val controller = loadedController(repository)
        controller.selectApprovalMode(WindowsApprovalMode.Custom)

        controller.saveDefaults {}
        advanceUntilIdle()
        controller.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(1, repository.loadCount)
        assertSame(repository.updatedSnapshot, controller.state.value.snapshot)
    }

    @Test
    fun failedAutomaticLoadRetriesOnlyOnExplicitLoad() = runTest {
        val repository = FakeDataSource().apply {
            nextLoadResult = WindowsSettingsLoadResult(
                state = WindowsSettingsLoadState.Error,
                errorCode = "offline",
            )
        }
        val controller = WindowsSettingsController(this, repository)

        controller.loadIfNeeded()
        advanceUntilIdle()
        controller.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(1, repository.loadCount)

        controller.load()
        advanceUntilIdle()

        assertEquals(2, repository.loadCount)
    }

    @Test
    fun failedSaveKeepsExactEditedDraft() = runTest {
        val repository = FakeDataSource()
        repository.nextUpdateResult = WindowsSettingsUpdateResult(
            state = WindowsSettingsUpdateState.Error,
            errorCode = "write_lock_denied",
        )
        val controller = loadedController(repository)
        controller.addCommandPrefix("dotnet test")
        val editedDraft = controller.state.value.draft

        controller.saveCustomApproval {}
        advanceUntilIdle()

        assertSame(editedDraft, controller.state.value.draft)
        assertEquals("保存失败：write_lock_denied", controller.state.value.errorText)
        assertFalse(controller.state.value.saving)
    }

    @Test
    fun unchangedSaveSkipsRepositoryAndReturns() = runTest {
        val repository = FakeDataSource()
        val controller = loadedController(repository)
        var saved = false

        controller.saveOfficial { saved = true }
        advanceUntilIdle()

        assertEquals(0, repository.updateCount)
        assertTrue(saved)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.loadedController(
        repository: FakeDataSource,
    ): WindowsSettingsController {
        val controller = WindowsSettingsController(this, repository)
        controller.load()
        advanceUntilIdle()
        return controller
    }

    private class FakeDataSource : WindowsSettingsDataSource {
        val snapshot = testWindowsSettingsSnapshot()
        val updatedSnapshot = snapshot.copy(
            defaults = snapshot.defaults.copy(approvalMode = WindowsApprovalMode.Custom),
        )

        var updateCount = 0
        var loadCount = 0
        var lastUpdate: WindowsSettingsUpdate? = null
        var nextLoadResult = WindowsSettingsLoadResult(
            state = WindowsSettingsLoadState.Loaded,
            settings = snapshot,
        )
        var nextUpdateResult = WindowsSettingsUpdateResult(
            state = WindowsSettingsUpdateState.Completed,
            updated = true,
            settings = updatedSnapshot,
        )

        override suspend fun load(): WindowsSettingsLoadResult {
            loadCount += 1
            return nextLoadResult
        }

        override suspend fun update(update: WindowsSettingsUpdate): WindowsSettingsUpdateResult {
            updateCount += 1
            lastUpdate = update
            return nextUpdateResult
        }
    }
}
