package com.komgatv.core.datastore.crypto

/**
 * 자격 증명(비밀번호, API 키 등) 암호화 및 복호화 인터페이스
 */
interface CredentialEncryptor {
    /**
     * 평문을 AES-GCM으로 암호화하여 Base64 문자열로 반환합니다. 실패하면 예외를 던집니다.
     */
    fun encrypt(plainText: String): String

    /**
     * Base64로 인코딩된 AES-GCM 암호문을 평문으로 복호화합니다.
     * 복호화 실패 시 빈 문자열을 반환합니다.
     */
    fun decrypt(cipherText: String): String
}
