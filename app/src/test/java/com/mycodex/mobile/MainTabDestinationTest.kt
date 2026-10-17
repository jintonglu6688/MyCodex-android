package com.mycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabDestinationTest {
    @Test
    fun topLevelDestinationsMatchSupportedModes() {
        val destinations = MainTabDestination.entries

        assertEquals(listOf("编程", "办公", "我的"), destinations.map { it.label })
        assertEquals(listOf("coding", "office", "profile"), destinations.map { it.route })
        assertEquals(destinations.size, destinations.map { it.route }.distinct().size)
    }
}
