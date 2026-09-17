package io.github.gijungpark.payswitch

import io.github.gijungpark.payswitch.application.authorization.AuthorizePaymentUseCase
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRepository
import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

@SpringBootTest
@ActiveProfiles("test")
class PaymentApiApplicationTests {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun contextLoads() {
    }

    @Test
    fun assemblesMyBatisRepositoryAdapters() {
        assertEquals(
            "MyBatisPaymentRepository",
            context.getBean(PaymentRepository::class.java).javaClass.simpleName,
        )
        assertEquals(
            "MyBatisFinancialTransactionRepository",
            context.getBean(FinancialTransactionRepository::class.java).javaClass.simpleName,
        )
        assertEquals(
            "MyBatisIdempotencyRepository",
            context.getBean(IdempotencyRepository::class.java).javaClass.simpleName,
        )
        context.getBean(AuthorizePaymentUseCase::class.java)
    }

    @Test
    fun appliesMigrationToTestProfileMySql() {
        dataSource.connection.use { connection ->
            assertEquals("MySQL", connection.metaData.databaseProductName)

            val appliedVersions = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL " +
                        "ORDER BY installed_rank",
                ).use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
            }
            assertEquals(listOf("1", "2"), appliedVersions)

            val tables = connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name IN ('payment', 'financial_transaction', 'idempotency_request')
                    ORDER BY table_name
                    """.trimIndent(),
                ).use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
            }
            assertEquals(listOf("financial_transaction", "idempotency_request", "payment"), tables)
        }
    }
}
