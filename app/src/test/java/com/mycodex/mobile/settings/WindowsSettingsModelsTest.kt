package com.mycodex.mobile.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsSettingsModelsTest {
    @Test
    fun approvalMode_usesStableWireValues() {
        assertEquals(WindowsApprovalMode.ReadOnly, WindowsApprovalMode.fromWire("read-only"))
        assertEquals(WindowsApprovalMode.FullAccess, WindowsApprovalMode.fromWire("full-access"))
        assertEquals("custom", WindowsApprovalMode.Custom.wireValue)
        assertNull(WindowsApprovalMode.fromWire("unsafe"))
    }

    @Test
    fun selectingModel_usesCompatibleCurrentThenDefaultThenFirstEffort() {
        val snapshot = testWindowsSettingsSnapshot()
        val draft = WindowsSettingsDraft.from(snapshot)
        val models = listOf(
            WindowsSettingsModelOption("keep", "Keep", "low", listOf("high", "low")),
            WindowsSettingsModelOption("default", "Default", "medium", listOf("low", "medium")),
            WindowsSettingsModelOption("first", "First", "missing", listOf("minimal")),
            WindowsSettingsModelOption("none", "None", null, emptyList()),
        )

        assertEquals("high", draft.selectModel("keep", models).defaults.reasoningEffort)
        assertEquals("medium", draft.selectModel("default", models).defaults.reasoningEffort)
        assertEquals("minimal", draft.selectModel("first", models).defaults.reasoningEffort)
        assertNull(draft.selectModel("none", models).defaults.reasoningEffort)
    }

    @Test
    fun commandPrefixes_trimDropBlankAndDeduplicateOrdinal() {
        val draft = WindowsSettingsDraft.from(testWindowsSettingsSnapshot())
            .withAddedPrefix(" git status ")
            .withAddedPrefix("")
            .withAddedPrefix("GIT STATUS")
            .withAddedPrefix("git status")

        assertEquals(listOf("git status", "GIT STATUS"), draft.customApproval.commandPrefixes)
        assertEquals(
            listOf("dotnet test", "GIT STATUS"),
            draft.withReplacedPrefix(0, "dotnet test").customApproval.commandPrefixes,
        )
    }

    @Test
    fun changedSectionDetection_onlyBuildsEditedSection() {
        val snapshot = testWindowsSettingsSnapshot()
        val draft = WindowsSettingsDraft.from(snapshot)

        assertNull(draft.defaultsUpdate(snapshot))
        assertNull(draft.customApprovalUpdate(snapshot))
        assertNull(draft.officialUpdate(snapshot))
        val update = draft.copy(
            defaults = draft.defaults.copy(approvalMode = WindowsApprovalMode.FullAccess),
        ).defaultsUpdate(snapshot)
        assertTrue(update!!.defaults != null)
        assertNull(update.customApproval)
        assertNull(update.official)
    }

    @Test
    fun officialChoices_matchWindowsPolicy() {
        assertEquals(listOf(null, "auto", "concise", "detailed", "none"), WindowsOfficialChoices.reasoningSummaries)
        assertEquals(listOf(null, "low", "medium", "high"), WindowsOfficialChoices.verbosities)
        assertEquals(listOf(null, "disabled", "cached", "indexed", "live"), WindowsOfficialChoices.webSearchModes)
        assertEquals(listOf(null, "low", "medium", "high"), WindowsOfficialChoices.searchContextSizes)
        assertEquals(listOf<Boolean?>(null, true, false), WindowsOfficialChoices.viewImageValues)
        assertEquals(listOf<Long?>(null, 8192L, 16384L, 32768L), WindowsOfficialChoices.toolOutputLimits)
        assertEquals(listOf<Long?>(null, 65536L, 131072L, 262144L), WindowsOfficialChoices.projectDocLimits)
    }

    @Test
    fun officialValues_useWindowsChinesePresentation() {
        assertEquals("跟随默认", displayOfficialReasoningSummary(null))
        assertEquals("自动", displayOfficialReasoningSummary("auto"))
        assertEquals("简短", displayOfficialReasoningSummary("concise"))
        assertEquals("详细", displayOfficialReasoningSummary("detailed"))
        assertEquals("无", displayOfficialReasoningSummary("none"))
        assertEquals("future", displayOfficialReasoningSummary("future"))

        assertEquals("低", displayOfficialLevel("low"))
        assertEquals("中", displayOfficialLevel("medium"))
        assertEquals("高", displayOfficialLevel("high"))
        assertEquals("跟随默认", displayOfficialLevel(null))

        assertEquals("禁用", displayOfficialWebSearch("disabled"))
        assertEquals("缓存", displayOfficialWebSearch("cached"))
        assertEquals("索引", displayOfficialWebSearch("indexed"))
        assertEquals("实时", displayOfficialWebSearch("live"))
        assertEquals("跟随默认", displayOfficialWebSearch(null))

        assertEquals("跟随默认", displayOfficialBoolean(null))
        assertEquals("启用", displayOfficialBoolean(true))
        assertEquals("禁用", displayOfficialBoolean(false))
    }

    @Test
    fun officialLimits_useWindowsFormattedPresentation() {
        assertEquals("8K", displayToolOutputLimit(8192L))
        assertEquals("16K", displayToolOutputLimit(16384L))
        assertEquals("32K", displayToolOutputLimit(32768L))
        assertEquals("跟随默认", displayToolOutputLimit(null))

        assertEquals("64 KB", displayProjectDocLimit(65536L))
        assertEquals("128 KB", displayProjectDocLimit(131072L))
        assertEquals("256 KB", displayProjectDocLimit(262144L))
        assertEquals("跟随默认", displayProjectDocLimit(null))
    }
}

internal fun testWindowsSettingsSnapshot(): WindowsSettingsSnapshot {
    return WindowsSettingsSnapshot(
        scope = WindowsSettingsScope(
            hostName = "PC",
            runtimeSource = "Wsl",
            runtimeDisplayName = "Ubuntu",
            runtimeProfileId = null,
            authenticationSourceKind = "chatgpt",
            authenticationDisplayName = "ChatGPT",
        ),
        defaults = WindowsSettingsDefaults(
            modelId = "current",
            reasoningEffort = "high",
            approvalMode = WindowsApprovalMode.Auto,
        ),
        models = listOf(
            WindowsSettingsModelOption(
                modelId = "current",
                displayName = "Current",
                defaultReasoningEffort = "high",
                reasoningEfforts = listOf("low", "high"),
            ),
        ),
        customApproval = WindowsCustomApprovalSettings(
            availablePermissions = listOf(WindowsPermissionOption("GitInspect", "Git 检查")),
            enabledPermissions = listOf("GitInspect"),
            commandPrefixes = listOf("git status"),
        ),
        official = WindowsOfficialSettings(
            modelReasoningSummary = null,
            modelVerbosity = null,
            serviceTier = null,
            webSearchMode = null,
            webSearchContextSize = null,
            viewImageToolEnabled = null,
            toolOutputTokenLimit = null,
            projectDocMaxBytes = null,
        ),
    )
}
