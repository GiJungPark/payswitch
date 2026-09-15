package io.github.gijungpark.payswitch.domain.transaction

import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.PaymentId

/**
 * 기관에 전달되는 개별 금융거래의 독립된 상태.
 *
 * 결제는 [paymentId]로만 참조하며 결제를 변경하지 않는다. 같은 target 상태의 반복 적용은 no-op이고,
 * 허용되지 않은 전이와 terminal 상태의 상충 결과는 상태·version을 바꾸지 않고 [IllegalStateException]을 던진다.
 * 상태가 실제로 바뀔 때마다 [version]이 1 증가한다.
 */
class FinancialTransaction private constructor(
    val transactionId: TransactionId,
    val paymentId: PaymentId,
    val type: FinancialTransactionType,
    val originalTransactionId: TransactionId?,
    val amount: Money,
) {

    var status: FinancialTransactionStatus = FinancialTransactionStatus.RECEIVED
        private set

    var version: Long = 0
        private set

    fun startProcessing() = transitionTo(FinancialTransactionStatus.PROCESSING)

    fun succeed() = transitionTo(FinancialTransactionStatus.SUCCEEDED)

    fun decline() = transitionTo(FinancialTransactionStatus.DECLINED)

    fun fail() = transitionTo(FinancialTransactionStatus.FAILED)

    fun markUnknown() = transitionTo(FinancialTransactionStatus.UNKNOWN)

    fun requireManualReview() = transitionTo(FinancialTransactionStatus.MANUAL_REVIEW_REQUIRED)

    private fun transitionTo(target: FinancialTransactionStatus) {
        if (status == target) {
            return
        }
        check(status.canTransitionTo(target)) {
            "transaction $transactionId cannot transition from $status to $target"
        }
        status = target
        version++
    }

    companion object {
        /** 1원 이상 KRW 승인 거래를 `RECEIVED`, version 0으로 생성한다. */
        fun createAuthorization(
            transactionId: TransactionId,
            paymentId: PaymentId,
            amount: Money,
        ): FinancialTransaction {
            require(amount.currency == CurrencyCode.KRW) {
                "authorization currency must be KRW: ${amount.currency}"
            }
            require(amount.amountMinor > 0) { "authorization amount must be positive: ${amount.amountMinor}" }
            return FinancialTransaction(
                transactionId = transactionId,
                paymentId = paymentId,
                type = FinancialTransactionType.AUTHORIZE,
                originalTransactionId = null,
                amount = amount,
            )
        }
    }
}
