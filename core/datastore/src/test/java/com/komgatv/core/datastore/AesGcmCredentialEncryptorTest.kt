package com.komgatv.core.datastore

import com.komgatv.core.datastore.crypto.AesGcmCredentialEncryptor
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AesGcmCredentialEncryptorTest {

    private lateinit var encryptor: AesGcmCredentialEncryptor

    @Before
    fun setUp() {
        // Android Keystore는 JVM에 없으므로 같은 AES-GCM 경로를 일반 AES 키로 검증한다.
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        encryptor = AesGcmCredentialEncryptor { key }
    }

    @Test
    fun encrypt_and_decrypt_returns_original_text() {
        val original = "mySecretPassword123!@#"
        val encrypted = encryptor.encrypt(original)

        assertNotEquals(original, encrypted)
        assertTrue(encrypted.isNotBlank())

        val decrypted = encryptor.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encrypt_and_decrypt_korean_and_special_characters() {
        val original = "한글비밀번호-키값_@#123!?"
        val encrypted = encryptor.encrypt(original)

        assertNotEquals(original, encrypted)
        val decrypted = encryptor.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encrypt_empty_string_returns_empty() {
        val encrypted = encryptor.encrypt("")
        assertEquals("", encrypted)
    }

    @Test
    fun decrypt_with_different_key_returns_empty() {
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encrypted = AesGcmCredentialEncryptor { otherKey }.encrypt("secret")
        assertEquals("", encryptor.decrypt(encrypted))
    }

    @Test
    fun decrypt_empty_or_corrupt_string_returns_empty() {
        assertEquals("", encryptor.decrypt(""))
        assertEquals("", encryptor.decrypt("invalid-base64-string!@#"))
        assertEquals("", encryptor.decrypt("dGVzdA==")) // 너무 짧은 암호문 (IV 바이트 미달)
    }
}
