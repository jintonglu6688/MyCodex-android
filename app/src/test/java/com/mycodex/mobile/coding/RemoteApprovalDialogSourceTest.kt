package com.mycodex.mobile.coding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteApprovalDialogSourceTest {
    @Test
    fun defaultDialogBodyUsesQuestionTextOnly() {
        val source = readSource("app", "src", "main", "java", "com", "mycodex", "mobile", "coding", "RemoteApprovalDialog.kt")

        assertTrue(source.contains("Text(request.questionText)"))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.contains("enabled = option.enabled && !state.submitting"))
        assertFalse(source.contains("request.summaryText"))
        assertFalse(source.contains("request.detailsText"))
    }

    private fun readSource(vararg parts: String): String {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            val root = directory
            val candidate = parts.fold(root) { current, part -> File(current, part) }
            if (candidate.exists()) {
                return candidate.readText()
            }

            directory = root.parentFile
        }

        throw AssertionError("Could not locate source file: ${parts.joinToString(File.separator)}")
    }
}
