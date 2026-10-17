package com.mycodex.mobile.security

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecureRemoteCrypto {
    fun signRawEcdsa(privateKey: PrivateKey, transcript: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(transcript)
        return derToRawEcdsa(signature.sign())
    }

    fun verifyRawEcdsa(
        publicKey: ByteArray,
        transcript: ByteArray,
        rawSignature: ByteArray,
    ): Boolean {
        if (rawSignature.size != RAW_SIGNATURE_SIZE) {
            return false
        }

        return runCatching {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(decodeP256PublicKey(publicKey))
            verifier.update(transcript)
            verifier.verify(rawToDerEcdsa(rawSignature))
        }.getOrDefault(false)
    }

    fun deriveAgreement(privateKey: PrivateKey, peerPublicKey: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(decodeP256PublicKey(peerPublicKey), true)
        return fixedWidth(agreement.generateSecret(), FIELD_SIZE)
    }

    fun sha256(value: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(value)
    }

    fun hmacSha256(key: ByteArray, value: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(value)
    }

    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length in 0..HKDF_MAX_LENGTH)
        val actualSalt = if (salt.isEmpty()) ByteArray(SHA256_SIZE) else salt
        val pseudorandomKey = hmacSha256(actualSalt, ikm)
        val output = ByteArrayOutputStream(length)
        var previous = byteArrayOf()
        var counter = 1
        while (output.size() < length) {
            previous = hmacSha256(
                pseudorandomKey,
                previous + info + byteArrayOf(counter.toByte()),
            )
            output.write(previous, 0, minOf(previous.size, length - output.size()))
            counter++
        }
        return output.toByteArray()
    }

    fun encryptAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        return aesGcm(Cipher.ENCRYPT_MODE, key, nonce, plaintext, aad)
    }

    fun decryptAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        return aesGcm(Cipher.DECRYPT_MODE, key, nonce, ciphertext, aad)
    }

    fun envelopeNonce(prefix: ByteArray, sequence: ULong): ByteArray {
        require(prefix.size == NONCE_PREFIX_SIZE)
        return ByteBuffer.allocate(GCM_NONCE_SIZE)
            .put(prefix)
            .putLong(sequence.toLong())
            .array()
    }

    fun pairingCode(claimPlaintext: ByteArray): String {
        val digest = sha256(SecureRemoteTranscript.pairingCodeTranscript(claimPlaintext))
        val value = (ByteBuffer.wrap(digest, 0, 4).int.toLong() and 0xffffffffL) % 1_000_000L
        return value.toString().padStart(6, '0')
    }

    fun encodeP256PublicKey(publicKey: PublicKey): ByteArray {
        val ecPublicKey = publicKey as? ECPublicKey
            ?: throw IllegalArgumentException("Expected an EC public key")
        return byteArrayOf(UNCOMPRESSED_POINT) +
            fixedWidth(ecPublicKey.w.affineX.toByteArray(), FIELD_SIZE) +
            fixedWidth(ecPublicKey.w.affineY.toByteArray(), FIELD_SIZE)
    }

    fun isValidP256PublicKey(encoded: ByteArray): Boolean {
        return runCatching { decodeP256PublicKey(encoded) }.isSuccess
    }

    internal fun derToRawEcdsa(derSignature: ByteArray): ByteArray {
        var offset = 0
        require(readByte(derSignature, offset++) == DER_SEQUENCE)
        val sequenceLength = readDerLength(derSignature, offset)
        offset = sequenceLength.nextOffset
        require(sequenceLength.length == derSignature.size - offset)
        require(readByte(derSignature, offset++) == DER_INTEGER)
        val rLength = readDerLength(derSignature, offset)
        offset = rLength.nextOffset
        val r = derSignature.copyOfRange(offset, offset + rLength.length)
        offset += rLength.length
        require(readByte(derSignature, offset++) == DER_INTEGER)
        val sLength = readDerLength(derSignature, offset)
        offset = sLength.nextOffset
        val s = derSignature.copyOfRange(offset, offset + sLength.length)
        offset += sLength.length
        require(offset == derSignature.size)
        return unsignedInteger(r) + unsignedInteger(s)
    }

    private fun rawToDerEcdsa(rawSignature: ByteArray): ByteArray {
        require(rawSignature.size == RAW_SIGNATURE_SIZE)
        val r = derInteger(rawSignature.copyOfRange(0, FIELD_SIZE))
        val s = derInteger(rawSignature.copyOfRange(FIELD_SIZE, RAW_SIGNATURE_SIZE))
        val content = byteArrayOf(DER_INTEGER.toByte(), r.size.toByte()) + r +
            byteArrayOf(DER_INTEGER.toByte(), s.size.toByte()) + s
        return byteArrayOf(DER_SEQUENCE.toByte(), content.size.toByte()) + content
    }

    private fun decodeP256PublicKey(encoded: ByteArray): ECPublicKey {
        require(encoded.size == SEC1_PUBLIC_KEY_SIZE)
        require(encoded[0] == UNCOMPRESSED_POINT)
        val parameters = p256Parameters()
        val x = BigInteger(1, encoded.copyOfRange(1, 1 + FIELD_SIZE))
        val y = BigInteger(1, encoded.copyOfRange(1 + FIELD_SIZE, encoded.size))
        require(isPointOnCurve(x, y, parameters))
        val specification = ECPublicKeySpec(ECPoint(x, y), parameters)
        return KeyFactory.getInstance("EC").generatePublic(specification) as ECPublicKey
    }

    private fun p256Parameters(): ECParameterSpec {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        return parameters.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun isPointOnCurve(x: BigInteger, y: BigInteger, parameters: ECParameterSpec): Boolean {
        val prime = (parameters.curve.field as ECFieldFp).p
        if (x.signum() < 0 || y.signum() < 0 || x >= prime || y >= prime) {
            return false
        }
        val left = y.modPow(BigInteger.valueOf(2), prime)
        val right = x.modPow(BigInteger.valueOf(3), prime)
            .add(parameters.curve.a.multiply(x))
            .add(parameters.curve.b)
            .mod(prime)
        return left == right
    }

    private fun derInteger(unsigned: ByteArray): ByteArray {
        val firstNonZero = unsigned.indexOfFirst { it.toInt() != 0 }
        val magnitude = if (firstNonZero < 0) {
            byteArrayOf(0)
        } else {
            unsigned.copyOfRange(firstNonZero, unsigned.size)
        }
        return if ((magnitude[0].toInt() and 0x80) != 0) byteArrayOf(0) + magnitude else magnitude
    }

    private fun unsignedInteger(encoded: ByteArray): ByteArray {
        require(encoded.isNotEmpty())
        require(encoded.size <= FIELD_SIZE + 1)
        require((encoded[0].toInt() and 0x80) == 0)
        val magnitude = if (encoded.size > 1 && encoded[0] == 0.toByte()) {
            require((encoded[1].toInt() and 0x80) != 0)
            encoded.copyOfRange(1, encoded.size)
        } else {
            encoded
        }
        require(magnitude.size <= FIELD_SIZE)
        val value = BigInteger(1, magnitude)
        require(value.signum() > 0)
        require(value < P256_ORDER)
        return ByteArray(FIELD_SIZE - magnitude.size) + magnitude
    }

    private fun fixedWidth(value: ByteArray, width: Int): ByteArray {
        var first = 0
        while (first < value.lastIndex && value[first] == 0.toByte()) {
            first++
        }
        val magnitude = value.copyOfRange(first, value.size)
        require(magnitude.size <= width)
        return ByteArray(width - magnitude.size) + magnitude
    }

    private fun aesGcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(key.size == AES256_KEY_SIZE)
        require(nonce.size == GCM_NONCE_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }

    private fun readByte(bytes: ByteArray, offset: Int): Int {
        require(offset in bytes.indices)
        return bytes[offset].toInt() and 0xff
    }

    private fun readDerLength(bytes: ByteArray, offset: Int): DerLength {
        val first = readByte(bytes, offset)
        if ((first and 0x80) == 0) {
            return DerLength(first, offset + 1)
        }
        val byteCount = first and 0x7f
        require(byteCount in 1..2)
        var length = 0
        repeat(byteCount) { index ->
            length = (length shl 8) or readByte(bytes, offset + 1 + index)
        }
        require(length >= 128)
        return DerLength(length, offset + 1 + byteCount)
    }

    private data class DerLength(val length: Int, val nextOffset: Int)

    private const val FIELD_SIZE = 32
    private const val SHA256_SIZE = 32
    private const val HKDF_MAX_LENGTH = 255 * SHA256_SIZE
    private const val AES256_KEY_SIZE = 32
    private const val NONCE_PREFIX_SIZE = 4
    private const val GCM_NONCE_SIZE = 12
    private const val GCM_TAG_BITS = 128
    private const val RAW_SIGNATURE_SIZE = FIELD_SIZE * 2
    private const val SEC1_PUBLIC_KEY_SIZE = 1 + FIELD_SIZE * 2
    private const val UNCOMPRESSED_POINT: Byte = 0x04
    private const val DER_SEQUENCE = 0x30
    private const val DER_INTEGER = 0x02
    private val P256_ORDER = BigInteger(
        "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
        16,
    )
}
