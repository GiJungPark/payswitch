package io.github.gijungpark.payswitch.domain.transaction

/**
 * 금융거래 식별자. blank 값은 허용하지 않는다.
 */
@JvmInline
value class TransactionId(val value: String) {

    init {
        require(value.isNotBlank()) { "transactionId must not be blank" }
    }

    override fun toString(): String = value
}
