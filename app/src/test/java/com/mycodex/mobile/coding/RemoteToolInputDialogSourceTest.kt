package com.mycodex.mobile.coding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteToolInputDialogSourceTest {
    @Test
    fun dialogUsesSingleFocusableOptionRowsAndLiveStatus() {
        val source = readSource("app", "src", "main", "java", "com", "mycodex", "mobile", "coding", "RemoteToolInputDialog.kt")

        assertTrue(source.contains("clearAndSetSemantics"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.contains("modifier = Modifier.semantics { heading() }"))
        assertTrue(source.contains("onDismissRequest = {}"))
        assertFalse(source.contains("按钮"))
        assertFalse(source.contains("单选按钮"))
        assertFalse(source.contains("title = { Text(request.title) }"))
        assertFalse(source.contains("request.headerText"))
        assertFalse(source.contains("request.detailsText"))
        assertFalse(source.contains("question.header"))
        assertFalse(source.contains("question.description"))
        assertFalse(source.contains("listOfNotNull(option.label, option.description)"))
        assertTrue(source.contains("contentDescription = option.label"))
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
