package io.github.gijungpark.payswitch.domain.payment

/**
 * 가맹점이 최초 승인 요청에 부여한 참조 값. blank 값은 허용하지 않는다.
 */
@JvmInline
value class ClientReference(val value: String) {

    init {
        require(value.isNotBlank()) { "clientReference must not be blank" }
    }

    override fun toString(): String = value
}
