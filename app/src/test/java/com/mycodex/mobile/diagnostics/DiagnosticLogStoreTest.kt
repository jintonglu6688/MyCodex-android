package com.mycodex.mobile.diagnostics

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogStoreTest {
    @Test
    fun createsSessionLogAndRedactsSecretsFromShareReport() {
        val root = createTempDirectory("mycodex-diagnostics-").toFile()
        try {
            val logs = File(root, "logs")
            val store = DiagnosticLogStore(logs, now = { 1_775_300_000_000L }, processId = 42)
            store.append("INFO", "Remote", "Authorization: Bearer top-secret ticket=abc")

            val report = store.createShareReport(File(root, "share"), "header\n").readText()

            assertTrue(report.contains("[Remote]"))
            assertTrue(report.contains("[REDACTED]"))
            assertFalse(report.contains("top-secret"))
            assertFalse(report.contains("ticket=abc"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun retainsOnlyConfiguredNumberOfSessionFiles() {
        val root = createTempDirectory("mycodex-diagnostics-retention-").toFile()
        try {
            var now = 1_775_300_000_000L
            repeat(4) {
                DiagnosticLogStore(root, now = { now++ }, processId = it, retainedFiles = 2)
                    .append("INFO", "Test", "entry")
            }

            assertEquals(2, root.listFiles { file -> file.name.startsWith("android-") }?.size)
        } finally {
            root.deleteRecursively()
        }
    }
}
