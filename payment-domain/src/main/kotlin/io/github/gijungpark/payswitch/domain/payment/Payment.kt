package io.github.gijungpark.payswitch.domain.payment

import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money

/**
 * 가맹점 주문에 대응하는 결제 전체의 요약 상태와 금액 합계.
 *
 * 금융거래를 직접 참조하거나 변경하지 않는다. 같은 target 상태의 반복 적용은 no-op이고,
 * 허용되지 않은 전이와 상충 결과는 상태·금액·version을 바꾸지 않고 [IllegalStateException]을 던진다.
 * 상태가 실제로 바뀔 때마다 [version]이 1 증가한다.
 */
class Payment private constructor(
    val paymentId: PaymentId,
    val merchantId: MerchantId,
    val clientReference: ClientReference,
    val transactionCurrency: CurrencyCode,
) {

    /** 승인 성공 전에는 거래 통화의 0이다. */
    var approvedAmount: Money = Money.zero(transactionCurrency)
        private set

    val cancelledAmount: Money = Money.zero(transactionCurrency)

    val reservedCancelAmount: Money = Money.zero(transactionCurrency)

    var status: PaymentStatus = PaymentStatus.CREATED
        private set

    var version: Long = 0
        private set

    /**
     * 승인 성공을 반영하고 [approvedAmount]를 승인 금액으로 설정한다.
     *
     * 이미 같은 금액으로 승인됐다면 no-op이고, 다른 금액이면 상충 결과로 거절한다.
     */
    fun approve(amount: Money) {
        require(amount.currency == transactionCurrency) {
            "approved amount currency ${amount.currency} must match payment currency $transactionCurrency"
        }
        require(amount.amountMinor > 0) { "approved amount must be positive: ${amount.amountMinor}" }

        if (status == PaymentStatus.APPROVED) {
            check(approvedAmount == amount) {
                "payment $paymentId is already approved with a different amount"
            }
            return
        }
        changeStatus(PaymentStatus.APPROVED)
        approvedAmount = amount
    }

    fun decline() = transitionTo(PaymentStatus.DECLINED)

    fun fail() = transitionTo(PaymentStatus.FAILED)

    fun requireResolution() = transitionTo(PaymentStatus.RESOLUTION_REQUIRED)

    fun requireManualReview() = transitionTo(PaymentStatus.MANUAL_REVIEW_REQUIRED)

    private fun transitionTo(target: PaymentStatus) {
        if (status == target) {
            return
        }
        changeStatus(target)
    }

    private fun changeStatus(target: PaymentStatus) {
        check(status.canTransitionTo(target)) {
            "payment $paymentId cannot transition from $status to $target"
        }
        status = target
        version++
    }

    companion object {
        /** KRW 승인 요청을 위한 결제를 `CREATED`, 0원 금액, version 0으로 생성한다. */
        fun createKrwAuthorization(
            paymentId: PaymentId,
            merchantId: MerchantId,
            clientReference: ClientReference,
        ): Payment = Payment(paymentId, merchantId, clientReference, CurrencyCode.KRW)
    }
}
