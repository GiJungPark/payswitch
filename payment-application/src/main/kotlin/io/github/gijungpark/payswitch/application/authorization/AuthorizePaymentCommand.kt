package io.github.gijungpark.payswitch.application.authorization

import io.github.gijungpark.payswitch.application.idempotency.IdempotencyKey
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId

/**
 * 승인 접수 요청.
 *
 * [amount]는 1 이상이어야 한다. 통화 지원 여부는 기존 요청과의 충돌 판정 뒤에 use case가 검사한다.
 */
data class AuthorizePaymentCommand(
    val merchantId: MerchantId,
    val clientReference: ClientReference,
    val idempotencyKey: IdempotencyKey,
    val amount: Money,
) {

    init {
        require(amount.amountMinor > 0) { "authorization amount must be positive: ${amount.amountMinor}" }
    }
}
