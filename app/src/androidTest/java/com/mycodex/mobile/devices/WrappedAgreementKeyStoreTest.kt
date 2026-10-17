package com.mycodex.mobile.devices

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mycodex.mobile.security.SecureRemoteCrypto
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappedAgreementKeyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "wrapped-agreement-instrumented-test.bin"
    private val wrappingAlias = "mycodex.remote.device.agreement.wrap.instrumented-test"
    private val store = WrappedAgreementKeyStore(context, fileName, wrappingAlias)

    @Test
    fun encryptedPrivateKeyReloadsAndDerivesTheSameSecret() {
        store.delete()
        try {
            val publicKey = store.create(DEVICE_ID, KEY_VERSION)
            val peer = KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()

            assertTrue(store.exists())
            assertTrue(store.isUsable(DEVICE_ID, KEY_VERSION))
            assertArrayEquals(publicKey, store.loadPublicKey(DEVICE_ID, KEY_VERSION))
            assertArrayEquals(
                SecureRemoteCrypto.deriveAgreement(peer.private, publicKey),
                store.deriveAgreement(
                    DEVICE_ID,
                    KEY_VERSION,
                    SecureRemoteCrypto.encodeP256PublicKey(peer.public),
                ),
            )

            val wrappingKey = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey(wrappingAlias, null)
            assertNull(wrappingKey.encoded)
            assertTrue(File(context.noBackupFilesDir, fileName).isFile)
        } finally {
            store.delete()
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        store.delete()
        try {
            store.create(DEVICE_ID, KEY_VERSION)
            val file = File(context.noBackupFilesDir, fileName)
            RandomAccessFile(file, "rw").use { output ->
                output.seek(output.length() - 1)
                val value = output.readByte().toInt()
                output.seek(output.length() - 1)
                output.writeByte(value xor 1)
            }

            assertFalse(store.isUsable(DEVICE_ID, KEY_VERSION))
        } finally {
            store.delete()
        }
    }

    private companion object {
        const val DEVICE_ID = "11111111-2222-3333-4444-555555555555"
        const val KEY_VERSION = 1
    }
}
