package io.github.gijungpark.payswitch.application.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IdempotencyKeyTest {

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n", "　"])
    fun rejectsBlankKey(value: String) {
        assertThrows<IllegalArgumentException> { IdempotencyKey(value) }
    }

    @Test
    fun acceptsKeysUpToMaxLengthInCodePoints() {
        assertEquals(128, IdempotencyKey.MAX_LENGTH)
        IdempotencyKey("k")
        IdempotencyKey("a".repeat(128))
        IdempotencyKey("가".repeat(128))
        // U+1F600은 UTF-16 2 char지만 MySQL VARCHAR 문자 수와 같이 1자로 센다.
        IdempotencyKey("😀".repeat(128))
    }

    @Test
    fun rejectsKeysLongerThanMaxLength() {
        assertThrows<IllegalArgumentException> { IdempotencyKey("a".repeat(129)) }
        assertThrows<IllegalArgumentException> { IdempotencyKey("😀".repeat(129)) }
    }

    @Test
    fun keepsCaseAndSurroundingSpaces() {
        assertNotEquals(IdempotencyKey("KEY-1"), IdempotencyKey("key-1"))
        assertNotEquals(IdempotencyKey("KEY-1"), IdempotencyKey("KEY-1 "))
        assertEquals(" KEY-1 ", IdempotencyKey(" KEY-1 ").value)
    }
}

class RequestHashTest {

    @Test
    fun acceptsOnlyLowercaseSha256Hex() {
        RequestHash("0".repeat(64))
        RequestHash("0123456789abcdef".repeat(4))
        assertThrows<IllegalArgumentException> { RequestHash("0".repeat(63)) }
        assertThrows<IllegalArgumentException> { RequestHash("0".repeat(65)) }
        assertThrows<IllegalArgumentException> { RequestHash("A".repeat(64)) }
        assertThrows<IllegalArgumentException> { RequestHash("g".repeat(64)) }
    }

    @Test
    fun computesSha256OfBytes() {
        assertEquals(
            RequestHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            RequestHash.sha256Of(ByteArray(0)),
        )
        assertEquals(
            RequestHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            RequestHash.sha256Of("abc".toByteArray(Charsets.US_ASCII)),
        )
    }
}
