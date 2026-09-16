package io.github.gijungpark.payswitch.infrastructure.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MigrationSchemaIntegrationTest : MySqlIntegrationTest() {

    @Test
    fun appliesVersionedMigrationToEmptyDatabase() {
        val applied = jdbcTemplate.queryForList(
            "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
        ).map { it["version"] to it["success"] }

        assertEquals(listOf("1" to true), applied)
    }

    @Test
    fun createsPaymentColumnsWithMinorUnitBigint() {
        assertEquals(
            mapOf(
                "payment_id" to "varchar(64) NO",
                "merchant_id" to "varchar(64) NO",
                "client_reference" to "varchar(128) NO",
                "approved_amount_minor" to "bigint NO",
                "cancelled_amount_minor" to "bigint NO",
                "reserved_cancel_amount_minor" to "bigint NO",
                "transaction_currency" to "char(3) NO",
                "status" to "varchar(32) NO",
                "version" to "bigint NO",
            ),
            columnsOf("payment"),
        )
    }

    @Test
    fun createsFinancialTransactionColumnsWithMinorUnitBigint() {
        assertEquals(
            mapOf(
                "transaction_id" to "varchar(64) NO",
                "payment_id" to "varchar(64) NO",
                "transaction_type" to "varchar(16) NO",
                "original_transaction_id" to "varchar(64) YES",
                "amount_minor" to "bigint NO",
                "currency" to "char(3) NO",
                "status" to "varchar(32) NO",
                "version" to "bigint NO",
            ),
            columnsOf("financial_transaction"),
        )
    }

    @Test
    fun createsDocumentedPaymentConstraints() {
        assertEquals(
            setOf(
                "PRIMARY" to "PRIMARY KEY",
                "uk_payment_merchant_client_reference" to "UNIQUE",
                "ck_payment_approved_amount_non_negative" to "CHECK",
                "ck_payment_cancelled_amount_non_negative" to "CHECK",
                "ck_payment_reserved_cancel_amount_non_negative" to "CHECK",
                "ck_payment_cancel_within_approved" to "CHECK",
                "ck_payment_transaction_currency" to "CHECK",
                "ck_payment_status" to "CHECK",
                "ck_payment_version_non_negative" to "CHECK",
            ),
            constraintsOf("payment"),
        )
        assertEquals(
            listOf("merchant_id", "client_reference"),
            jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'payment'
                  AND constraint_name = 'uk_payment_merchant_client_reference'
                ORDER BY ordinal_position
                """.trimIndent(),
                String::class.java,
            ),
        )
    }

    @Test
    fun createsDocumentedFinancialTransactionConstraints() {
        assertEquals(
            setOf(
                "PRIMARY" to "PRIMARY KEY",
                "fk_financial_transaction_payment" to "FOREIGN KEY",
                "fk_financial_transaction_original" to "FOREIGN KEY",
                "ck_financial_transaction_amount_positive" to "CHECK",
                "ck_financial_transaction_currency" to "CHECK",
                "ck_financial_transaction_type" to "CHECK",
                "ck_financial_transaction_authorize_without_original" to "CHECK",
                "ck_financial_transaction_status" to "CHECK",
                "ck_financial_transaction_version_non_negative" to "CHECK",
            ),
            constraintsOf("financial_transaction"),
        )
        assertEquals(
            setOf(
                "fk_financial_transaction_payment" to "payment_id -> payment.payment_id",
                "fk_financial_transaction_original" to
                    "original_transaction_id -> financial_transaction.transaction_id",
            ),
            jdbcTemplate.queryForList(
                """
                SELECT constraint_name, column_name, referenced_table_name, referenced_column_name
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'financial_transaction'
                  AND referenced_table_name IS NOT NULL
                """.trimIndent(),
            ).map {
                it["CONSTRAINT_NAME"] to
                    "${it["COLUMN_NAME"]} -> ${it["REFERENCED_TABLE_NAME"]}.${it["REFERENCED_COLUMN_NAME"]}"
            }.toSet(),
        )
    }

    private fun columnsOf(table: String): Map<String, String> =
        jdbcTemplate.queryForList(
            """
            SELECT column_name, column_type, is_nullable FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ?
            ORDER BY ordinal_position
            """.trimIndent(),
            table,
        ).associate { it["COLUMN_NAME"] as String to "${it["COLUMN_TYPE"]} ${it["IS_NULLABLE"]}" }

    private fun constraintsOf(table: String): Set<Pair<String, String>> =
        jdbcTemplate.queryForList(
            """
            SELECT constraint_name, constraint_type FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = ?
            """.trimIndent(),
            table,
        ).map { it["CONSTRAINT_NAME"] as String to it["CONSTRAINT_TYPE"] as String }.toSet()
}
