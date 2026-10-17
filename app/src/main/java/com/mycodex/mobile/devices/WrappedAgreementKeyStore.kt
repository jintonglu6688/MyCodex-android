package com.mycodex.mobile.devices

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mycodex.mobile.security.SecureRemoteCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class WrappedAgreementKeyStore(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
    private val wrappingAlias: String = DEFAULT_WRAPPING_ALIAS,
) {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, fileName))

    fun exists(): Boolean = atomicFile.baseFile.isFile

    fun isUsable(deviceId: String, keyVersion: Int): Boolean {
        return runCatching { loadPublicKey(deviceId, keyVersion) }.isSuccess
    }

    fun create(deviceId: String, keyVersion: Int): ByteArray {
        delete()
        val keyPair = KeyPairGenerator.getInstance(EC_ALGORITHM).apply {
            initialize(ECGenParameterSpec(P256_CURVE))
        }.generateKeyPair()
        val publicKey = SecureRemoteCrypto.encodeP256PublicKey(keyPair.public)
        val privateKey = requireNotNull(keyPair.private.encoded) {
            "Generated agreement private key is not exportable"
        }
        var ciphertext: ByteArray? = null
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateWrappingKey())
            cipher.updateAAD(aad(deviceId, keyVersion, publicKey))
            ciphertext = cipher.doFinal(privateKey)
            writeRecord(
                StoredAgreementKey(
                    publicKey = publicKey,
                    nonce = cipher.iv,
                    ciphertext = ciphertext,
                ),
            )
            loadPrivateKey(deviceId, keyVersion).also { loaded ->
                require(loaded.algorithm.equals(EC_ALGORITHM, ignoreCase = true))
            }
            return publicKey.clone()
        } finally {
            privateKey.fill(0)
            ciphertext?.fill(0)
        }
    }

    fun loadPublicKey(deviceId: String, keyVersion: Int): ByteArray {
        val record = readRecord()
        loadPrivateKey(deviceId, keyVersion, record).also { loaded ->
            require(loaded.algorithm.equals(EC_ALGORITHM, ignoreCase = true))
        }
        return record.publicKey.clone()
    }

    fun deriveAgreement(deviceId: String, keyVersion: Int, peerPublicKey: ByteArray): ByteArray {
        require(SecureRemoteCrypto.isValidP256PublicKey(peerPublicKey))
        val privateKey = loadPrivateKey(deviceId, keyVersion)
        return SecureRemoteCrypto.deriveAgreement(privateKey, peerPublicKey)
    }

    fun delete() {
        atomicFile.delete()
        val keyStore = loadAndroidKeyStore()
        if (keyStore.containsAlias(wrappingAlias)) {
            keyStore.deleteEntry(wrappingAlias)
        }
    }

    private fun loadPrivateKey(deviceId: String, keyVersion: Int): PrivateKey {
        return loadPrivateKey(deviceId, keyVersion, readRecord())
    }

    private fun loadPrivateKey(
        deviceId: String,
        keyVersion: Int,
        record: StoredAgreementKey,
    ): PrivateKey {
        require(record.nonce.size == GCM_NONCE_SIZE)
        require(SecureRemoteCrypto.isValidP256PublicKey(record.publicKey))
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            loadWrappingKey(),
            GCMParameterSpec(GCM_TAG_BITS, record.nonce),
        )
        cipher.updateAAD(aad(deviceId, keyVersion, record.publicKey))
        val encoded = cipher.doFinal(record.ciphertext)
        try {
            val privateKey = KeyFactory.getInstance(EC_ALGORITHM)
                .generatePrivate(PKCS8EncodedKeySpec(encoded))
            val ecPrivateKey = privateKey as? ECPrivateKey
                ?: throw IllegalArgumentException("Expected an EC private key")
            require(ecPrivateKey.params.curve.field.fieldSize == P256_FIELD_SIZE)
            return privateKey
        } finally {
            encoded.fill(0)
        }
    }

    private fun loadOrCreateWrappingKey(): SecretKey {
        val existing = loadAndroidKeyStore().getKey(wrappingAlias, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        wrappingAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(AES_KEY_SIZE_BITS)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun loadWrappingKey(): SecretKey {
        return loadAndroidKeyStore().getKey(wrappingAlias, null) as? SecretKey
            ?: throw IllegalStateException("Agreement wrapping key is unavailable")
    }

    private fun loadAndroidKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }

    private fun writeRecord(record: StoredAgreementKey) {
        require(record.publicKey.size == P256_PUBLIC_KEY_SIZE)
        require(record.nonce.size == GCM_NONCE_SIZE)
        require(record.ciphertext.size in 1..MAX_CIPHERTEXT_SIZE)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            DataOutputStream(output).apply {
                writeInt(FILE_MAGIC)
                writeByte(FILE_VERSION)
                writeByte(record.nonce.size)
                writeShort(record.publicKey.size)
                writeInt(record.ciphertext.size)
                write(record.publicKey)
                write(record.nonce)
                write(record.ciphertext)
                flush()
            }
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    private fun readRecord(): StoredAgreementKey {
        val encoded = atomicFile.readFully()
        require(encoded.size in MIN_FILE_SIZE..MAX_FILE_SIZE)
        return DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            require(input.readInt() == FILE_MAGIC)
            require(input.readUnsignedByte() == FILE_VERSION)
            val nonceLength = input.readUnsignedByte()
            val publicKeyLength = input.readUnsignedShort()
            val ciphertextLength = input.readInt()
            require(nonceLength == GCM_NONCE_SIZE)
            require(publicKeyLength == P256_PUBLIC_KEY_SIZE)
            require(ciphertextLength in 1..MAX_CIPHERTEXT_SIZE)
            val expectedRemaining = publicKeyLength + nonceLength + ciphertextLength
            require(input.available() == expectedRemaining)
            val publicKey = ByteArray(publicKeyLength).also(input::readFully)
            val nonce = ByteArray(nonceLength).also(input::readFully)
            val ciphertext = ByteArray(ciphertextLength).also(input::readFully)
            require(input.available() == 0)
            StoredAgreementKey(publicKey, nonce, ciphertext)
        }
    }

    private fun aad(deviceId: String, keyVersion: Int, publicKey: ByteArray): ByteArray {
        val deviceIdBytes = deviceId.toByteArray(Charsets.UTF_8)
        require(deviceIdBytes.size in 1..MAX_DEVICE_ID_SIZE)
        require(keyVersion > 0)
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeByte(FILE_VERSION)
                output.writeByte(deviceIdBytes.size)
                output.write(deviceIdBytes)
                output.writeInt(keyVersion)
                output.writeShort(publicKey.size)
                output.write(publicKey)
            }
        }.toByteArray()
    }

    private data class StoredAgreementKey(
        val publicKey: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    )

    private companion object {
        const val DEFAULT_FILE_NAME = "remote-device-agreement-v1.bin"
        const val DEFAULT_WRAPPING_ALIAS = "mycodex.remote.device.agreement.wrap.v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val EC_ALGORITHM = "EC"
        const val P256_CURVE = "secp256r1"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val FILE_MAGIC = 0x4D434147
        const val FILE_VERSION = 1
        const val GCM_NONCE_SIZE = 12
        const val GCM_TAG_BITS = 128
        const val AES_KEY_SIZE_BITS = 256
        const val P256_FIELD_SIZE = 256
        const val P256_PUBLIC_KEY_SIZE = 65
        const val MAX_DEVICE_ID_SIZE = 64
        const val MAX_CIPHERTEXT_SIZE = 1024
        const val MIN_FILE_SIZE = 4 + 1 + 1 + 2 + 4 + P256_PUBLIC_KEY_SIZE + GCM_NONCE_SIZE + 1
        const val MAX_FILE_SIZE = 4 + 1 + 1 + 2 + 4 + P256_PUBLIC_KEY_SIZE + GCM_NONCE_SIZE + MAX_CIPHERTEXT_SIZE
    }
}
