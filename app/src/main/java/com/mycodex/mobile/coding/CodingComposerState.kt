package com.mycodex.mobile.coding

data class CodingComposerState(
    val text: String,
    val attachmentCount: Int = 0,
    val sending: Boolean = false,
    val generating: Boolean = false,
    val stopping: Boolean = false,
) {
    val submittedText: String
        get() = text.trim()

    val canSend: Boolean
        get() = (submittedText.isNotEmpty() || attachmentCount > 0) && !sending && !stopping

    val canStop: Boolean
        get() = generating && !sending && !stopping
}
