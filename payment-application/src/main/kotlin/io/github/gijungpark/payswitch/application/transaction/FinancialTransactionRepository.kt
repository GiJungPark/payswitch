package io.github.gijungpark.payswitch.application.transaction

import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransaction
import io.github.gijungpark.payswitch.domain.transaction.TransactionId

/**
 * [FinancialTransaction] 저장소 port.
 *
 * 조회 결과는 domain 규칙상 도달 가능한 persisted state만 복원한다. 상태 변경 저장과 optimistic lock은 후속 Issue에서 정의한다.
 */
interface FinancialTransactionRepository {

    /**
     * 새 금융거래를 저장한다.
     *
     * 같은 `transactionId`가 이미 있거나 참조한 결제가 없으면 DB 제약 위반으로 실패한다.
     */
    fun insert(transaction: FinancialTransaction)

    fun findById(transactionId: TransactionId): FinancialTransaction?

    /** 결제에 속한 금융거래를 `transactionId` 오름차순으로 반환한다. */
    fun findByPaymentId(paymentId: PaymentId): List<FinancialTransaction>
}
