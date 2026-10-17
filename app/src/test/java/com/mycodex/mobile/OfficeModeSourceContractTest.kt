package com.mycodex.mobile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeModeSourceContractTest {
    @Test
    fun officeTabUsesSingleWorkspaceSessionRootWithoutCategoryNavigation() {
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val screenSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")

        assertTrue(appSource.contains("MainTabDestination.Office -> OfficeSessionRootPage("))
        assertTrue(screenSource.contains("fun OfficeSessionRootPage("))
        assertFalse(appSource.contains("showCreateOfficeWorkspaceDialog"))
        assertFalse(appSource.contains("AppScreen.OfficeSessions"))
    }

    @Test
    fun officeRepositoryDoesNotExposeRemovedCategoryCreationProtocol() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingRemoteRepository.kt")

        assertFalse(source.contains("category.create"))
        assertFalse(source.contains("category.created"))
        assertFalse(source.contains("createWorkspace("))
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not find project file: $relativePath")
        return file.readText(Charsets.UTF_8)
    }
}
