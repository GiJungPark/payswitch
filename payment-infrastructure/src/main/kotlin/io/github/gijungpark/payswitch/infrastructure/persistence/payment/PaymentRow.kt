package io.github.gijungpark.payswitch.infrastructure.persistence.payment

import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.payment.Payment
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.payment.PaymentStatus
import io.github.gijungpark.payswitch.infrastructure.persistence.restorePersistedState

/**
 * `payment` table row. 금액은 [transactionCurrency]의 최소 단위 정수이고 enum은 이름으로 저장한다.
 */
internal data class PaymentRow(
    val paymentId: String,
    val merchantId: String,
    val clientReference: String,
    val approvedAmountMinor: Long,
    val cancelledAmountMinor: Long,
    val reservedCancelAmountMinor: Long,
    val transactionCurrency: String,
    val status: String,
    val version: Long,
) {

    /**
     * 공개 생성 factory와 상태 전이 method를 재생해 [Payment]를 복원한다.
     *
     * `(status, version)`이 도달 가능한 전이 경로가 아니거나 재생 결과가 row와 하나라도 다르면
     * [io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException]을 던진다.
     */
    fun toDomain(): Payment = restorePersistedState(TABLE, paymentId) {
        val paymentStatus = PaymentStatus.valueOf(status)
        val path = REPLAY_PATHS[paymentStatus to version]
            ?: throw IllegalStateException("status $status is not reachable at version $version")

        val payment = Payment.createKrwAuthorization(
            PaymentId(paymentId),
            MerchantId(merchantId),
            ClientReference(clientReference),
        )
        path.forEach { target -> replay(payment, target) }

        check(from(payment) == this) { "row does not match the replayed domain state" }
        payment
    }

    private fun replay(payment: Payment, target: PaymentStatus) {
        when (target) {
            PaymentStatus.APPROVED -> payment.approve(Money(approvedAmountMinor, CurrencyCode(transactionCurrency)))
            PaymentStatus.DECLINED -> payment.decline()
            PaymentStatus.FAILED -> payment.fail()
            PaymentStatus.RESOLUTION_REQUIRED -> payment.requireResolution()
            PaymentStatus.MANUAL_REVIEW_REQUIRED -> payment.requireManualReview()
            PaymentStatus.CREATED -> error("CREATED is not a transition target")
        }
    }

    companion object {
        private const val TABLE = "payment"

        /** domain-model.md의 구현된 Payment 전이에서 `(상태, version)`별로 거쳐야 하는 target 상태 순서. */
        private val REPLAY_PATHS: Map<Pair<PaymentStatus, Long>, List<PaymentStatus>> = mapOf(
            (PaymentStatus.CREATED to 0L) to emptyList(),
            (PaymentStatus.APPROVED to 1L) to listOf(PaymentStatus.APPROVED),
            (PaymentStatus.DECLINED to 1L) to listOf(PaymentStatus.DECLINED),
            (PaymentStatus.FAILED to 1L) to listOf(PaymentStatus.FAILED),
            (PaymentStatus.RESOLUTION_REQUIRED to 1L) to listOf(PaymentStatus.RESOLUTION_REQUIRED),
            (PaymentStatus.APPROVED to 2L) to listOf(PaymentStatus.RESOLUTION_REQUIRED, PaymentStatus.APPROVED),
            (PaymentStatus.DECLINED to 2L) to listOf(PaymentStatus.RESOLUTION_REQUIRED, PaymentStatus.DECLINED),
            (PaymentStatus.FAILED to 2L) to listOf(PaymentStatus.RESOLUTION_REQUIRED, PaymentStatus.FAILED),
            (PaymentStatus.MANUAL_REVIEW_REQUIRED to 2L) to
                listOf(PaymentStatus.RESOLUTION_REQUIRED, PaymentStatus.MANUAL_REVIEW_REQUIRED),
        )

        fun from(payment: Payment): PaymentRow {
            check(payment.approvedAmount.currency == payment.transactionCurrency)
            check(payment.cancelledAmount.currency == payment.transactionCurrency)
            check(payment.reservedCancelAmount.currency == payment.transactionCurrency)
            return PaymentRow(
                paymentId = payment.paymentId.value,
                merchantId = payment.merchantId.value,
                clientReference = payment.clientReference.value,
                approvedAmountMinor = payment.approvedAmount.amountMinor,
                cancelledAmountMinor = payment.cancelledAmount.amountMinor,
                reservedCancelAmountMinor = payment.reservedCancelAmount.amountMinor,
                transactionCurrency = payment.transactionCurrency.value,
                status = payment.status.name,
                version = payment.version,
            )
        }
    }
}
