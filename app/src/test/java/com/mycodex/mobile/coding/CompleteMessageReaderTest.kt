package com.mycodex.mobile.coding

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompleteMessageReaderTest {
    @Test
    fun completeReader_usesExplicitChunksAndSafStreaming() {
        val source = File("src/main/java/com/mycodex/mobile/coding/CodingCompleteMessagePage.kt").readText()
        assertTrue(source.contains("ActivityResultContracts.CreateDocument(\"text/plain\")"))
        assertTrue(source.contains("repository.readMessageContent"))
        assertTrue(source.contains("OutputStreamWriter(stream, Charsets.UTF_8)"))
        assertTrue(source.contains("while (!complete)"))
        assertTrue(!source.contains("createTempFile"))
    }
}
