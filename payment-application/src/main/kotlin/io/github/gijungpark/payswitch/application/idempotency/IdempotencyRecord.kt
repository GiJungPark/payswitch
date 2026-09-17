package io.github.gijungpark.payswitch.application.idempotency

import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType
import io.github.gijungpark.payswitch.domain.transaction.TransactionId

/**
 * 가맹점·operation 범위의 멱등키가 어떤 요청 hash로 어떤 금융거래를 만들었는지 기록한다.
 *
 * `merchantId`, `operationType`, `idempotencyKey` 조합은 DB에서 유일하다.
 */
data class IdempotencyRecord(
    val merchantId: MerchantId,
    val operationType: FinancialTransactionType,
    val idempotencyKey: IdempotencyKey,
    val requestHash: RequestHash,
    val transactionId: TransactionId,
)
