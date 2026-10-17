package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class RemoteConversationProtocolContractTest {
    @Test fun paginationFixture_hasFrozenSha256() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("protocol/remote-conversation-pagination-v1.json")!!.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("e7b596623d2e169ed33356ea3b9191944a6ef35b7b471851eeee5d20ad37255f", hash)
    }
}
