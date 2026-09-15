package io.github.gijungpark.payswitch.domain.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MoneyTest {

    @ParameterizedTest
    @ValueSource(longs = [0, 1, Long.MAX_VALUE])
    fun holdsNonNegativeAmountWithCurrency(amountMinor: Long) {
        val money = Money(amountMinor, CurrencyCode.KRW)

        assertEquals(amountMinor, money.amountMinor)
        assertEquals(CurrencyCode.KRW, money.currency)
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, Long.MIN_VALUE])
    fun rejectsNegativeAmount(amountMinor: Long) {
        assertThrows<IllegalArgumentException> { Money(amountMinor, CurrencyCode.KRW) }
    }

    @Test
    fun zeroKeepsCurrency() {
        val zero = Money.zero(CurrencyCode("USD"))

        assertEquals(0L, zero.amountMinor)
        assertEquals(CurrencyCode("USD"), zero.currency)
    }

    @Test
    fun equalityRequiresSameAmountAndCurrency() {
        assertEquals(Money(1_000, CurrencyCode.KRW), Money(1_000, CurrencyCode.KRW))
        assertNotEquals(Money(1_000, CurrencyCode.KRW), Money(999, CurrencyCode.KRW))
        assertNotEquals(Money(1_000, CurrencyCode.KRW), Money(1_000, CurrencyCode("USD")))
    }

    @Test
    fun exposesNoPublicSetters() {
        assertEquals(emptyList<String>(), Money::class.java.methods.map { it.name }.filter { it.startsWith("set") })
    }
}
