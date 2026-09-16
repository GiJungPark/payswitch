package io.github.gijungpark.payswitch.infrastructure.persistence.transaction

import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransaction
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionStatus
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import io.github.gijungpark.payswitch.infrastructure.persistence.restorePersistedState

/**
 * `financial_transaction` table row. 금액은 [currency]의 최소 단위 정수이고 enum은 이름으로 저장한다.
 */
internal data class FinancialTransactionRow(
    val transactionId: String,
    val paymentId: String,
    val transactionType: String,
    val originalTransactionId: String?,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val version: Long,
) {

    /**
     * 공개 생성 factory와 상태 전이 method를 재생해 [FinancialTransaction]을 복원한다.
     *
     * `(status, version)`이 도달 가능한 전이 경로가 아니거나 재생 결과가 row와 하나라도 다르면
     * [io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException]을 던진다.
     */
    fun toDomain(): FinancialTransaction = restorePersistedState(TABLE, transactionId) {
        val transactionStatus = FinancialTransactionStatus.valueOf(status)
        val path = REPLAY_PATHS[transactionStatus to version]
            ?: throw IllegalStateException("status $status is not reachable at version $version")

        val transaction = when (FinancialTransactionType.valueOf(transactionType)) {
            FinancialTransactionType.AUTHORIZE -> FinancialTransaction.createAuthorization(
                TransactionId(transactionId),
                PaymentId(paymentId),
                Money(amountMinor, CurrencyCode(currency)),
            )
        }
        path.forEach { target -> replay(transaction, target) }

        check(from(transaction) == this) { "row does not match the replayed domain state" }
        transaction
    }

    private fun replay(transaction: FinancialTransaction, target: FinancialTransactionStatus) {
        when (target) {
            FinancialTransactionStatus.PROCESSING -> transaction.startProcessing()
            FinancialTransactionStatus.SUCCEEDED -> transaction.succeed()
            FinancialTransactionStatus.DECLINED -> transaction.decline()
            FinancialTransactionStatus.FAILED -> transaction.fail()
            FinancialTransactionStatus.UNKNOWN -> transaction.markUnknown()
            FinancialTransactionStatus.MANUAL_REVIEW_REQUIRED -> transaction.requireManualReview()
            FinancialTransactionStatus.RECEIVED -> error("RECEIVED is not a transition target")
        }
    }

    companion object {
        private const val TABLE = "financial_transaction"

        private val RECEIVED = FinancialTransactionStatus.RECEIVED
        private val PROCESSING = FinancialTransactionStatus.PROCESSING
        private val SUCCEEDED = FinancialTransactionStatus.SUCCEEDED
        private val DECLINED = FinancialTransactionStatus.DECLINED
        private val FAILED = FinancialTransactionStatus.FAILED
        private val UNKNOWN = FinancialTransactionStatus.UNKNOWN
        private val MANUAL_REVIEW_REQUIRED = FinancialTransactionStatus.MANUAL_REVIEW_REQUIRED

        /** domain-model.md의 구현된 FinancialTransaction 전이에서 `(상태, version)`별로 거쳐야 하는 target 상태 순서. */
        private val REPLAY_PATHS: Map<Pair<FinancialTransactionStatus, Long>, List<FinancialTransactionStatus>> = mapOf(
            (RECEIVED to 0L) to emptyList(),
            (PROCESSING to 1L) to listOf(PROCESSING),
            (SUCCEEDED to 2L) to listOf(PROCESSING, SUCCEEDED),
            (DECLINED to 2L) to listOf(PROCESSING, DECLINED),
            (FAILED to 2L) to listOf(PROCESSING, FAILED),
            (UNKNOWN to 2L) to listOf(PROCESSING, UNKNOWN),
            (SUCCEEDED to 3L) to listOf(PROCESSING, UNKNOWN, SUCCEEDED),
            (DECLINED to 3L) to listOf(PROCESSING, UNKNOWN, DECLINED),
            (FAILED to 3L) to listOf(PROCESSING, UNKNOWN, FAILED),
            (MANUAL_REVIEW_REQUIRED to 3L) to listOf(PROCESSING, UNKNOWN, MANUAL_REVIEW_REQUIRED),
        )

        fun from(transaction: FinancialTransaction): FinancialTransactionRow =
            FinancialTransactionRow(
                transactionId = transaction.transactionId.value,
                paymentId = transaction.paymentId.value,
                transactionType = transaction.type.name,
                originalTransactionId = transaction.originalTransactionId?.value,
                amountMinor = transaction.amount.amountMinor,
                currency = transaction.amount.currency.value,
                status = transaction.status.name,
                version = transaction.version,
            )
    }
}
