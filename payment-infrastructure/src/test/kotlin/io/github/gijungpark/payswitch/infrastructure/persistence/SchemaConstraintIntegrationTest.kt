package io.github.gijungpark.payswitch.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.dao.DataAccessException
import java.sql.SQLException

private const val DUPLICATE_KEY = 1062
private const val FOREIGN_KEY_MISSING_PARENT = 1452
private const val CHECK_CONSTRAINT_VIOLATED = 3819

/**
 * domain 검증을 거치지 않는 raw SQL로 DB 제약 자체가 잘못된 row를 거절하는지 검증한다.
 */
class SchemaConstraintIntegrationTest : MySqlIntegrationTest() {

    @Test
    fun acceptsValidRawRows() {
        insertPayment()
        insertTransaction()

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_transaction", Int::class.java))
    }

    @Test
    fun rejectsDuplicateMerchantClientReference() {
        insertPayment(mapOf("payment_id" to "P-1"))

        assertRejected(DUPLICATE_KEY, "uk_payment_merchant_client_reference") {
            insertPayment(mapOf("payment_id" to "P-2"))
        }
    }

    @Test
    fun allowsSameClientReferenceForDifferentMerchants() {
        insertPayment(mapOf("payment_id" to "P-1", "merchant_id" to "M-1"))
        insertPayment(mapOf("payment_id" to "P-2", "merchant_id" to "M-2"))

        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Int::class.java))
    }

    @Test
    fun treatsIdentifierCaseAndTrailingSpaceAsDistinct() {
        insertPayment(mapOf("payment_id" to "P-1", "client_reference" to "ORDER-1"))
        insertPayment(mapOf("payment_id" to "P-2", "client_reference" to "order-1"))
        insertPayment(mapOf("payment_id" to "P-3", "client_reference" to "ORDER-1 "))

        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Int::class.java))
    }

    @Test
    fun rejectsDuplicatePaymentId() {
        insertPayment(mapOf("client_reference" to "ORDER-1"))

        assertRejected(DUPLICATE_KEY, "PRIMARY") { insertPayment(mapOf("client_reference" to "ORDER-2")) }
    }

    @ParameterizedTest(name = "{0}={1} violates {2}")
    @MethodSource("invalidPaymentValues")
    fun rejectsInvalidPaymentValues(column: String, value: Any, constraint: String) {
        assertRejected(CHECK_CONSTRAINT_VIOLATED, constraint) { insertPayment(mapOf(column to value)) }
    }

    @Test
    fun rejectsCancelAmountsExceedingApprovedAmount() {
        assertRejected(CHECK_CONSTRAINT_VIOLATED, "ck_payment_cancel_within_approved") {
            insertPayment(
                mapOf(
                    "approved_amount_minor" to 10_000L,
                    "cancelled_amount_minor" to 6_000L,
                    "reserved_cancel_amount_minor" to 4_001L,
                ),
            )
        }
    }

    @Test
    fun rejectsTransactionReferencingMissingPayment() {
        assertRejected(FOREIGN_KEY_MISSING_PARENT, "fk_financial_transaction_payment") {
            insertTransaction(mapOf("payment_id" to "P-MISSING"))
        }
    }

    @Test
    fun rejectsDuplicateTransactionId() {
        insertPayment()
        insertTransaction()

        assertRejected(DUPLICATE_KEY, "PRIMARY") { insertTransaction() }
    }

    @Test
    fun rejectsAuthorizationWithOriginalTransaction() {
        insertPayment()
        insertTransaction(mapOf("transaction_id" to "T-1"))

        assertRejected(CHECK_CONSTRAINT_VIOLATED, "ck_financial_transaction_authorize_without_original") {
            insertTransaction(mapOf("transaction_id" to "T-2", "original_transaction_id" to "T-1"))
        }
    }

    @ParameterizedTest(name = "{0}={1} violates {2}")
    @MethodSource("invalidTransactionValues")
    fun rejectsInvalidTransactionValues(column: String, value: Any, constraint: String) {
        insertPayment()

        assertRejected(CHECK_CONSTRAINT_VIOLATED, constraint) { insertTransaction(mapOf(column to value)) }
    }

    private fun insertPayment(overrides: Map<String, Any?> = emptyMap()) =
        insert(
            "payment",
            mapOf(
                "payment_id" to "P-1",
                "merchant_id" to "M-1",
                "client_reference" to "ORDER-1",
                "approved_amount_minor" to 10_000L,
                "cancelled_amount_minor" to 0L,
                "reserved_cancel_amount_minor" to 0L,
                "transaction_currency" to "KRW",
                "status" to "APPROVED",
                "version" to 1L,
            ) + overrides,
        )

    private fun insertTransaction(overrides: Map<String, Any?> = emptyMap()) =
        insert(
            "financial_transaction",
            mapOf(
                "transaction_id" to "T-1",
                "payment_id" to "P-1",
                "transaction_type" to "AUTHORIZE",
                "original_transaction_id" to null,
                "amount_minor" to 10_000L,
                "currency" to "KRW",
                "status" to "SUCCEEDED",
                "version" to 2L,
            ) + overrides,
        )

    private fun insert(table: String, values: Map<String, Any?>) {
        val columns = values.keys.joinToString()
        val placeholders = values.keys.joinToString { "?" }
        jdbcTemplate.update("INSERT INTO $table ($columns) VALUES ($placeholders)", *values.values.toTypedArray())
    }

    private fun assertRejected(errorCode: Int, constraint: String, insert: () -> Unit) {
        val exception = assertThrows<DataAccessException> { insert() }
        val sqlException = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<SQLException>()
            .first()

        assertEquals(errorCode, sqlException.errorCode, sqlException.message)
        assertTrue(sqlException.message.orEmpty().contains(constraint), sqlException.message)
    }

    companion object {
        @JvmStatic
        fun invalidPaymentValues(): List<Arguments> = listOf(
            Arguments.of("approved_amount_minor", -1L, "ck_payment_approved_amount_non_negative"),
            Arguments.of("cancelled_amount_minor", -1L, "ck_payment_cancelled_amount_non_negative"),
            Arguments.of("reserved_cancel_amount_minor", -1L, "ck_payment_reserved_cancel_amount_non_negative"),
            Arguments.of("transaction_currency", "USD", "ck_payment_transaction_currency"),
            Arguments.of("transaction_currency", "krw", "ck_payment_transaction_currency"),
            Arguments.of("transaction_currency", "KR1", "ck_payment_transaction_currency"),
            Arguments.of("transaction_currency", "KR", "ck_payment_transaction_currency"),
            Arguments.of("status", "UNKNOWN", "ck_payment_status"),
            Arguments.of("status", "approved", "ck_payment_status"),
            Arguments.of("version", -1L, "ck_payment_version_non_negative"),
        )

        @JvmStatic
        fun invalidTransactionValues(): List<Arguments> = listOf(
            Arguments.of("amount_minor", -1L, "ck_financial_transaction_amount_positive"),
            Arguments.of("amount_minor", 0L, "ck_financial_transaction_amount_positive"),
            Arguments.of("currency", "USD", "ck_financial_transaction_currency"),
            Arguments.of("currency", "krw", "ck_financial_transaction_currency"),
            Arguments.of("currency", "K W", "ck_financial_transaction_currency"),
            Arguments.of("transaction_type", "REFUND", "ck_financial_transaction_type"),
            Arguments.of("status", "APPROVED", "ck_financial_transaction_status"),
            Arguments.of("status", "succeeded", "ck_financial_transaction_status"),
            Arguments.of("version", -1L, "ck_financial_transaction_version_non_negative"),
        )
    }
}
