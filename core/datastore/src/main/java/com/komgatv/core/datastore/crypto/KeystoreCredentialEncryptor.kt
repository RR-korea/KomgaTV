package com.komgatv.core.datastore.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-GCM 자격 증명 암호화 (저장 형식: Base64(IV 12바이트 + 암호문·태그))
 *
 * 키는 [keyProvider]가 준다. 운영은 Android Keystore 키([KeystoreCredentialEncryptor]),
 * 단위 테스트는 일반 AES 키를 넘긴다.
 */
class AesGcmCredentialEncryptor(
    private val keyProvider: () -> SecretKey,
) : CredentialEncryptor {

    /** 암호화 실패는 예외로 올린다 — 빈 값을 저장해 로그인이 조용히 풀리는 일을 막는다. */
    override fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    override fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return runCatching {
            val combined = Base64.getDecoder().decode(cipherText)
            require(combined.size > IV_LENGTH_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = GCMParameterSpec(GCM_TAG_LENGTH_BITS, combined, 0, IV_LENGTH_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), iv)
            String(cipher.doFinal(combined, IV_LENGTH_BYTES, combined.size - IV_LENGTH_BYTES), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
    }
}

/**
 * Android Keystore 키로 암호화한다 (LEAD_REVIEW D6).
 *
 * 키는 기기 밖으로 나가지 않고, 앱을 지우면 함께 사라진다.
 */
@Singleton
class KeystoreCredentialEncryptor @Inject constructor() :
    CredentialEncryptor by AesGcmCredentialEncryptor(::keystoreKey)

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "komgatv_credential_key"

private fun keystoreKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

    val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        .apply { init(spec) }
        .generateKey()
}
