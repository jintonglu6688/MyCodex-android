package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Test

class CodingMessagePagingViewportRestoreTest {
    @Test
    fun olderPageRestoreKeepsThePreviousBoundaryAfterTheLeadingLoadAction() {
        val messages = (1..200).map { index -> testMessage(index) }

        val restoredIndex = CodingMessagePagingViewportRestore.resolveLazyListIndex(
            messages = messages,
            pagingSupported = true,
            hasOlder = true,
            messageId = "message-101",
        )

        assertEquals(101, restoredIndex)
    }

    @Test
    fun finalOlderPageRestoreDoesNotReserveAnIndexForAHiddenLoadAction() {
        val messages = (1..200).map { index -> testMessage(index) }

        val restoredIndex = CodingMessagePagingViewportRestore.resolveLazyListIndex(
            messages = messages,
            pagingSupported = true,
            hasOlder = false,
            messageId = "message-101",
        )

        assertEquals(100, restoredIndex)
    }

    @Test
    fun latestMessageAlsoAccountsForTheLeadingLoadAction() {
        val messages = (1..200).map { index -> testMessage(index) }

        val restoredIndex = CodingMessagePagingViewportRestore.resolveLazyListIndex(
            messages = messages,
            pagingSupported = true,
            hasOlder = true,
            messageId = "message-200",
        )

        assertEquals(200, restoredIndex)
    }

    private fun testMessage(index: Int) = CodingMessage(
        id = "message-$index",
        role = CodingMessageRole.User,
        content = "message $index",
        createdAt = null,
    )
}
