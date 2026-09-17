package io.github.gijungpark.payswitch.infrastructure.persistence.idempotency

import io.github.gijungpark.payswitch.application.idempotency.IdempotencyKey
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRecord
import io.github.gijungpark.payswitch.application.idempotency.RequestHash
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import io.github.gijungpark.payswitch.infrastructure.persistence.restorePersistedState

/**
 * `idempotency_request` table row. operation type은 enum 이름, request hash는 소문자 16진수로 저장한다.
 */
internal data class IdempotencyRequestRow(
    val merchantId: String,
    val operationType: String,
    val idempotencyKey: String,
    val requestHash: String,
    val transactionId: String,
) {

    /** 값 검증에 실패하면 [io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException]을 던진다. */
    fun toRecord(): IdempotencyRecord = restorePersistedState(TABLE, "$merchantId/$operationType/$idempotencyKey") {
        IdempotencyRecord(
            merchantId = MerchantId(merchantId),
            operationType = FinancialTransactionType.valueOf(operationType),
            idempotencyKey = IdempotencyKey(idempotencyKey),
            requestHash = RequestHash(requestHash),
            transactionId = TransactionId(transactionId),
        )
    }

    companion object {
        private const val TABLE = "idempotency_request"

        fun from(record: IdempotencyRecord): IdempotencyRequestRow =
            IdempotencyRequestRow(
                merchantId = record.merchantId.value,
                operationType = record.operationType.name,
                idempotencyKey = record.idempotencyKey.value,
                requestHash = record.requestHash.value,
                transactionId = record.transactionId.value,
            )
    }
}
