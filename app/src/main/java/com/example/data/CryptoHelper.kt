package com.example.data

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val ITERATIONS = 1000
    private const val KEY_LENGTH = 256
    
    private val SALT = byteArrayOf(
        0x45, 0x6e, 0x63, 0x72, 0x79, 0x70, 0x74, 0x65,
        0x64, 0x4a, 0x6f, 0x75, 0x72, 0x6e, 0x61, 0x6c,
        0x53, 0x65, 0x63, 0x72, 0x65, 0x74, 0x53, 0x61, 0x6c, 0x74
    )

    private fun deriveKey(password: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun generateRandomKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun encrypt(plainText: String, password: String): EncryptedData {
        if (plainText.isEmpty()) return EncryptedData("", "")
        val key = deriveKey(password)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherTextBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedData(
            cipherText = Base64.encodeToString(cipherTextBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(cipherText: String, iv: String, password: String): String {
        if (cipherText.isEmpty() || iv.isEmpty()) return ""
        return try {
            val key = deriveKey(password)
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
            val cipherTextBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, ivBytes))
            val decryptedBytes = cipher.doFinal(cipherTextBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            "⚠️ Mật khẩu giải mã không chính xác hoặc dữ liệu bị lỗi"
        }
    }

    fun encryptBytes(dataBytes: ByteArray, password: String): EncryptedBytesData {
        val key = deriveKey(password)
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherTextBytes = cipher.doFinal(dataBytes)
        return EncryptedBytesData(cipherTextBytes, iv)
    }

    fun decryptBytes(cipherBytes: ByteArray, iv: ByteArray, password: String): ByteArray {
        val key = deriveKey(password)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        return cipher.doFinal(cipherBytes)
    }

    // High level file encryption: returns iv prepended to ciphertext
    fun encryptFileBytes(inputBytes: ByteArray, password: String): ByteArray {
        val encryptedData = encryptBytes(inputBytes, password)
        val resultBytes = ByteArray(IV_LENGTH_BYTE + encryptedData.cipherBytes.size)
        System.arraycopy(encryptedData.iv, 0, resultBytes, 0, IV_LENGTH_BYTE)
        System.arraycopy(encryptedData.cipherBytes, 0, resultBytes, IV_LENGTH_BYTE, encryptedData.cipherBytes.size)
        return resultBytes
    }

    // High level file decryption: extracts iv and decrypts
    fun decryptFileBytes(fileBytes: ByteArray, password: String): ByteArray {
        if (fileBytes.size <= IV_LENGTH_BYTE) return byteArrayOf()
        val iv = ByteArray(IV_LENGTH_BYTE)
        System.arraycopy(fileBytes, 0, iv, 0, IV_LENGTH_BYTE)
        val cipherBytes = ByteArray(fileBytes.size - IV_LENGTH_BYTE)
        System.arraycopy(fileBytes, IV_LENGTH_BYTE, cipherBytes, 0, cipherBytes.size)
        return decryptBytes(cipherBytes, iv, password)
    }
}

data class EncryptedData(
    val cipherText: String,
    val iv: String
)

data class EncryptedBytesData(
    val cipherBytes: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBytesData) return false
        if (!cipherBytes.contentEquals(other.cipherBytes)) return false
        if (!iv.contentEquals(other.iv)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = cipherBytes.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
