package com.mycodex.mobile.devices

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.mycodex.mobile.security.SecureRemoteCrypto
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID

data class LocalDeviceIdentity(
    val deviceId: String,
    val displayName: String,
    val keyVersion: Int,
    val signingPublicKey: String,
    val agreementPublicKey: String,
) {
    val publicKey: String
        get() = signingPublicKey
}

internal enum class AgreementKeyBackend {
    NativeKeystore,
    Wrapped,
}

internal fun selectExistingAgreementKeyBackend(
    apiLevel: Int,
    wrappedExists: Boolean,
    nativeExists: Boolean,
): AgreementKeyBackend? {
    return when {
        wrappedExists -> AgreementKeyBackend.Wrapped
        apiLevel >= 31 && nativeExists -> AgreementKeyBackend.NativeKeystore
        else -> null
    }
}

internal fun selectNewAgreementKeyBackend(apiLevel: Int): AgreementKeyBackend {
    return if (apiLevel >= 31) {
        AgreementKeyBackend.NativeKeystore
    } else {
        AgreementKeyBackend.Wrapped
    }
}

internal class DeviceIdentityException(
    val code: String,
    cause: Throwable? = null,
) : IllegalStateException(code, cause) {
    companion object {
        const val SECURITY_UNAVAILABLE = "device_security_unavailable"
    }
}

class LocalDeviceIdentityStore(context: Context) {
    private val contentResolver = context.contentResolver
    private val preferences = context.getSharedPreferences("local_device_identity", Context.MODE_PRIVATE)
    private val wrappedAgreementKeys = WrappedAgreementKeyStore(context)

    fun load(): LocalDeviceIdentity {
        return try {
            synchronized(IDENTITY_LOCK) {
                loadOrCreate()
            }
        } catch (error: DeviceIdentityException) {
            throw error
        } catch (error: Exception) {
            throw DeviceIdentityException(DeviceIdentityException.SECURITY_UNAVAILABLE, error)
        }
    }

    private fun loadOrCreate(): LocalDeviceIdentity {
        val keyStore = loadKeyStore()
        val existingDeviceId = preferences.getString(KEY_DEVICE_ID, null)
            ?.takeIf { DEVICE_ID_PATTERN.matches(it) }
        val existingBackend = selectExistingAgreementKeyBackend(
            apiLevel = Build.VERSION.SDK_INT,
            wrappedExists = wrappedAgreementKeys.exists(),
            nativeExists = keyStore.containsAlias(AGREEMENT_ALIAS),
        )
        val identityIsComplete = existingDeviceId != null &&
            preferences.getInt(KEY_KEY_VERSION, 0) == CURRENT_KEY_VERSION &&
            keyStore.containsAlias(SIGNING_ALIAS) &&
            existingBackend != null &&
            agreementKeyIsUsable(keyStore, existingBackend, existingDeviceId)
        val deviceId: String
        val backend: AgreementKeyBackend
        if (identityIsComplete) {
            deviceId = checkNotNull(existingDeviceId)
            backend = checkNotNull(existingBackend)
        } else {
            resetIdentity(keyStore)
            deviceId = UUID.randomUUID().toString()
            backend = selectNewAgreementKeyBackend(Build.VERSION.SDK_INT)
            try {
                ensureSigningKey(keyStore)
                createAgreementKey(keyStore, backend, deviceId)
                val committed = preferences.edit()
                    .putString(KEY_DEVICE_ID, deviceId)
                    .putInt(KEY_KEY_VERSION, CURRENT_KEY_VERSION)
                    .remove(LEGACY_KEY_PUBLIC_KEY)
                    .commit()
                check(committed) { "Unable to persist the local device identity" }
            } catch (error: Exception) {
                runCatching { resetIdentity(keyStore) }
                throw error
            }
        }

        return LocalDeviceIdentity(
            deviceId = deviceId,
            displayName = buildDisplayName(),
            keyVersion = CURRENT_KEY_VERSION,
            signingPublicKey = encodePublicKey(keyStore, SIGNING_ALIAS),
            agreementPublicKey = encodeAgreementPublicKey(keyStore, backend, deviceId),
        )
    }

    fun sign(transcript: ByteArray): ByteArray {
        return try {
            synchronized(IDENTITY_LOCK) {
                val entry = loadKeyStore().getEntry(SIGNING_ALIAS, null) as? KeyStore.PrivateKeyEntry
                    ?: throw IllegalStateException("Secure remote signing key is unavailable")
                SecureRemoteCrypto.signRawEcdsa(entry.privateKey, transcript)
            }
        } catch (error: Exception) {
            throw DeviceIdentityException(DeviceIdentityException.SECURITY_UNAVAILABLE, error)
        }
    }

    fun deriveAgreement(peerPublicKey: ByteArray): ByteArray {
        return try {
            synchronized(IDENTITY_LOCK) {
                val identity = loadOrCreate()
                if (wrappedAgreementKeys.exists()) {
                    wrappedAgreementKeys.deriveAgreement(
                        identity.deviceId,
                        identity.keyVersion,
                        peerPublicKey,
                    )
                } else {
                    val entry = loadKeyStore().getEntry(AGREEMENT_ALIAS, null)
                        as? KeyStore.PrivateKeyEntry
                        ?: throw IllegalStateException("Secure remote agreement key is unavailable")
                    SecureRemoteCrypto.deriveAgreement(entry.privateKey, peerPublicKey)
                }
            }
        } catch (error: DeviceIdentityException) {
            throw error
        } catch (error: Exception) {
            throw DeviceIdentityException(DeviceIdentityException.SECURITY_UNAVAILABLE, error)
        }
    }

    private fun agreementKeyIsUsable(
        keyStore: KeyStore,
        backend: AgreementKeyBackend,
        deviceId: String,
    ): Boolean {
        return when (backend) {
            AgreementKeyBackend.NativeKeystore -> runCatching {
                encodePublicKey(keyStore, AGREEMENT_ALIAS)
            }.isSuccess
            AgreementKeyBackend.Wrapped -> wrappedAgreementKeys.isUsable(
                deviceId,
                CURRENT_KEY_VERSION,
            )
        }
    }

    private fun createAgreementKey(
        keyStore: KeyStore,
        backend: AgreementKeyBackend,
        deviceId: String,
    ) {
        when (backend) {
            AgreementKeyBackend.NativeKeystore -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    throw IllegalStateException("Native agreement keys require Android 12")
                }
                ensureNativeAgreementKey(keyStore)
            }
            AgreementKeyBackend.Wrapped -> wrappedAgreementKeys.create(deviceId, CURRENT_KEY_VERSION)
        }
    }

    private fun encodeAgreementPublicKey(
        keyStore: KeyStore,
        backend: AgreementKeyBackend,
        deviceId: String,
    ): String {
        return when (backend) {
            AgreementKeyBackend.NativeKeystore -> encodePublicKey(keyStore, AGREEMENT_ALIAS)
            AgreementKeyBackend.Wrapped -> Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    wrappedAgreementKeys.loadPublicKey(deviceId, CURRENT_KEY_VERSION),
                )
        }
    }

    private fun resetIdentity(keyStore: KeyStore) {
        keyStore.deleteEntry(SIGNING_ALIAS)
        keyStore.deleteEntry(AGREEMENT_ALIAS)
        wrappedAgreementKeys.delete()
        preferences.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_KEY_VERSION)
            .remove(LEGACY_KEY_PUBLIC_KEY)
            .commit()
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }

    private fun ensureSigningKey(keyStore: KeyStore) {
        if (keyStore.containsAlias(SIGNING_ALIAS)) {
            return
        }
        generateKey(
            alias = SIGNING_ALIAS,
            purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            includeSha256Digest = true,
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ensureNativeAgreementKey(keyStore: KeyStore) {
        if (keyStore.containsAlias(AGREEMENT_ALIAS)) {
            return
        }
        generateKey(
            alias = AGREEMENT_ALIAS,
            purposes = KeyProperties.PURPOSE_AGREE_KEY,
            includeSha256Digest = false,
        )
    }

    private fun generateKey(alias: String, purposes: Int, includeSha256Digest: Boolean) {
        val builder = KeyGenParameterSpec.Builder(alias, purposes)
            .setAlgorithmParameterSpec(ECGenParameterSpec(P256_CURVE))
        if (includeSha256Digest) {
            builder.setDigests(KeyProperties.DIGEST_SHA256)
        }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
            .apply { initialize(builder.build()) }
            .generateKeyPair()
    }

    private fun encodePublicKey(keyStore: KeyStore, alias: String): String {
        val publicKey = keyStore.getCertificate(alias)?.publicKey as? ECPublicKey
            ?: throw IllegalStateException("Secure remote public key is unavailable")
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(SecureRemoteCrypto.encodeP256PublicKey(publicKey))
    }

    private fun buildDisplayName(): String {
        val configuredName = Settings.Global.getString(
            contentResolver,
            Settings.Global.DEVICE_NAME,
        )
        return selectDeviceDisplayName(configuredName, Build.MANUFACTURER, Build.MODEL)
    }

    private companion object {
        val IDENTITY_LOCK = Any()
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val P256_CURVE = "secp256r1"
        const val SIGNING_ALIAS = "mycodex.remote.device.signing.v1"
        const val AGREEMENT_ALIAS = "mycodex.remote.device.agreement.v1"
        const val CURRENT_KEY_VERSION = 1
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_KEY_VERSION = "key_version"
        const val LEGACY_KEY_PUBLIC_KEY = "public_key"
        val DEVICE_ID_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )
    }
}

internal fun selectDeviceDisplayName(
    configuredName: String?,
    manufacturer: String?,
    model: String?,
): String {
    configuredName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val normalizedManufacturer = manufacturer?.trim().orEmpty()
    val normalizedModel = model?.trim().orEmpty()
    val hardwareName = when {
        normalizedModel.isEmpty() -> normalizedManufacturer
        normalizedManufacturer.isEmpty() ||
            normalizedModel.startsWith(normalizedManufacturer, ignoreCase = true) -> normalizedModel
        else -> "$normalizedManufacturer $normalizedModel"
    }
    return if (hardwareName.isEmpty()) "Android" else "Android $hardwareName"
}
