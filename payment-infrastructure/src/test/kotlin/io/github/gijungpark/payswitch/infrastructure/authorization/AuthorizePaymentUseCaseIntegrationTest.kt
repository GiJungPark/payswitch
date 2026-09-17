package io.github.gijungpark.payswitch.infrastructure.authorization

import io.github.gijungpark.payswitch.application.authorization.AuthorizationConflictReason
import io.github.gijungpark.payswitch.application.authorization.AuthorizationIdGenerator
import io.github.gijungpark.payswitch.application.authorization.AuthorizationIntakeResult
import io.github.gijungpark.payswitch.application.authorization.AuthorizationRequestCanonicalForm
import io.github.gijungpark.payswitch.application.authorization.AuthorizePaymentCommand
import io.github.gijungpark.payswitch.application.authorization.AuthorizePaymentUseCase
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyKey
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRecord
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRepository
import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.application.persistence.UnitOfWork
import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import io.github.gijungpark.payswitch.infrastructure.persistence.MySqlIntegrationTest
import io.github.gijungpark.payswitch.infrastructure.persistence.payment.newPayment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val CONCURRENT_REQUESTS = 8
private const val WAIT_SECONDS = 30L

/**
 * 실제 MySQL에서 승인 접수의 원자성, 멱등 재사용, 충돌과 동시 중복 요청을 검증한다.
 */
class AuthorizePaymentUseCaseIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var springUseCase: AuthorizePaymentUseCase

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var transactionRepository: FinancialTransactionRepository

    @Autowired
    private lateinit var idempotencyRepository: IdempotencyRepository

    @Autowired
    private lateinit var unitOfWork: UnitOfWork

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val executor: ExecutorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)

    @AfterEach
    fun shutdownExecutor() {
        executor.shutdownNow()
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)
    }

    @Test
    fun registersAllRowsOnceForFirstRequest() {
        val result = springUseCase.authorize(command())

        val registered = assertInstanceOf(AuthorizationIntakeResult.Registered::class.java, result)
        val paymentId = registered.payment.paymentId.value
        val transactionId = registered.transaction.transactionId.value
        assertEquals(
            listOf(
                mapOf(
                    "payment_id" to paymentId,
                    "client_reference" to "ORDER-1",
                    "status" to "CREATED",
                    "version" to 0L,
                ),
            ),
            jdbcTemplate.queryForList("SELECT payment_id, client_reference, status, version FROM payment")
                .map { it.lowercaseKeys() },
        )
        assertEquals(
            listOf(
                mapOf(
                    "transaction_id" to transactionId,
                    "payment_id" to paymentId,
                    "transaction_type" to "AUTHORIZE",
                    "amount_minor" to 10_000L,
                    "currency" to "KRW",
                    "status" to "RECEIVED",
                    "version" to 0L,
                ),
            ),
            jdbcTemplate.queryForList(
                """
                SELECT transaction_id, payment_id, transaction_type, amount_minor, currency, status, version
                FROM financial_transaction
                """.trimIndent(),
            ).map { it.lowercaseKeys() },
        )
        assertEquals(
            listOf(
                mapOf(
                    "merchant_id" to "M-1",
                    "operation_type" to "AUTHORIZE",
                    "idempotency_key" to "KEY-1",
                    // SHA-256("9:AUTHORIZE3:M-17:ORDER-15:100003:KRW")
                    "request_hash" to "bce351799eeaaf26a2a540b19d0a18d514d27bdeec7f800e29a38297923c539a",
                    "transaction_id" to transactionId,
                ),
            ),
            jdbcTemplate.queryForList("SELECT * FROM idempotency_request").map { it.lowercaseKeys() },
        )
    }

    @Test
    fun reusesTransactionForSequentialDuplicateWithoutNewRows() {
        val first = springUseCase.authorize(command()) as AuthorizationIntakeResult.Registered
        val before = snapshot()

        val second = springUseCase.authorize(command())

        val reused = assertInstanceOf(AuthorizationIntakeResult.Reused::class.java, second)
        assertEquals(first.transaction.transactionId, reused.transaction.transactionId)
        assertEquals(first.payment.paymentId, reused.payment.paymentId)
        assertEquals(before, snapshot())
    }

    @Test
    fun rejectsSameKeyWithDifferentRequestWithoutChangingRows() {
        springUseCase.authorize(command())
        val before = snapshot()

        val differentAmount = springUseCase.authorize(command(amountMinor = 20_000))
        val differentClientReference = springUseCase.authorize(command(clientReference = "ORDER-2"))

        listOf(differentAmount, differentClientReference).forEach {
            assertEquals(AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED), it)
        }
        assertEquals(before, snapshot())
    }

    @Test
    fun resolvesSameClientReferenceWithDifferentKeyByContent() {
        val first = springUseCase.authorize(command()) as AuthorizationIntakeResult.Registered
        val before = snapshot()

        val sameContent = springUseCase.authorize(command(idempotencyKey = "KEY-2"))
        val afterBinding = snapshot()
        val differentAmount = springUseCase.authorize(command(idempotencyKey = "KEY-3", amountMinor = 9_999))
        val differentCurrency = springUseCase.authorize(command(idempotencyKey = "KEY-4", currency = "USD"))

        val reused = assertInstanceOf(AuthorizationIntakeResult.Reused::class.java, sameContent)
        assertEquals(first.transaction.transactionId, reused.transaction.transactionId)
        listOf(differentAmount, differentCurrency).forEach {
            assertEquals(AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.CLIENT_REFERENCE_CONFLICT), it)
        }
        // 결제·금융거래는 불변이고 KEY-2만 기존 거래에 결합된다. 충돌한 KEY-3, KEY-4는 저장하지 않는다.
        assertEquals(before.take(2), afterBinding.take(2))
        assertEquals(
            listOf(
                idempotencyRow("KEY-1", command(), first.transaction.transactionId.value),
                idempotencyRow("KEY-2", command(idempotencyKey = "KEY-2"), first.transaction.transactionId.value),
            ),
            afterBinding[2],
        )
        assertEquals(afterBinding, snapshot())
    }

    @Test
    fun rejectsLaterDifferentRequestWithKeyBoundByReuse() {
        val first = springUseCase.authorize(command(idempotencyKey = "KEY-1")) as AuthorizationIntakeResult.Registered
        assertInstanceOf(
            AuthorizationIntakeResult.Reused::class.java,
            springUseCase.authorize(command(idempotencyKey = "KEY-2")),
        )
        val before = snapshot()

        val results = listOf(
            springUseCase.authorize(command(idempotencyKey = "KEY-2", clientReference = "ORDER-2")),
            springUseCase.authorize(command(idempotencyKey = "KEY-2", amountMinor = 20_000)),
            springUseCase.authorize(command(idempotencyKey = "KEY-2", currency = "USD")),
        )

        results.forEach {
            assertEquals(AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED), it)
        }
        assertEquals(before, snapshot())
        assertCounts(payments = 1, transactions = 1, idempotency = 2)
        assertEquals(
            listOf(first.transaction.transactionId.value, first.transaction.transactionId.value),
            jdbcTemplate.queryForList(
                "SELECT transaction_id FROM idempotency_request ORDER BY idempotency_key",
                String::class.java,
            ),
        )
    }

    @Test
    fun rollsBackOnlyNewKeyWhenReuseBindingFails() {
        springUseCase.authorize(command(idempotencyKey = "KEY-1"))
        val before = snapshot()
        val failingIdempotencyRepository = object : IdempotencyRepository by idempotencyRepository {
            override fun insert(record: IdempotencyRecord) {
                idempotencyRepository.insert(record)
                throw IllegalStateException("injected failure after key binding")
            }
        }

        val thrown = assertThrows<IllegalStateException> {
            useCase(idempotency = failingIdempotencyRepository).authorize(command(idempotencyKey = "KEY-2"))
        }

        assertEquals("injected failure after key binding", thrown.message)
        assertEquals(before, snapshot())
    }

    @Test
    fun rollsBackAllRowsWhenFailureOccursAfterLastInsert() {
        val failingIdempotencyRepository = object : IdempotencyRepository by idempotencyRepository {
            override fun insert(record: IdempotencyRecord) {
                idempotencyRepository.insert(record)
                throw IllegalStateException("injected failure after idempotency insert")
            }
        }
        val useCase = useCase(idempotency = failingIdempotencyRepository)

        val thrown = assertThrows<IllegalStateException> { useCase.authorize(command()) }

        assertEquals("injected failure after idempotency insert", thrown.message)
        assertCounts(payments = 0, transactions = 0, idempotency = 0)
    }

    @Test
    fun doesNotHideUnrelatedUniqueViolation() {
        paymentRepository.insert(newPayment(paymentId = "P-FIXED", clientReference = "ORDER-EXISTING"))
        val useCase = useCase(idGenerator = FixedIdGenerator("P-FIXED", "T-FIXED"))

        val thrown = assertThrows<DuplicateKeyException> { useCase.authorize(command(clientReference = "ORDER-NEW")) }

        val sqlException = generateSequence<Throwable>(thrown) { it.cause }.filterIsInstance<SQLException>().first()
        assertTrue(sqlException.message.orEmpty().contains("payment.PRIMARY"), sqlException.message)
        assertCounts(payments = 1, transactions = 0, idempotency = 0)
    }

    @Test
    fun refusesToJoinExistingTransaction() {
        assertThrows<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult { unitOfWork.execute { } }
        }
    }

    @Test
    fun createsSingleTransactionForConcurrentIdenticalRequests() {
        val results = runConcurrently { command() }

        val registered = results.filterIsInstance<AuthorizationIntakeResult.Registered>().single()
        val reused = results.filterIsInstance<AuthorizationIntakeResult.Reused>()
        assertEquals(CONCURRENT_REQUESTS - 1, reused.size)
        reused.forEach {
            assertEquals(registered.transaction.transactionId, it.transaction.transactionId)
            assertEquals(registered.payment.paymentId, it.payment.paymentId)
        }
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
    }

    @Test
    fun createsSingleTransactionAndBindsEveryKeyForConcurrentSameClientReference() {
        val results = runConcurrently { index -> command(idempotencyKey = "KEY-$index") }

        val registered = results.filterIsInstance<AuthorizationIntakeResult.Registered>().single()
        val reused = results.filterIsInstance<AuthorizationIntakeResult.Reused>()
        assertEquals(CONCURRENT_REQUESTS - 1, reused.size)
        reused.forEach { assertEquals(registered.transaction.transactionId, it.transaction.transactionId) }
        assertCounts(payments = 1, transactions = 1, idempotency = CONCURRENT_REQUESTS)
        assertEquals(
            List(CONCURRENT_REQUESTS) { "KEY-$it" to registered.transaction.transactionId.value },
            jdbcTemplate.queryForList("SELECT idempotency_key, transaction_id FROM idempotency_request")
                .map { it["IDEMPOTENCY_KEY"] as String to it["TRANSACTION_ID"] as String }
                .sortedBy { it.first },
        )
    }

    @Test
    fun rejectsConcurrentKeyReuseWithDifferentAmounts() {
        val results = runConcurrently { index -> command(amountMinor = 1_000L * (index + 1)) }

        val registered = results.filterIsInstance<AuthorizationIntakeResult.Registered>().single()
        assertEquals(
            List(CONCURRENT_REQUESTS - 1) {
                AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED)
            },
            results.filterIsInstance<AuthorizationIntakeResult.Conflict>(),
        )
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
        assertEquals(
            registered.transaction.amount.amountMinor,
            jdbcTemplate.queryForObject("SELECT amount_minor FROM financial_transaction", Long::class.java),
        )
    }

    @Test
    fun rollsBackLosersThatConflictOnIdempotencyKeyAfterInsertingPayment() {
        // client reference가 모두 달라 결제·금융거래 insert는 각자 성공하고 idempotency PK에서만 경쟁한다.
        val results = runConcurrently { index -> command(clientReference = "ORDER-$index") }

        val registered = results.filterIsInstance<AuthorizationIntakeResult.Registered>().single()
        assertEquals(
            List(CONCURRENT_REQUESTS - 1) {
                AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED)
            },
            results.filterIsInstance<AuthorizationIntakeResult.Conflict>(),
        )
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
        assertEquals(
            registered.payment.paymentId.value,
            jdbcTemplate.queryForObject("SELECT payment_id FROM payment", String::class.java),
        )
    }

    /**
     * 모든 요청이 선조회를 마친 뒤 barrier에서 모여 동시에 첫 DB transaction을 시작하게 한다.
     * 선조회가 비어 있는 상태에서 DB unique 제약만으로 경쟁을 판정하는 경로를 결정적으로 실행한다.
     * 경쟁에서 진 요청의 재판정 transaction은 barrier를 거치지 않는다.
     */
    private fun runConcurrently(commandOf: (Int) -> AuthorizePaymentCommand): List<AuthorizationIntakeResult> {
        val barrier = CyclicBarrier(CONCURRENT_REQUESTS)
        val passedBarrier = ThreadLocal.withInitial { false }
        val barrierUnitOfWork = object : UnitOfWork {
            override fun <T> execute(block: () -> T): T {
                if (!passedBarrier.get()) {
                    passedBarrier.set(true)
                    barrier.await(WAIT_SECONDS, TimeUnit.SECONDS)
                }
                return unitOfWork.execute(block)
            }
        }
        val useCase = useCase(unitOfWork = barrierUnitOfWork, idGenerator = SequentialIdGenerator())

        val futures = List(CONCURRENT_REQUESTS) { index ->
            executor.submit(Callable { useCase.authorize(commandOf(index)) })
        }
        return futures.map { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
    }

    private fun useCase(
        idempotency: IdempotencyRepository = idempotencyRepository,
        unitOfWork: UnitOfWork = this.unitOfWork,
        idGenerator: AuthorizationIdGenerator = SequentialIdGenerator(),
    ) = AuthorizePaymentUseCase(paymentRepository, transactionRepository, idempotency, unitOfWork, idGenerator)

    private fun assertCounts(payments: Int, transactions: Int, idempotency: Int) {
        assertEquals(payments, count("payment"), "payment rows")
        assertEquals(transactions, count("financial_transaction"), "financial_transaction rows")
        assertEquals(idempotency, count("idempotency_request"), "idempotency_request rows")
    }

    private fun count(table: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

    private fun snapshot(): List<List<Map<String, Any?>>> =
        listOf(
            "SELECT * FROM payment ORDER BY payment_id",
            "SELECT * FROM financial_transaction ORDER BY transaction_id",
            "SELECT * FROM idempotency_request ORDER BY merchant_id, operation_type, idempotency_key",
        ).map { sql -> jdbcTemplate.queryForList(sql).map { it.lowercaseKeys() } }

    private fun idempotencyRow(
        key: String,
        command: AuthorizePaymentCommand,
        transactionId: String,
    ): Map<String, Any?> =
        mapOf(
            "merchant_id" to command.merchantId.value,
            "operation_type" to "AUTHORIZE",
            "idempotency_key" to key,
            "request_hash" to AuthorizationRequestCanonicalForm.hash(command).value,
            "transaction_id" to transactionId,
        )

    private fun Map<String, Any?>.lowercaseKeys(): Map<String, Any?> = entries.associate { it.key.lowercase() to it.value }

    private fun command(
        clientReference: String = "ORDER-1",
        idempotencyKey: String = "KEY-1",
        amountMinor: Long = 10_000,
        currency: String = "KRW",
    ) = AuthorizePaymentCommand(
        MerchantId("M-1"),
        ClientReference(clientReference),
        IdempotencyKey(idempotencyKey),
        Money(amountMinor, CurrencyCode(currency)),
    )
}

private class SequentialIdGenerator : AuthorizationIdGenerator {
    private val payments = AtomicInteger()
    private val transactions = AtomicInteger()

    override fun newPaymentId(): PaymentId = PaymentId("P-${payments.incrementAndGet()}")

    override fun newTransactionId(): TransactionId = TransactionId("T-${transactions.incrementAndGet()}")
}

private class FixedIdGenerator(
    private val paymentId: String,
    private val transactionId: String,
) : AuthorizationIdGenerator {
    override fun newPaymentId(): PaymentId = PaymentId(paymentId)

    override fun newTransactionId(): TransactionId = TransactionId(transactionId)
}
