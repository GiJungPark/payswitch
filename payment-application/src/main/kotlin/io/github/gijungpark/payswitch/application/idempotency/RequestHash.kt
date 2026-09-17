package io.github.gijungpark.payswitch.application.idempotency

import java.security.MessageDigest

/**
 * 요청 canonical representation의 SHA-256 digest를 소문자 16진수 64자로 표현한 값.
 */
@JvmInline
value class RequestHash(val value: String) {

    init {
        require(value.length == HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }) {
            "requestHash must be $HEX_LENGTH lowercase hexadecimal characters: '$value'"
        }
    }

    override fun toString(): String = value

    companion object {
        private const val HEX_LENGTH = 64
        private const val HEX_DIGITS = "0123456789abcdef"

        /** [canonicalBytes]의 SHA-256 digest를 계산한다. */
        fun sha256Of(canonicalBytes: ByteArray): RequestHash {
            val digest = MessageDigest.getInstance("SHA-256").digest(canonicalBytes)
            val hex = buildString(HEX_LENGTH) {
                digest.forEach { byte ->
                    val unsigned = byte.toInt() and 0xFF
                    append(HEX_DIGITS[unsigned ushr 4])
                    append(HEX_DIGITS[unsigned and 0x0F])
                }
            }
            return RequestHash(hex)
        }
    }
}
