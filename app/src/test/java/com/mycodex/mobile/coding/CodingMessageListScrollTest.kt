package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodingMessageListScrollTest {
    @Test
    fun initialScrollTargetPointsToLastMessageWhenMessagesAreLoaded() {
        val result = CodingListResult(
            state = CodingLoadState.Loaded,
            items = listOf(
                CodingMessage("message_a", CodingMessageRole.User, "first", null),
                CodingMessage("message_b", CodingMessageRole.Assistant, "last", null),
            ),
        )

        assertEquals(2, result.initialMessageListScrollIndex())
    }

    @Test
    fun initialScrollTargetIsMissingWhenMessagesAreUnavailableOrEmpty() {
        assertNull(CodingListResult<CodingMessage>(CodingLoadState.Unavailable).initialMessageListScrollIndex())
        assertNull(CodingListResult<CodingMessage>(CodingLoadState.Loaded).initialMessageListScrollIndex())
    }

    @Test
    fun shouldFollowNewMessagesOnlyWhenAlreadyAtBottom() {
        assertEquals(true, shouldAutoFollowNewMessages(lastVisibleIndex = 4, totalItems = 5))
        assertEquals(false, shouldAutoFollowNewMessages(lastVisibleIndex = 1, totalItems = 5))
    }

    @Test
    fun scrollToBottomButtonShowsOnlyWhenLastItemIsNotVisible() {
        assertEquals(false, shouldShowMessageListBottomButton(lastVisibleItemIndex = null, totalItems = 0))
        assertEquals(false, shouldShowMessageListBottomButton(lastVisibleItemIndex = 4, totalItems = 5))
        assertEquals(false, shouldShowMessageListBottomButton(lastVisibleItemIndex = 0, totalItems = 1))
        assertEquals(true, shouldShowMessageListBottomButton(lastVisibleItemIndex = 2, totalItems = 5))
    }
}
