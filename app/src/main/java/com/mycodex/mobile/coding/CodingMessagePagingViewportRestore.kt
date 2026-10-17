package com.mycodex.mobile.coding

internal object CodingMessagePagingViewportRestore {
    fun resolveLazyListIndex(
        messages: List<CodingMessage>,
        pagingSupported: Boolean,
        hasOlder: Boolean,
        messageId: String,
    ): Int? {
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex < 0) {
            return null
        }

        val leadingLoadActionCount = if (pagingSupported && hasOlder) 1 else 0
        return leadingLoadActionCount + messageIndex
    }
}
