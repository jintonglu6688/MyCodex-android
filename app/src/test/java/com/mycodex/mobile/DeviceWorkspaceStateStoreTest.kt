package com.mycodex.mobile

import androidx.lifecycle.SavedStateHandle
import com.mycodex.mobile.coding.CodingAttachmentKind
import com.mycodex.mobile.coding.CodingDraftViewModel
import com.mycodex.mobile.coding.CodingInputAttachment
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceWorkspaceStateStoreTest {
    @Test fun hostRoundTripRetainsBothModesAndRemoteAttachmentReferencesWithoutCrossingSameSessionIds() {
        val values = mutableMapOf<String, String>()
        val store = DeviceWorkspaceStateStore(values::get) { key, value -> values[key] = value; true }
        val coding = CodingDraftViewModel().apply {
            bindConversation("A:Coding:workspace:same-session")
            text = "coding A"
            attachments = listOf(CodingInputAttachment(CodingAttachmentKind.Skill, "skill", "skill", "Skill", "/home/a/skill"))
            uncertainSend = true
        }
        val office = CodingDraftViewModel().apply { bindConversation("A:Office:workspace:same-session"); text = "office A" }
        store.save("A", JSONObject().put("coding", coding.snapshot()).put("office", office.snapshot()))
        coding.restore(null)
        coding.bindConversation("B:Coding:workspace:same-session")
        coding.text = "coding B"
        store.save("B", JSONObject().put("coding", coding.snapshot()))
        val restoredA = requireNotNull(store.load("A"))
        coding.restore(restoredA.getJSONObject("coding"))
        office.restore(restoredA.getJSONObject("office"))
        assertEquals("coding A", coding.text)
        assertEquals("office A", office.text)
        assertEquals("/home/a/skill", coding.attachments.single().path)
        assertTrue(coding.uncertainSend)
        coding.bindConversation("A:Coding:workspace:same-session")
        assertEquals("coding A", coding.text)
        coding.restore(store.load("B")!!.getJSONObject("coding"))
        assertEquals("coding B", coding.text)
        assertTrue(coding.attachments.isEmpty())
        assertNull(store.load("unknown"))
    }

    @Test fun pagesAndScrollPositionsRoundTripPerHostWithoutReusingSameSessionIdentity() {
        val values = mutableMapOf<String, String>()
        val store = DeviceWorkspaceStateStore(values::get) { key, value -> values[key] = value; true }
        val navigation = AppNavigationViewModel(SavedStateHandle()).apply {
            selectedRoute = MainTabDestination.Office.route
            activeScreen = AppScreen.OfficeChat.name
            codingSessionId = "same-session"
            codingSessionTitle = "A coding"
            officeSessionId = "same-session"
            officeSessionTitle = "A office"
        }
        store.save("A", JSONObject().put("navigation", navigation.workspaceSnapshot()).put("codingSessionIndex", 7).put("codingSessionOffset", 23))
        navigation.restoreWorkspace(null)
        navigation.codingSessionId = "same-session"
        navigation.codingSessionTitle = "B coding"
        store.save("B", JSONObject().put("navigation", navigation.workspaceSnapshot()).put("codingSessionIndex", 2))
        val a = store.load("A")!!
        navigation.restoreWorkspace(a.getJSONObject("navigation"))
        assertEquals("A coding", navigation.codingSessionTitle)
        assertEquals("A office", navigation.officeSessionTitle)
        assertEquals(AppScreen.OfficeChat.name, navigation.activeScreen)
        assertEquals(7, a.getInt("codingSessionIndex"))
        assertEquals(23, a.getInt("codingSessionOffset"))
        navigation.restoreWorkspace(store.load("B")!!.getJSONObject("navigation"))
        assertEquals("B coding", navigation.codingSessionTitle)
        assertNull(navigation.officeSessionId)
    }

    @Test fun invalidAttachmentSnapshotIsRejectedBeforeSelectionCanCommit() {
        val store = DeviceWorkspaceStateStore({ "{\"coding\":{\"attachments\":[{\"kind\":\"Unknown\"}]}}" }, { _, _ -> error("must not write") })
        try { store.load("A"); fail("must fail") } catch (_: IllegalArgumentException) {}
    }

    @Test fun rejectedWriteLeavesPreviousHostSnapshotUntouched() {
        val values = mutableMapOf<String, String>()
        var accept = true
        val store = DeviceWorkspaceStateStore(values::get) { key, value -> if (accept) values[key] = value; accept }
        store.save("A", JSONObject().put("text", "old"))
        accept = false
        try { store.save("A", JSONObject().put("text", "new")); fail("must fail") } catch (_: IllegalStateException) {}
        assertEquals("old", store.load("A")!!.getString("text"))
    }

    @Test fun backgroundSaveUsesQueuedWriterAndRoundTrips() {
        val values = mutableMapOf<String, String>()
        val store = DeviceWorkspaceStateStore(
            read = values::get,
            write = { _, _ -> error("must not block") },
            writeInBackground = { key, value -> values[key] = value },
        )
        store.saveInBackground("A", JSONObject().put("text", "typed"))
        assertEquals("typed", store.load("A")!!.getString("text"))
    }

    @Test fun malformedPersistedDataIsReportedAndNeverReplacedWithDefaults() {
        val store = DeviceWorkspaceStateStore({ "not-json" }, { _, _ -> error("must not write") })
        try { store.load("A"); fail("must fail") } catch (_: org.json.JSONException) {}
    }
}
