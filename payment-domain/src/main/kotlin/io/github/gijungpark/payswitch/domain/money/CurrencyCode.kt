package io.github.gijungpark.payswitch.domain.money

/**
 * ISO 4217 alphabetic 형식의 세 자리 대문자 통화 코드.
 */
@JvmInline
value class CurrencyCode(val value: String) {

    init {
        require(value.length == 3 && value.all { it in 'A'..'Z' }) {
            "currency code must be three uppercase letters: '$value'"
        }
    }

    override fun toString(): String = value

    companion object {
        val KRW = CurrencyCode("KRW")
    }
}
