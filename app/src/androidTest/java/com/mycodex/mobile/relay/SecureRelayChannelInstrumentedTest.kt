package com.mycodex.mobile.relay

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.mycodex.mobile.devices.DeviceBinding
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SecureRemoteTranscript
import com.mycodex.mobile.security.SessionHello
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

class SecureRelayChannelInstrumentedTest {
    @Test
    fun secureChannelDoesNotChangeIdentityOrBinding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val aliasesBefore = aliases()
        val identityBefore = context.getSharedPreferences("local_device_identity", 0).all.toSortedMap().toString()
        val bindingBefore = context.getSharedPreferences("managed_devices", 0).all.toSortedMap().toString()

        val deviceSigning = keyPair(); val deviceAgreement = keyPair(); val hostSigning = keyPair(); val hostAgreement = keyPair(); val hostEphemeral = keyPair()
        val binding = DeviceBinding("https://relay.example", encoded(keyPair().public), encoded(ByteArray(32)), null, "tenant", "host", "Host", "device", encoded(hostSigning.public), encoded(hostAgreement.public), encoded(deviceSigning.public), encoded(deviceAgreement.public), 1, 1, 1)
        val sent = mutableListOf<String>()
        val channel = SecureRelayChannel(binding, { SecureRemoteCrypto.signRawEcdsa(deviceSigning.private, it) }, { sent += it; true })
        assertTrue(channel.start())
        val client = SecureRelayFrames.parse(sent.single()) as SecureRelayFrame.Hello
        val nonce = ByteArray(32) { 9 }
        val hello = SessionHello(1, SecureRelayFrames.ServerHello, client.hello.sessionId, binding.tenantId, binding.hostId, binding.deviceId, SecureRelayFrames.WindowsToMobile, 1, 1, SecureRemoteCrypto.encodeP256PublicKey(hostEphemeral.public), nonce, 2)
        val serverTranscript = SecureRemoteTranscript.serverHello(SecureRemoteTranscript.sessionHello(client.hello), client.signature, hello)
        val signature = SecureRemoteCrypto.signRawEcdsa(hostSigning.private, serverTranscript)
        channel.receive(SecureRelayFrames.serializeHello(hello, signature))
        val complete = SecureRemoteTranscript.sessionComplete(serverTranscript, signature)
        val master = SecureRemoteCrypto.hkdfSha256(SecureRemoteCrypto.deriveAgreement(hostEphemeral.private, client.hello.ephemeralPublicKey), SecureRemoteCrypto.sha256(client.hello.nonce + nonce), complete, 32)
        val confirmationKey = SecureRemoteCrypto.hkdfSha256(master, byteArrayOf(), "session confirmation".toByteArray(), 32)
        channel.receive(SecureRelayFrames.serializeConfirmation(client.hello.sessionId, binding.tenantId, binding.hostId, binding.deviceId, SecureRelayFrames.WindowsToMobile, SecureRelayFrames.Windows, SecureRemoteCrypto.hmacSha256(confirmationKey, SecureRemoteTranscript.sessionConfirmation(complete, SecureRelayFrames.Windows))))
        assertEquals(SecureRelayChannel.State.Established, channel.currentState())

        assertEquals(aliasesBefore, aliases())
        assertEquals(identityBefore, context.getSharedPreferences("local_device_identity", 0).all.toSortedMap().toString())
        assertEquals(bindingBefore, context.getSharedPreferences("managed_devices", 0).all.toSortedMap().toString())
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("mycodex_secure_remote_gate", "IDENTITY_BINDING_CHANNEL_VERIFIED")
        })
    }

    private fun aliases(): Set<String> = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.aliases().toList().toSet()
    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private fun encoded(value: java.security.PublicKey): String = SecureRemoteProtocol.encodeBase64Url(SecureRemoteCrypto.encodeP256PublicKey(value))
    private fun encoded(value: ByteArray): String = SecureRemoteProtocol.encodeBase64Url(value)
}
