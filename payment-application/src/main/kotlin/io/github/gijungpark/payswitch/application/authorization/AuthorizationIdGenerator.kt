package io.github.gijungpark.payswitch.application.authorization

import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.TransactionId

/**
 * 새 승인 접수에 사용할 식별자를 발급하는 port. 테스트에서 결정적인 값을 주입할 수 있다.
 */
interface AuthorizationIdGenerator {

    fun newPaymentId(): PaymentId

    fun newTransactionId(): TransactionId
}
