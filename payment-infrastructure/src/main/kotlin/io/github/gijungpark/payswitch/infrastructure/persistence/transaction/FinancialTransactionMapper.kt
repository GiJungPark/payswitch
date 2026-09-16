package io.github.gijungpark.payswitch.infrastructure.persistence.transaction

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** `financial_transaction` table MyBatis mapper. SQL은 같은 package의 `FinancialTransactionMapper.xml`에 있다. */
@Mapper
internal interface FinancialTransactionMapper {

    fun insert(row: FinancialTransactionRow): Int

    fun findById(@Param("transactionId") transactionId: String): FinancialTransactionRow?

    fun findByPaymentId(@Param("paymentId") paymentId: String): List<FinancialTransactionRow>
}
