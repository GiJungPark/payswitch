package io.github.gijungpark.payswitch.infrastructure.persistence.idempotency

import io.github.gijungpark.payswitch.application.idempotency.IdempotencyKey
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRecord
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRepository
import io.github.gijungpark.payswitch.application.idempotency.RequestHash
import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import io.github.gijungpark.payswitch.infrastructure.persistence.MySqlIntegrationTest
import io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException
import io.github.gijungpark.payswitch.infrastructure.persistence.payment.newPayment
import io.github.gijungpark.payswitch.infrastructure.persistence.transaction.newTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import java.sql.SQLException

private const val DUPLICATE_KEY = 1062
private const val CHECK_CONSTRAINT_VIOLATED = 3819
private val HASH_A = RequestHash("a".repeat(64))
private val HASH_B = RequestHash("b".repeat(64))

class MyBatisIdempotencyRepositoryIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var transactionRepository: FinancialTransactionRepository

    @Autowired
    private lateinit var repository: IdempotencyRepository

    @BeforeEach
    fun insertTransactions() {
        paymentRepository.insert(newPayment(paymentId = "P-1", clientReference = "ORDER-1"))
        paymentRepository.insert(newPayment(paymentId = "P-2", clientReference = "ORDER-2"))
        transactionRepository.insert(newTransaction(transactionId = "T-1", paymentId = "P-1"))
        transactionRepository.insert(newTransaction(transactionId = "T-2", paymentId = "P-2"))
    }

    @Test
    fun findsSavedRecordByMerchantOperationAndKey() {
        val record = record(idempotencyKey = "가".repeat(IdempotencyKey.MAX_LENGTH))

        repository.insert(record)

        assertEquals(record, repository.find(record.merchantId, record.operationType, record.idempotencyKey))
    }

    @Test
    fun comparesKeyExactly() {
        repository.insert(record(idempotencyKey = "KEY-1"))

        assertNull(find(idempotencyKey = "key-1"))
        assertNull(find(idempotencyKey = "KEY-1 "))
        assertNull(find(idempotencyKey = " KEY-1"))
        assertNull(find(merchantId = "M-2"))
        assertEquals(TransactionId("T-1"), find(idempotencyKey = "KEY-1")?.transactionId)
    }

    @Test
    fun allowsSameKeyForDifferentMerchants() {
        repository.insert(record(merchantId = "M-1", transactionId = "T-1"))
        repository.insert(record(merchantId = "M-2", transactionId = "T-2", requestHash = HASH_B))

        assertEquals(HASH_A, find(merchantId = "M-1")?.requestHash)
        assertEquals(HASH_B, find(merchantId = "M-2")?.requestHash)
    }

    @Test
    fun rejectsDuplicateMerchantOperationKeyAndKeepsFirstRecord() {
        repository.insert(record(transactionId = "T-1", requestHash = HASH_A))

        val exception = assertThrows<DuplicateKeyException> {
            repository.insert(record(transactionId = "T-2", requestHash = HASH_B))
        }

        assertSqlError(exception, DUPLICATE_KEY, "idempotency_request.PRIMARY")
        assertEquals(record(transactionId = "T-1", requestHash = HASH_A), find())
    }

    @Test
    fun allowsSeveralKeysForSameTransaction() {
        repository.insert(record(idempotencyKey = "KEY-1", transactionId = "T-1", requestHash = HASH_A))
        repository.insert(record(idempotencyKey = "KEY-2", transactionId = "T-1", requestHash = HASH_B))

        assertEquals(TransactionId("T-1"), find(idempotencyKey = "KEY-1")?.transactionId)
        assertEquals(TransactionId("T-1"), find(idempotencyKey = "KEY-2")?.transactionId)
        assertEquals(HASH_B, find(idempotencyKey = "KEY-2")?.requestHash)
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotency_request WHERE transaction_id = 'T-1'",
                Int::class.java,
            ),
        )
    }

    @Test
    fun rejectsRecordReferencingMissingTransaction() {
        assertThrows<DataIntegrityViolationException> { repository.insert(record(transactionId = "T-MISSING")) }
        assertNull(find())
    }

    @ParameterizedTest
    @CsvSource(
        "operation_type, CANCEL, ck_idempotency_request_operation_type",
        "operation_type, authorize, ck_idempotency_request_operation_type",
        "request_hash, AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA, ck_idempotency_request_request_hash",
        "request_hash, gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg, ck_idempotency_request_request_hash",
        "request_hash, aaaa, ck_idempotency_request_request_hash",
    )
    fun rejectsInvalidRawValues(column: String, value: String, constraint: String) {
        val values = mutableMapOf(
            "merchant_id" to "M-1",
            "operation_type" to "AUTHORIZE",
            "idempotency_key" to "KEY-1",
            "request_hash" to HASH_A.value,
            "transaction_id" to "T-1",
        )
        values[column] = value

        val exception = assertThrows<DataAccessException> {
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_request (merchant_id, operation_type, idempotency_key, request_hash, transaction_id)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                *values.values.toTypedArray(),
            )
        }

        assertSqlError(exception, CHECK_CONSTRAINT_VIOLATED, constraint)
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_request", Int::class.java))
    }

    @Test
    fun refusesToRestoreRowWithInvalidKey() {
        // DB CHECK는 key blank 여부를 검사하지 않으므로 복원 경계에서 거절한다.
        val exception = assertThrows<PersistedStateRestoreException> {
            IdempotencyRequestRow("M-1", "AUTHORIZE", " ", HASH_A.value, "T-1").toRecord()
        }
        assertTrue(exception.message.orEmpty().contains("idempotency_request"))
    }

    private fun find(merchantId: String = "M-1", idempotencyKey: String = "KEY-1"): IdempotencyRecord? =
        repository.find(MerchantId(merchantId), FinancialTransactionType.AUTHORIZE, IdempotencyKey(idempotencyKey))

    private fun record(
        merchantId: String = "M-1",
        idempotencyKey: String = "KEY-1",
        requestHash: RequestHash = HASH_A,
        transactionId: String = "T-1",
    ) = IdempotencyRecord(
        MerchantId(merchantId),
        FinancialTransactionType.AUTHORIZE,
        IdempotencyKey(idempotencyKey),
        requestHash,
        TransactionId(transactionId),
    )

    private fun assertSqlError(exception: Throwable, errorCode: Int, message: String) {
        val sqlException = generateSequence(exception) { it.cause }.filterIsInstance<SQLException>().first()
        assertEquals(errorCode, sqlException.errorCode, sqlException.message)
        assertTrue(sqlException.message.orEmpty().contains(message), sqlException.message)
    }
}
