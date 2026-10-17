package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodingComposerStateTest {
    @Test
    fun blankDraftCannotBeSent() {
        val draft = CodingComposerState(text = " \n\t ")

        assertFalse(draft.canSend)
        assertEquals("", draft.submittedText)
    }

    @Test
    fun nonBlankDraftCanBeSentWithTrimmedText() {
        val draft = CodingComposerState(text = "  帮我继续推进  ")

        assertTrue(draft.canSend)
        assertEquals("帮我继续推进", draft.submittedText)
    }

    @Test
    fun attachmentOnlyDraftCanBeSent() {
        val draft = CodingComposerState(text = " \n\t ", attachmentCount = 1)

        assertTrue(draft.canSend)
        assertEquals("", draft.submittedText)
    }

    @Test
    fun sendingDraftCannotBeSentAgain() {
        val draft = CodingComposerState(text = "继续", attachmentCount = 1, sending = true)

        assertFalse(draft.canSend)
        assertEquals("继续", draft.submittedText)
    }

    @Test
    fun generatingDraftCanSendAndStop() {
        val draft = CodingComposerState(text = "继续", generating = true)

        assertTrue(draft.canSend)
        assertTrue(draft.canStop)
    }

    @Test
    fun generatingWithoutDraftCanStopButCannotSend() {
        val draft = CodingComposerState(text = "", generating = true)

        assertFalse(draft.canSend)
        assertTrue(draft.canStop)
    }

    @Test
    fun sendingFollowUpCannotAlsoStop() {
        val draft = CodingComposerState(text = "继续", sending = true, generating = true)

        assertFalse(draft.canSend)
        assertFalse(draft.canStop)
    }

    @Test
    fun stoppingDraftCannotStopAgain() {
        val draft = CodingComposerState(text = "继续", generating = true, stopping = true)

        assertFalse(draft.canSend)
        assertFalse(draft.canStop)
    }

    @Test
    fun composerStacksForNarrowWidthOrLargeText() {
        assertFalse(shouldStackCodingComposer(maxWidthDp = 360f, fontScale = 1f))
        assertTrue(shouldStackCodingComposer(maxWidthDp = 359f, fontScale = 1f))
        assertTrue(shouldStackCodingComposer(maxWidthDp = 600f, fontScale = 1.3f))
        assertTrue(shouldStackCodingComposer(maxWidthDp = 600f, fontScale = 2f))
        assertTrue(shouldStackCodingComposer(maxWidthDp = 400f, fontScale = 1f, showStopAction = true))
        assertFalse(shouldStackCodingComposer(maxWidthDp = 600f, fontScale = 1f, showStopAction = true))
    }
}
