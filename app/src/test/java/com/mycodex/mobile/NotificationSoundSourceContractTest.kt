package com.mycodex.mobile

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSoundSourceContractTest {
    @Test
    fun localSettingsExposeFourAccessibleSoundToggles() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/LocalSettingsPage.kt")

        assertTrue(source.contains("local_sound_turn_completed"))
        assertTrue(source.contains("local_sound_subagent_completed"))
        assertTrue(source.contains("local_sound_approval_requested"))
        assertTrue(source.contains("local_sound_message_sent"))
        assertTrue(source.contains("val stateText = if (enabled) \"已开启\" else \"已关闭\""))
        assertTrue(source.contains("contentDescription = \"\$title，\$stateText\""))
    }

    @Test
    fun playerUsesNotificationAudioAttributesAndAllPackagedSounds() {
        val source = readProjectFile(
            "src/main/java/com/mycodex/mobile/notifications/NotificationSoundCoordinator.kt",
        )

        assertTrue(source.contains("AudioAttributes.USAGE_NOTIFICATION_EVENT"))
        assertTrue(source.contains("R.raw.approval"))
        assertTrue(source.contains("R.raw.end_of_session"))
        assertTrue(source.contains("R.raw.subagent_completed"))
        assertTrue(source.contains("R.raw.msg_sent"))

        listOf(
            "approval.wav",
            "end_of_session.wav",
            "subagent_completed.wav",
            "msg_sent.wav",
        ).forEach { name ->
            assertTrue(projectFile("src/main/res/raw/$name").length() > 0L)
        }
    }

    @Test
    fun sentCueRunsOnlyAfterSendIsAccepted() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")

        assertTrue(
            Regex(
                """controller\.send\(submittedText, attachments\) \{\s*""" +
                    """draft\.clearAccepted\(submittedText, attachments\)\s*onMessageSent\(\)""",
            ).containsMatchIn(source),
        )
    }

    private fun readProjectFile(relativePath: String): String {
        return projectFile(relativePath).readText(Charsets.UTF_8)
    }

    private fun projectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not find project file: $relativePath")
    }
}
