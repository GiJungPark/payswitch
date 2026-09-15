package io.github.gijungpark.payswitch.infrastructure.persistence.transaction

import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransaction
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import org.springframework.stereotype.Component

/**
 * [FinancialTransactionRepository]의 MyBatis 구현.
 *
 * DB 제약 위반은 MyBatis-Spring이 변환한 `org.springframework.dao.DataIntegrityViolationException` 계열로 전파된다.
 */
@Component
internal class MyBatisFinancialTransactionRepository(
    private val mapper: FinancialTransactionMapper,
) : FinancialTransactionRepository {

    override fun insert(transaction: FinancialTransaction) {
        val inserted = mapper.insert(FinancialTransactionRow.from(transaction))
        check(inserted == 1) { "expected to insert one financial_transaction row but inserted $inserted" }
    }

    override fun findById(transactionId: TransactionId): FinancialTransaction? =
        mapper.findById(transactionId.value)?.toDomain()

    override fun findByPaymentId(paymentId: PaymentId): List<FinancialTransaction> =
        mapper.findByPaymentId(paymentId.value).map { it.toDomain() }
}
