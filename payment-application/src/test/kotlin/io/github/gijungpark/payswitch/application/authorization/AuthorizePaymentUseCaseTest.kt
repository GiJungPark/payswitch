package io.github.gijungpark.payswitch.application.authorization

import io.github.gijungpark.payswitch.application.idempotency.IdempotencyKey
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRecord
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRepository
import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.application.persistence.IdempotencyUniqueConstraint
import io.github.gijungpark.payswitch.application.persistence.UniqueConstraintViolationException
import io.github.gijungpark.payswitch.application.persistence.UnitOfWork
import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.payment.Payment
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.payment.PaymentStatus
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransaction
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionStatus
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 저장소 port의 in-memory fake로 use case 판정 규칙을 검증한다. DB 경쟁과 transaction은 MySQL 통합 테스트가 검증한다.
 */
class AuthorizePaymentUseCaseTest {

    private val store = InMemoryStore()
    private val idGenerator = SequentialIdGenerator()
    private val useCase = AuthorizePaymentUseCase(
        store.payments,
        store.transactions,
        store.idempotency,
        store.unitOfWork,
        idGenerator,
    )

    @Test
    fun registersPaymentTransactionAndIdempotencyRecordForFirstRequest() {
        val result = useCase.authorize(command())

        val registered = assertInstanceOf(AuthorizationIntakeResult.Registered::class.java, result)
        assertEquals(PaymentId("P-1"), registered.payment.paymentId)
        assertEquals(PaymentStatus.CREATED, registered.payment.status)
        assertEquals(0L, registered.payment.version)
        assertEquals(TransactionId("T-1"), registered.transaction.transactionId)
        assertEquals(PaymentId("P-1"), registered.transaction.paymentId)
        assertEquals(FinancialTransactionType.AUTHORIZE, registered.transaction.type)
        assertEquals(FinancialTransactionStatus.RECEIVED, registered.transaction.status)
        assertEquals(Money(10_000, CurrencyCode.KRW), registered.transaction.amount)
        assertEquals(
            IdempotencyRecord(
                MerchantId("M-1"),
                FinancialTransactionType.AUTHORIZE,
                IdempotencyKey("KEY-1"),
                AuthorizationRequestCanonicalForm.hash(command()),
                TransactionId("T-1"),
            ),
            store.idempotencyRows.single(),
        )
        assertEquals(1, store.paymentRows.size)
        assertEquals(1, store.transactionRows.size)
    }

    @Test
    fun reusesExistingTransactionForSameKeyAndSameRequest() {
        useCase.authorize(command())

        val result = useCase.authorize(command())

        val reused = assertInstanceOf(AuthorizationIntakeResult.Reused::class.java, result)
        assertEquals(PaymentId("P-1"), reused.payment.paymentId)
        assertEquals(TransactionId("T-1"), reused.transaction.transactionId)
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
        assertEquals(1, idGenerator.issuedPayments)
    }

    @Test
    fun returnsCurrentStateOfReusedTransaction() {
        useCase.authorize(command())
        store.transactionRows.single().startProcessing()

        val reused = useCase.authorize(command()) as AuthorizationIntakeResult.Reused

        assertEquals(FinancialTransactionStatus.PROCESSING, reused.transaction.status)
        assertEquals(1L, reused.transaction.version)
    }

    @Test
    fun rejectsSameKeyWithDifferentRequestWithoutChangingExistingData() {
        useCase.authorize(command())

        val differentAmount = useCase.authorize(command(amountMinor = 20_000))
        val differentClientReference = useCase.authorize(command(clientReference = "ORDER-2"))
        val differentCurrency = useCase.authorize(command(currency = "USD"))

        listOf(differentAmount, differentClientReference, differentCurrency).forEach {
            assertEquals(
                AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED),
                it,
            )
        }
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
        assertOriginalUnchanged()
    }

    @Test
    fun reusesExistingTransactionAndBindsNewKeyForSameClientReferenceAndSameContent() {
        useCase.authorize(command(idempotencyKey = "KEY-1"))

        val result = useCase.authorize(command(idempotencyKey = "KEY-2"))

        val reused = assertInstanceOf(AuthorizationIntakeResult.Reused::class.java, result)
        assertEquals(TransactionId("T-1"), reused.transaction.transactionId)
        assertEquals(PaymentId("P-1"), reused.payment.paymentId)
        assertCounts(payments = 1, transactions = 1, idempotency = 2)
        assertEquals(
            IdempotencyRecord(
                MerchantId("M-1"),
                FinancialTransactionType.AUTHORIZE,
                IdempotencyKey("KEY-2"),
                AuthorizationRequestCanonicalForm.hash(command(idempotencyKey = "KEY-2")),
                TransactionId("T-1"),
            ),
            store.idempotencyRows.single { it.idempotencyKey == IdempotencyKey("KEY-2") },
        )
        assertEquals(1, idGenerator.issuedPayments)
    }

    @Test
    fun treatsKeyBoundByReuseAsConsumedForLaterDifferentRequests() {
        useCase.authorize(command(idempotencyKey = "KEY-1"))
        useCase.authorize(command(idempotencyKey = "KEY-2"))

        val results = listOf(
            useCase.authorize(command(idempotencyKey = "KEY-2", clientReference = "ORDER-2")),
            useCase.authorize(command(idempotencyKey = "KEY-2", amountMinor = 20_000)),
            useCase.authorize(command(idempotencyKey = "KEY-2", currency = "USD")),
        )

        results.forEach {
            assertEquals(AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED), it)
        }
        assertCounts(payments = 1, transactions = 1, idempotency = 2)
        assertEquals(
            listOf(TransactionId("T-1"), TransactionId("T-1")),
            store.idempotencyRows.map { it.transactionId },
        )
        assertEquals(
            AuthorizationIntakeResult.Reused::class.java,
            useCase.authorize(command(idempotencyKey = "KEY-2")).javaClass,
        )
    }

    @Test
    fun rollsBackNewKeyWhenReuseBindingFails() {
        useCase.authorize(command(idempotencyKey = "KEY-1"))
        store.failAfterIdempotencyInsert = IllegalStateException("injected failure")

        val thrown = assertThrows<IllegalStateException> { useCase.authorize(command(idempotencyKey = "KEY-2")) }

        assertEquals("injected failure", thrown.message)
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
        assertEquals(IdempotencyKey("KEY-1"), store.idempotencyRows.single().idempotencyKey)
    }

    @Test
    fun rejectsDifferentKeyWithSameClientReferenceAndDifferentAmountOrCurrency() {
        useCase.authorize(command(idempotencyKey = "KEY-1"))

        val differentAmount = useCase.authorize(command(idempotencyKey = "KEY-2", amountMinor = 10_001))
        val differentCurrency = useCase.authorize(command(idempotencyKey = "KEY-3", currency = "USD"))

        listOf(differentAmount, differentCurrency).forEach {
            assertEquals(
                AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.CLIENT_REFERENCE_CONFLICT),
                it,
            )
        }
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
        assertOriginalUnchanged()
    }

    @Test
    fun treatsSameClientReferenceOfAnotherMerchantAsNewRequest() {
        useCase.authorize(command(merchantId = "M-1"))

        val result = useCase.authorize(command(merchantId = "M-2"))

        assertInstanceOf(AuthorizationIntakeResult.Registered::class.java, result)
        assertCounts(payments = 2, transactions = 2, idempotency = 2)
    }

    @Test
    fun rejectsUnsupportedCurrencyForNewRequestWithoutWriting() {
        assertThrows<IllegalArgumentException> { useCase.authorize(command(currency = "USD")) }

        assertCounts(payments = 0, transactions = 0, idempotency = 0)
        assertEquals(0, store.unitOfWorkCalls)
    }

    @Test
    fun rejectsInvalidCommandBeforeRepositoryAccess() {
        assertThrows<IllegalArgumentException> { command(idempotencyKey = " ") }
        assertThrows<IllegalArgumentException> { command(idempotencyKey = "k".repeat(129)) }
        assertThrows<IllegalArgumentException> { command(amountMinor = 0) }
        assertEquals(0, store.reads)
    }

    @Test
    fun resolvesLostUniqueRaceFromCommittedIdempotencyRecord() {
        // 선조회는 비어 있었지만 insert 직전에 다른 요청이 같은 요청을 commit한 상황.
        store.beforeNextUnitOfWork = { commitCompetingRequest(command()) }

        val result = useCase.authorize(command())

        val reused = assertInstanceOf(AuthorizationIntakeResult.Reused::class.java, result)
        assertEquals(TransactionId("T-OTHER"), reused.transaction.transactionId)
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
    }

    @Test
    fun resolvesLostKeyRaceWithDifferentRequestAsKeyReuse() {
        store.beforeNextUnitOfWork = { commitCompetingRequest(command(clientReference = "ORDER-OTHER")) }

        val result = useCase.authorize(command())

        assertEquals(AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED), result)
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
        assertEquals(ClientReference("ORDER-OTHER"), store.paymentRows.single().clientReference)
    }

    @Test
    fun resolvesLostClientReferenceRaceAsConflictWhenAmountDiffers() {
        store.beforeNextUnitOfWork = {
            commitCompetingRequest(command(idempotencyKey = "KEY-OTHER", amountMinor = 5_000))
        }

        val result = useCase.authorize(command())

        assertEquals(
            AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.CLIENT_REFERENCE_CONFLICT),
            result,
        )
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
    }

    @Test
    fun resolvesLostClientReferenceRaceByBindingKeyWhenContentIsSame() {
        store.beforeNextUnitOfWork = { commitCompetingRequest(command(idempotencyKey = "KEY-OTHER")) }

        val result = useCase.authorize(command())

        val reused = assertInstanceOf(AuthorizationIntakeResult.Reused::class.java, result)
        assertEquals(TransactionId("T-OTHER"), reused.transaction.transactionId)
        assertCounts(payments = 1, transactions = 1, idempotency = 2)
        assertEquals(
            TransactionId("T-OTHER"),
            store.idempotencyRows.single { it.idempotencyKey == IdempotencyKey("KEY-1") }.transactionId,
        )
    }

    @Test
    fun resolvesLostKeyBindingRaceFromCommittedRecord() {
        useCase.authorize(command(idempotencyKey = "KEY-1"))
        // 기존 거래에 KEY-2를 결합하기 직전에 다른 요청이 KEY-2를 다른 내용으로 commit한 상황.
        store.beforeNextUnitOfWork = {
            commitCompetingRequest(command(idempotencyKey = "KEY-2", clientReference = "ORDER-OTHER"))
        }

        val result = useCase.authorize(command(idempotencyKey = "KEY-2"))

        assertEquals(AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED), result)
        assertCounts(payments = 2, transactions = 2, idempotency = 2)
        assertEquals(
            TransactionId("T-OTHER"),
            store.idempotencyRows.single { it.idempotencyKey == IdempotencyKey("KEY-2") }.transactionId,
        )
    }

    @Test
    fun propagatesUnclassifiedFailureAndRollsBackAllRows() {
        store.failAfterIdempotencyInsert = IllegalStateException("injected failure")

        val thrown = assertThrows<IllegalStateException> { useCase.authorize(command()) }

        assertEquals("injected failure", thrown.message)
        assertCounts(payments = 0, transactions = 0, idempotency = 0)
    }

    @Test
    fun retriesRegistrationWhenConflictLeavesNoCommittedRow() {
        store.beforeNextUnitOfWork = {
            throw UniqueConstraintViolationException(IdempotencyUniqueConstraint.IDEMPOTENCY_KEY)
        }

        val result = useCase.authorize(command())

        assertInstanceOf(AuthorizationIntakeResult.Registered::class.java, result)
        assertEquals(2, store.unitOfWorkCalls)
        assertCounts(payments = 1, transactions = 1, idempotency = 1)
    }

    @Test
    fun failsExplicitlyWhenConflictsNeverResolve() {
        store.conflictOnEveryUnitOfWork = true

        val thrown = assertThrows<IllegalStateException> { useCase.authorize(command()) }

        assertInstanceOf(UniqueConstraintViolationException::class.java, thrown.cause)
        assertEquals(3, store.unitOfWorkCalls)
        assertCounts(payments = 0, transactions = 0, idempotency = 0)
    }

    private fun commitCompetingRequest(competing: AuthorizePaymentCommand) {
        val payment = Payment.createKrwAuthorization(
            PaymentId("P-OTHER"),
            competing.merchantId,
            competing.clientReference,
        )
        val transaction = FinancialTransaction.createAuthorization(
            TransactionId("T-OTHER"),
            payment.paymentId,
            competing.amount,
        )
        store.paymentRows += payment
        store.transactionRows += transaction
        store.idempotencyRows += IdempotencyRecord(
            competing.merchantId,
            FinancialTransactionType.AUTHORIZE,
            competing.idempotencyKey,
            AuthorizationRequestCanonicalForm.hash(competing),
            transaction.transactionId,
        )
    }

    private fun assertCounts(payments: Int, transactions: Int, idempotency: Int) {
        assertEquals(payments, store.paymentRows.size, "payment rows")
        assertEquals(transactions, store.transactionRows.size, "transaction rows")
        assertEquals(idempotency, store.idempotencyRows.size, "idempotency rows")
    }

    private fun assertOriginalUnchanged() {
        val payment = store.paymentRows.single()
        assertEquals(PaymentStatus.CREATED, payment.status)
        assertEquals(0L, payment.version)
        assertEquals(Money.zero(CurrencyCode.KRW), payment.approvedAmount)
        val transaction = store.transactionRows.single()
        assertEquals(FinancialTransactionStatus.RECEIVED, transaction.status)
        assertEquals(0L, transaction.version)
        assertEquals(Money(10_000, CurrencyCode.KRW), transaction.amount)
        assertEquals(
            AuthorizationRequestCanonicalForm.hash(command()),
            store.idempotencyRows.single().requestHash,
        )
    }

    private fun command(
        merchantId: String = "M-1",
        clientReference: String = "ORDER-1",
        idempotencyKey: String = "KEY-1",
        amountMinor: Long = 10_000,
        currency: String = "KRW",
    ) = AuthorizePaymentCommand(
        MerchantId(merchantId),
        ClientReference(clientReference),
        IdempotencyKey(idempotencyKey),
        Money(amountMinor, CurrencyCode(currency)),
    )
}

private class SequentialIdGenerator : AuthorizationIdGenerator {
    var issuedPayments = 0
        private set
    private var issuedTransactions = 0

    override fun newPaymentId(): PaymentId = PaymentId("P-${++issuedPayments}")

    override fun newTransactionId(): TransactionId = TransactionId("T-${++issuedTransactions}")
}

/**
 * 세 저장소의 row와 unique 제약, transaction rollback을 흉내 내는 in-memory 저장소.
 */
private class InMemoryStore {
    val paymentRows = mutableListOf<Payment>()
    val transactionRows = mutableListOf<FinancialTransaction>()
    val idempotencyRows = mutableListOf<IdempotencyRecord>()

    var reads = 0
        private set
    var unitOfWorkCalls = 0
        private set
    var beforeNextUnitOfWork: (() -> Unit)? = null
    var failAfterIdempotencyInsert: RuntimeException? = null
    var conflictOnEveryUnitOfWork = false

    val payments = object : PaymentRepository {
        override fun insert(payment: Payment) {
            if (paymentRows.any {
                    it.merchantId == payment.merchantId && it.clientReference == payment.clientReference
                }
            ) {
                throw UniqueConstraintViolationException(IdempotencyUniqueConstraint.MERCHANT_CLIENT_REFERENCE)
            }
            check(paymentRows.none { it.paymentId == payment.paymentId })
            paymentRows += payment
        }

        override fun findById(paymentId: PaymentId): Payment? {
            reads++
            return paymentRows.find { it.paymentId == paymentId }
        }

        override fun findByMerchantIdAndClientReference(
            merchantId: MerchantId,
            clientReference: ClientReference,
        ): Payment? {
            reads++
            return paymentRows.find { it.merchantId == merchantId && it.clientReference == clientReference }
        }
    }

    val transactions = object : FinancialTransactionRepository {
        override fun insert(transaction: FinancialTransaction) {
            check(transactionRows.none { it.transactionId == transaction.transactionId })
            check(paymentRows.any { it.paymentId == transaction.paymentId })
            transactionRows += transaction
        }

        override fun findById(transactionId: TransactionId): FinancialTransaction? {
            reads++
            return transactionRows.find { it.transactionId == transactionId }
        }

        override fun findByPaymentId(paymentId: PaymentId): List<FinancialTransaction> {
            reads++
            return transactionRows.filter { it.paymentId == paymentId }.sortedBy { it.transactionId.value }
        }
    }

    val idempotency = object : IdempotencyRepository {
        override fun insert(record: IdempotencyRecord) {
            if (idempotencyRows.any {
                    it.merchantId == record.merchantId &&
                        it.operationType == record.operationType &&
                        it.idempotencyKey == record.idempotencyKey
                }
            ) {
                throw UniqueConstraintViolationException(IdempotencyUniqueConstraint.IDEMPOTENCY_KEY)
            }
            check(transactionRows.any { it.transactionId == record.transactionId })
            idempotencyRows += record
            failAfterIdempotencyInsert?.let { throw it }
        }

        override fun find(
            merchantId: MerchantId,
            operationType: FinancialTransactionType,
            idempotencyKey: IdempotencyKey,
        ): IdempotencyRecord? {
            reads++
            return idempotencyRows.find {
                it.merchantId == merchantId && it.operationType == operationType && it.idempotencyKey == idempotencyKey
            }
        }
    }

    val unitOfWork = object : UnitOfWork {
        override fun <T> execute(block: () -> T): T {
            unitOfWorkCalls++
            if (conflictOnEveryUnitOfWork) {
                throw UniqueConstraintViolationException(IdempotencyUniqueConstraint.MERCHANT_CLIENT_REFERENCE)
            }
            beforeNextUnitOfWork?.let { hook ->
                beforeNextUnitOfWork = null
                hook()
            }
            val paymentSnapshot = paymentRows.toList()
            val transactionSnapshot = transactionRows.toList()
            val idempotencySnapshot = idempotencyRows.toList()
            try {
                return block()
            } catch (e: RuntimeException) {
                paymentRows.apply { clear(); addAll(paymentSnapshot) }
                transactionRows.apply { clear(); addAll(transactionSnapshot) }
                idempotencyRows.apply { clear(); addAll(idempotencySnapshot) }
                throw e
            }
        }
    }
}
