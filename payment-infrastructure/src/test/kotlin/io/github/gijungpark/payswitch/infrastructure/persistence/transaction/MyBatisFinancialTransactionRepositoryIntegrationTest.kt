package io.github.gijungpark.payswitch.infrastructure.persistence.transaction

import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransaction
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import io.github.gijungpark.payswitch.infrastructure.persistence.MySqlIntegrationTest
import io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException
import io.github.gijungpark.payswitch.infrastructure.persistence.payment.newPayment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisFinancialTransactionRepositoryIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var repository: FinancialTransactionRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun insertPayments() {
        paymentRepository.insert(newPayment(paymentId = "P-1", clientReference = "ORDER-1"))
        paymentRepository.insert(newPayment(paymentId = "P-2", clientReference = "ORDER-2"))
    }

    @ParameterizedTest
    @EnumSource(ReachableTransaction::class)
    fun findsSavedTransactionByIdWithSameState(state: ReachableTransaction) {
        val transaction = state.create()

        repository.insert(transaction)

        assertSameState(transaction, repository.findById(transaction.transactionId))
    }

    @Test
    fun keepsMaximumMinorUnitAmount() {
        val transaction = newTransaction(amount = Money(Long.MAX_VALUE, CurrencyCode.KRW))

        repository.insert(transaction)

        assertSameState(transaction, repository.findById(transaction.transactionId))
    }

    @Test
    fun findsTransactionsOfPaymentOrderedByTransactionId() {
        val second = newTransaction(transactionId = "T-2").apply { startProcessing(); markUnknown() }
        val first = newTransaction(transactionId = "T-1").apply { startProcessing(); succeed() }
        val otherPayment = newTransaction(transactionId = "T-3", paymentId = "P-2")
        listOf(second, first, otherPayment).forEach(repository::insert)

        val found = repository.findByPaymentId(PaymentId("P-1"))

        assertEquals(listOf(TransactionId("T-1"), TransactionId("T-2")), found.map { it.transactionId })
        assertSameState(first, found[0])
        assertSameState(second, found[1])
    }

    @Test
    fun returnsEmptyResultsWhenTransactionDoesNotExist() {
        repository.insert(newTransaction())

        assertNull(repository.findById(TransactionId("T-MISSING")))
        assertTrue(repository.findByPaymentId(PaymentId("P-2")).isEmpty())
        assertTrue(repository.findByPaymentId(PaymentId("P-MISSING")).isEmpty())
    }

    @Test
    fun rejectsDuplicateTransactionIdInsert() {
        repository.insert(newTransaction(paymentId = "P-1"))

        assertThrows<DuplicateKeyException> { repository.insert(newTransaction(paymentId = "P-2")) }
    }

    @Test
    fun rejectsTransactionReferencingMissingPayment() {
        assertThrows<DataIntegrityViolationException> {
            repository.insert(newTransaction(paymentId = "P-MISSING"))
        }
        assertNull(repository.findById(TransactionId("T-1")))
    }

    @Test
    fun rollsBackPaymentAndTransactionsSavedInSameSpringTransactionWhenLaterInsertFails() {
        val transactionTemplate = TransactionTemplate(transactionManager)

        assertThrows<DuplicateKeyException> {
            transactionTemplate.executeWithoutResult {
                paymentRepository.insert(newPayment(paymentId = "P-3", clientReference = "ORDER-3"))
                repository.insert(newTransaction(transactionId = "T-3", paymentId = "P-3"))
                assertEquals(PaymentId("P-3"), repository.findById(TransactionId("T-3"))?.paymentId)

                // 두 insert가 이미 성공한 뒤 같은 transaction 안에서 PK 중복으로 실패시킨다.
                repository.insert(newTransaction(transactionId = "T-3", paymentId = "P-3"))
            }
        }

        // 각 repository가 auto-commit으로 따로 실행됐다면 P-3과 T-3이 남는다.
        assertNull(paymentRepository.findById(PaymentId("P-3")))
        assertNull(repository.findById(TransactionId("T-3")))
        assertEquals(
            listOf("P-1", "P-2"),
            jdbcTemplate.queryForList("SELECT payment_id FROM payment ORDER BY payment_id", String::class.java),
        )
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_transaction", Int::class.java))
    }

    @Test
    fun refusesToRestoreRowOutsideReachableDomainState() {
        jdbcTemplate.update(
            """
            INSERT INTO financial_transaction (transaction_id, payment_id, transaction_type, original_transaction_id,
                                               amount_minor, currency, status, version)
            VALUES ('T-1', 'P-1', 'AUTHORIZE', NULL, 10000, 'KRW', 'UNKNOWN', 1)
            """.trimIndent(),
        )

        assertThrows<PersistedStateRestoreException> { repository.findById(TransactionId("T-1")) }
        assertThrows<PersistedStateRestoreException> { repository.findByPaymentId(PaymentId("P-1")) }
    }

    private fun assertSameState(expected: FinancialTransaction, actual: FinancialTransaction?) {
        assertNotNull(actual)
        assertEquals(FinancialTransactionRow.from(expected), FinancialTransactionRow.from(actual!!))
        assertEquals(expected.transactionId, actual.transactionId)
        assertEquals(expected.amount, actual.amount)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.version, actual.version)
    }
}

internal fun newTransaction(
    transactionId: String = "T-1",
    paymentId: String = "P-1",
    amount: Money = Money(10_000, CurrencyCode.KRW),
): FinancialTransaction = FinancialTransaction.createAuthorization(TransactionId(transactionId), PaymentId(paymentId), amount)

/** domain 공개 method로만 만들 수 있는 승인 FinancialTransaction 상태 전체. */
enum class ReachableTransaction(private val transitions: (FinancialTransaction) -> Unit) {
    RECEIVED({}),
    PROCESSING({ it.startProcessing() }),
    SUCCEEDED({ it.startProcessing(); it.succeed() }),
    DECLINED({ it.startProcessing(); it.decline() }),
    FAILED({ it.startProcessing(); it.fail() }),
    UNKNOWN({ it.startProcessing(); it.markUnknown() }),
    RESOLVED_SUCCEEDED({ it.startProcessing(); it.markUnknown(); it.succeed() }),
    RESOLVED_DECLINED({ it.startProcessing(); it.markUnknown(); it.decline() }),
    RESOLVED_FAILED({ it.startProcessing(); it.markUnknown(); it.fail() }),
    MANUAL_REVIEW_REQUIRED({ it.startProcessing(); it.markUnknown(); it.requireManualReview() }),
    ;

    fun create(): FinancialTransaction = newTransaction().also(transitions)
}
