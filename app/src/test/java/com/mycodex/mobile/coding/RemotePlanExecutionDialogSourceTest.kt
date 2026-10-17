package com.mycodex.mobile.coding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemotePlanExecutionDialogSourceTest {
    @Test
    fun dialogShowsPlanAndCoreActionsWithoutControlTypeWords() {
        val source = readSource("app", "src", "main", "java", "com", "mycodex", "mobile", "coding", "RemotePlanExecutionDialog.kt")

        assertTrue(source.contains("Text(request.planText)"))
        assertTrue(source.contains("RemotePlanExecutionAction.Execute"))
        assertTrue(source.contains("RemotePlanExecutionAction.Continue"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.contains("enabled = option.enabled && !state.submitting"))
        assertFalse(source.contains("按钮"))
    }

    private fun readSource(vararg parts: String): String {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            val candidate = parts.fold(directory) { current, part -> File(current, part) }
            if (candidate.exists()) return candidate.readText()
            directory = directory.parentFile
        }
        throw AssertionError("Could not locate source file: ${parts.joinToString(File.separator)}")
    }
}
