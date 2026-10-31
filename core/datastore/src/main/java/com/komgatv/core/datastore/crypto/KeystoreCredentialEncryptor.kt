package com.komgatv.core.datastore.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.NoSuchProviderException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore 기반 AES-GCM 자격 증명 암호화기
 *
 * AndroidKeyStore 프로바이더를 사용하여 256비트 AES 키를 안전하게 보관하며,
 * 기기 환경이 아닌 JVM 단위 테스트 등 AndroidKeyStore를 사용할 수 없는 경우
 * 동일한 AES-GCM 명세의 표준 키로 안전하게 폴백합니다.
 */
@Singleton
class KeystoreCredentialEncryptor @Inject constructor() : CredentialEncryptor {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "komgatv_credential_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private var fallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey ?: generateAndroidKeyStoreKey()
            } else {
                generateAndroidKeyStoreKey()
            }
        } catch (_: NoSuchProviderException) {
            getOrCreateFallbackKey()
        } catch (_: Exception) {
            getOrCreateFallbackKey()
        }
    }

    private fun generateAndroidKeyStoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    private fun getOrCreateFallbackKey(): SecretKey {
        fallbackKey?.let { return it }
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key = keyGenerator.generateKey()
        fallbackKey = key
        return key
    }

    override fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv // 12 bytes
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // IV (12 바이트) + 암호문(태그 포함) 결합
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            ""
        }
    }

    override fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""

        return try {
            val combined = Base64.getDecoder().decode(cipherText)
            if (combined.size <= IV_LENGTH_BYTES) return ""

            val iv = ByteArray(IV_LENGTH_BYTES)
            val encryptedSize = combined.size - IV_LENGTH_BYTES
            val encryptedBytes = ByteArray(encryptedSize)

            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)
            System.arraycopy(combined, IV_LENGTH_BYTES, encryptedBytes, 0, encryptedSize)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
