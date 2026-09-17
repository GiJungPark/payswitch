package io.github.gijungpark.payswitch.application.idempotency

import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType

/**
 * [IdempotencyRecord] 저장소 port.
 */
interface IdempotencyRepository {

    /**
     * 새 멱등키 기록을 저장한다.
     *
     * 같은 `merchantId`·`operationType`·`idempotencyKey`가 이미 있거나 참조한 금융거래가 없으면 DB 제약 위반으로 실패한다.
     * [io.github.gijungpark.payswitch.application.persistence.UnitOfWork] 안에서 호출해야 결제·금융거래와 원자적으로 저장된다.
     */
    fun insert(record: IdempotencyRecord)

    fun find(
        merchantId: MerchantId,
        operationType: FinancialTransactionType,
        idempotencyKey: IdempotencyKey,
    ): IdempotencyRecord?
}
