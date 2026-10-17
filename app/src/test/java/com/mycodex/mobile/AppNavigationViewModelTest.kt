package com.mycodex.mobile

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationViewModelTest {
    @Test
    fun codingConversationSurvivesConfigurationRecreation() {
        val savedState = SavedStateHandle()
        val navigation = AppNavigationViewModel(savedState)
        navigation.selectedRoute = MainTabDestination.Coding.route
        navigation.activeScreen = AppScreen.CodingChat.name
        navigation.codingWorkspaceId = "workspace-a"
        navigation.codingWorkspaceTitle = "Workspace A"
        navigation.codingSessionId = "session-a"
        navigation.codingSessionTitle = "Session A"

        val restored = AppNavigationViewModel(
            SavedStateHandle(savedState.keys().associateWith { key -> savedState.get<Any?>(key) }),
        )

        assertEquals(MainTabDestination.Coding.route, restored.selectedRoute)
        assertEquals(AppScreen.CodingChat.name, restored.activeScreen)
        assertEquals("workspace-a", restored.codingWorkspaceId)
        assertEquals("Workspace A", restored.codingWorkspaceTitle)
        assertEquals("session-a", restored.codingSessionId)
        assertEquals("Session A", restored.codingSessionTitle)
    }
}
