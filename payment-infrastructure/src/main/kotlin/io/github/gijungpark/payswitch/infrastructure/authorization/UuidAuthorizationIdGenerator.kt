package io.github.gijungpark.payswitch.infrastructure.authorization

import io.github.gijungpark.payswitch.application.authorization.AuthorizationIdGenerator
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import java.util.UUID

/**
 * 무작위 UUID로 결제·금융거래 ID를 발급한다. 각 ID는 접두사를 포함해 40자로 `VARCHAR(64)` 안에 들어간다.
 */
internal class UuidAuthorizationIdGenerator : AuthorizationIdGenerator {

    override fun newPaymentId(): PaymentId = PaymentId("PAY-${UUID.randomUUID()}")

    override fun newTransactionId(): TransactionId = TransactionId("TXN-${UUID.randomUUID()}")
}
