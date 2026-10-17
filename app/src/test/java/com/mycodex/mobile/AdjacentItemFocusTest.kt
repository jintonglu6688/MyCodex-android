package com.mycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdjacentItemFocusTest {
    @Test
    fun prefersNextThenPreviousAndFallsBackNearOldIndex() {
        assertEquals("c", adjacentItemId(listOf("a", "b", "c"), "b", listOf("a", "c")))
        assertEquals("b", adjacentItemId(listOf("a", "b", "c"), "c", listOf("a", "b")))
        assertEquals("z", adjacentItemId(listOf("a", "b", "c"), "b", listOf("x", "z")))
        assertNull(adjacentItemId(listOf("a"), "a", emptyList()))
    }
}
