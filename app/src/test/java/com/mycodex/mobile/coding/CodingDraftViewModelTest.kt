package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodingDraftViewModelTest {
    @Test
    fun acceptedSendOnlyClearsTheDraftThatWasSubmitted() {
        val draft = CodingDraftViewModel()
        draft.text = "hello"
        draft.attachments = emptyList()
        draft.clearAccepted("hello", emptyList())
        assertEquals("", draft.text)
        assertTrue(draft.attachments.isEmpty())

        draft.text = "new text"
        draft.clearAccepted("old text", emptyList())
        assertEquals("new text", draft.text)
    }

    @Test
    fun conversationSwitchAndExplicitLeaveClearAllTransientComposerState() {
        val draft = CodingDraftViewModel()
        draft.bindConversation("coding:workspace-a:session-a")
        draft.text = "private draft"
        draft.sessionCreateTitle = "new session"
        draft.forkTitle = "fork"
        draft.deriveTitle = "derive"

        draft.bindConversation("coding:workspace-a:session-b")

        assertEquals("", draft.text)
        assertTrue(draft.attachments.isEmpty())
        assertEquals("", draft.forkTitle)
        assertEquals("", draft.deriveTitle)
        assertEquals("new session", draft.sessionCreateTitle)

        draft.clearAll()
        assertEquals("", draft.sessionCreateTitle)
    }
}
