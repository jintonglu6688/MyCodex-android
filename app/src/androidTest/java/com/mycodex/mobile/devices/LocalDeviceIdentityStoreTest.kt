package com.mycodex.mobile.devices

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.mycodex.mobile.security.SecureRemoteCrypto
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDeviceIdentityStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun loadTwiceReturnsSamePublicKeys() {
        val store = LocalDeviceIdentityStore(context)

        val first = store.load()
        val second = LocalDeviceIdentityStore(context).load()

        assertTrue(
            first.deviceId.matches(
                Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            ),
        )
        assertEquals(first.deviceId, second.deviceId)
        assertEquals(first.keyVersion, second.keyVersion)
        assertEquals(first.signingPublicKey, second.signingPublicKey)
        assertEquals(first.agreementPublicKey, second.agreementPublicKey)
        assertEquals(65, java.util.Base64.getUrlDecoder().decode(first.signingPublicKey).size)
        assertEquals(65, java.util.Base64.getUrlDecoder().decode(first.agreementPublicKey).size)

        val transcript = "identity-proof".toByteArray(Charsets.UTF_8)
        val signature = store.sign(transcript)
        assertEquals(64, signature.size)
        assertTrue(
            SecureRemoteCrypto.verifyRawEcdsa(
                publicKey = java.util.Base64.getUrlDecoder().decode(first.signingPublicKey),
                transcript = transcript,
                rawSignature = signature,
            ),
        )
        assertArrayEquals(
            store.deriveAgreement(java.util.Base64.getUrlDecoder().decode(first.agreementPublicKey)),
            store.deriveAgreement(java.util.Base64.getUrlDecoder().decode(second.agreementPublicKey)),
        )
    }

    @Test
    fun privateKeyMaterialIsNotStoredInPreferences() {
        LocalDeviceIdentityStore(context).load()

        val stored = context
            .getSharedPreferences("local_device_identity", Context.MODE_PRIVATE)
            .all

        assertEquals(setOf("device_id", "key_version"), stored.keys)
        assertFalse(stored.values.any { it is ByteArray })
        assertFalse(stored.values.any { it.toString().contains("PRIVATE", ignoreCase = true) })
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertEquals(
            null,
            (keyStore.getEntry("mycodex.remote.device.signing.v1", null) as KeyStore.PrivateKeyEntry)
                .privateKey
                .encoded,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertEquals(
                null,
                (keyStore.getEntry("mycodex.remote.device.agreement.v1", null) as KeyStore.PrivateKeyEntry)
                    .privateKey
                    .encoded,
            )
            assertFalse(
                java.io.File(
                    context.noBackupFilesDir,
                    "remote-device-agreement-v1.bin",
                ).exists(),
            )
        } else {
            assertTrue(
                java.io.File(
                    context.noBackupFilesDir,
                    "remote-device-agreement-v1.bin",
                ).isFile,
            )
        }
    }

    @Test
    fun missingKeyCreatesNewDeviceIdentity() {
        val first = LocalDeviceIdentityStore(context).load()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry("mycodex.remote.device.agreement.v1")
            }
        } else {
            java.io.File(
                context.noBackupFilesDir,
                "remote-device-agreement-v1.bin",
            ).delete()
        }

        val replacement = LocalDeviceIdentityStore(context).load()

        assertFalse(first.deviceId == replacement.deviceId)
        assertFalse(first.signingPublicKey == replacement.signingPublicKey)
        assertFalse(first.agreementPublicKey == replacement.agreementPublicKey)
    }

    @Test
    fun deriveAgreementNeverCreatesANewIdentity() {
        resetIdentity()
        val peer = SecureRemoteCrypto.encodeP256PublicKey(
            java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair().public,
        )

        assertThrows(DeviceIdentityException::class.java) {
            LocalDeviceIdentityStore(context).deriveAgreement(peer)
        }
        assertFalse(
            context.getSharedPreferences("local_device_identity", Context.MODE_PRIVATE)
                .contains("device_id"),
        )
    }

    @Test
    fun concurrentStoresReturnOneIdentityAndPublicKeySet() {
        resetIdentity()
        val workerCount = 12
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workerCount)
        try {
            val futures = (1..workerCount).map {
                executor.submit<LocalDeviceIdentity> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    LocalDeviceIdentityStore(context).load()
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val identities = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, identities.map { it.deviceId }.toSet().size)
            assertEquals(1, identities.map { it.signingPublicKey }.toSet().size)
            assertEquals(1, identities.map { it.agreementPublicKey }.toSet().size)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun resetIdentity() {
        context.getSharedPreferences("local_device_identity", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry("mycodex.remote.device.signing.v1")
            deleteEntry("mycodex.remote.device.agreement.v1")
            deleteEntry("mycodex.remote.device.agreement.wrap.v1")
        }
        java.io.File(context.noBackupFilesDir, "remote-device-agreement-v1.bin").delete()
    }
}
