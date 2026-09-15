package io.github.gijungpark.payswitch.domain.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CurrencyCodeTest {

    @ParameterizedTest
    @ValueSource(strings = ["KRW", "USD"])
    fun acceptsThreeUppercaseLetters(code: String) {
        assertEquals(code, CurrencyCode(code).value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "KR", "KRWW", "krw", "K1W", " KRW"])
    fun rejectsInvalidCode(code: String) {
        assertThrows<IllegalArgumentException> { CurrencyCode(code) }
    }

    @Test
    fun krwConstantIsKrw() {
        assertEquals("KRW", CurrencyCode.KRW.value)
    }
}
