package io.github.gijungpark.payswitch.domain.money

/**
 * 통화의 최소 단위 정수 금액과 통화를 항상 함께 보유하는 금액.
 *
 * 음수 금액은 허용하지 않는다. 금액 연산은 제공하지 않는다.
 */
data class Money(
    val amountMinor: Long,
    val currency: CurrencyCode,
) {

    init {
        require(amountMinor >= 0) { "amountMinor must not be negative: $amountMinor" }
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0, currency)
    }
}
