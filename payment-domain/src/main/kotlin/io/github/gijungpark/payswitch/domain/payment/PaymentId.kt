package io.github.gijungpark.payswitch.domain.payment

/**
 * 결제 식별자. blank 값은 허용하지 않는다.
 */
@JvmInline
value class PaymentId(val value: String) {

    init {
        require(value.isNotBlank()) { "paymentId must not be blank" }
    }

    override fun toString(): String = value
}
