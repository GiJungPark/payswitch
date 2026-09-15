package io.github.gijungpark.payswitch.domain.transaction

import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

private val AUTHORIZATION_AMOUNT = Money(10_000, CurrencyCode.KRW)

/** 구현과 독립적으로 docs/domain-model.md의 금융거래 전이를 기록한 기대값. */
private val ALLOWED_TRANSITIONS = mapOf(
    FinancialTransactionStatus.RECEIVED to setOf(
        FinancialTransactionStatus.PROCESSING,
    ),
    FinancialTransactionStatus.PROCESSING to setOf(
        FinancialTransactionStatus.SUCCEEDED,
        FinancialTransactionStatus.DECLINED,
        FinancialTransactionStatus.FAILED,
        FinancialTransactionStatus.UNKNOWN,
    ),
    FinancialTransactionStatus.UNKNOWN to setOf(
        FinancialTransactionStatus.SUCCEEDED,
        FinancialTransactionStatus.DECLINED,
        FinancialTransactionStatus.FAILED,
        FinancialTransactionStatus.MANUAL_REVIEW_REQUIRED,
    ),
)

enum class TransactionAction(
    val target: FinancialTransactionStatus,
    private val action: (FinancialTransaction) -> Unit,
) {
    START_PROCESSING(FinancialTransactionStatus.PROCESSING, { it.startProcessing() }),
    SUCCEED(FinancialTransactionStatus.SUCCEEDED, { it.succeed() }),
    DECLINE(FinancialTransactionStatus.DECLINED, { it.decline() }),
    FAIL(FinancialTransactionStatus.FAILED, { it.fail() }),
    MARK_UNKNOWN(FinancialTransactionStatus.UNKNOWN, { it.markUnknown() }),
    REQUIRE_MANUAL_REVIEW(FinancialTransactionStatus.MANUAL_REVIEW_REQUIRED, { it.requireManualReview() }),
    ;

    fun applyTo(transaction: FinancialTransaction) = action(transaction)
}

class FinancialTransactionTest {

    @Test
    fun startsAsReceivedAuthorizationWithoutOriginalTransactionAndVersionZero() {
        val transaction = newAuthorization()

        assertEquals(TransactionId("T-1"), transaction.transactionId)
        assertEquals(PaymentId("P-1"), transaction.paymentId)
        assertEquals(FinancialTransactionType.AUTHORIZE, transaction.type)
        assertNull(transaction.originalTransactionId)
        assertEquals(AUTHORIZATION_AMOUNT, transaction.amount)
        assertEquals(FinancialTransactionStatus.RECEIVED, transaction.status)
        assertEquals(0L, transaction.version)
    }

    @ParameterizedTest
    @ValueSource(longs = [1, Long.MAX_VALUE])
    fun createsAuthorizationForPositiveKrwAmount(amountMinor: Long) {
        val amount = Money(amountMinor, CurrencyCode.KRW)

        val transaction = FinancialTransaction.createAuthorization(TransactionId("T-1"), PaymentId("P-1"), amount)

        assertEquals(amount, transaction.amount)
        assertEquals(FinancialTransactionStatus.RECEIVED, transaction.status)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAuthorizationAmounts")
    fun rejectsAuthorizationAmountThatIsNotPositiveKrw(amount: Money) {
        assertThrows<IllegalArgumentException> {
            FinancialTransaction.createAuthorization(TransactionId("T-1"), PaymentId("P-1"), amount)
        }
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("allowedTransitions")
    fun appliesAllowedTransitionAndIncrementsVersion(from: FinancialTransactionStatus, action: TransactionAction) {
        val transaction = transactionIn(from)
        val versionBefore = transaction.version

        action.applyTo(transaction)

        assertEquals(action.target, transaction.status)
        assertEquals(versionBefore + 1, transaction.version)
        assertEquals(AUTHORIZATION_AMOUNT, transaction.amount)
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("rejectedTransitions")
    fun rejectsDisallowedTransitionWithoutChangingState(from: FinancialTransactionStatus, action: TransactionAction) {
        val transaction = transactionIn(from)
        val versionBefore = transaction.version

        assertThrows<IllegalStateException> { action.applyTo(transaction) }

        assertEquals(from, transaction.status)
        assertEquals(versionBefore, transaction.version)
        assertEquals(AUTHORIZATION_AMOUNT, transaction.amount)
    }

    @ParameterizedTest(name = "repeated {0}")
    @MethodSource("repeatedTransitions")
    fun ignoresRepeatedSameResult(action: TransactionAction) {
        val transaction = transactionIn(action.target)
        val versionBefore = transaction.version

        action.applyTo(transaction)

        assertEquals(action.target, transaction.status)
        assertEquals(versionBefore, transaction.version)
    }

    @Test
    fun resolvesUnknownOnceAndRejectsConflictingLateResult() {
        val transaction = newAuthorization()

        transaction.startProcessing()
        transaction.markUnknown()
        transaction.succeed()
        transaction.succeed()

        assertEquals(FinancialTransactionStatus.SUCCEEDED, transaction.status)
        assertEquals(3L, transaction.version)

        assertThrows<IllegalStateException> { transaction.decline() }

        assertEquals(FinancialTransactionStatus.SUCCEEDED, transaction.status)
        assertEquals(3L, transaction.version)
    }

    @Test
    fun exposesNoPublicSetters() {
        assertEquals(
            emptyList<String>(),
            FinancialTransaction::class.java.methods.map { it.name }.filter { it.startsWith("set") },
        )
    }

    private fun newAuthorization(): FinancialTransaction =
        FinancialTransaction.createAuthorization(TransactionId("T-1"), PaymentId("P-1"), AUTHORIZATION_AMOUNT)

    private fun transactionIn(status: FinancialTransactionStatus): FinancialTransaction {
        val transaction = newAuthorization()
        when (status) {
            FinancialTransactionStatus.RECEIVED -> Unit
            FinancialTransactionStatus.PROCESSING -> transaction.startProcessing()
            FinancialTransactionStatus.SUCCEEDED -> {
                transaction.startProcessing()
                transaction.succeed()
            }
            FinancialTransactionStatus.DECLINED -> {
                transaction.startProcessing()
                transaction.decline()
            }
            FinancialTransactionStatus.FAILED -> {
                transaction.startProcessing()
                transaction.fail()
            }
            FinancialTransactionStatus.UNKNOWN -> {
                transaction.startProcessing()
                transaction.markUnknown()
            }
            FinancialTransactionStatus.MANUAL_REVIEW_REQUIRED -> {
                transaction.startProcessing()
                transaction.markUnknown()
                transaction.requireManualReview()
            }
        }
        assertEquals(status, transaction.status)
        return transaction
    }

    companion object {

        @JvmStatic
        fun allowedTransitions(): List<Arguments> =
            FinancialTransactionStatus.entries.flatMap { from ->
                TransactionAction.entries
                    .filter { it.target in ALLOWED_TRANSITIONS[from].orEmpty() }
                    .map { Arguments.of(from, it) }
            }

        @JvmStatic
        fun rejectedTransitions(): List<Arguments> =
            FinancialTransactionStatus.entries.flatMap { from ->
                TransactionAction.entries
                    .filter { it.target != from && it.target !in ALLOWED_TRANSITIONS[from].orEmpty() }
                    .map { Arguments.of(from, it) }
            }

        @JvmStatic
        fun repeatedTransitions(): List<TransactionAction> = TransactionAction.entries

        @JvmStatic
        fun invalidAuthorizationAmounts(): List<Money> =
            listOf(
                Money(0, CurrencyCode.KRW),
                Money(10_000, CurrencyCode("USD")),
                Money(0, CurrencyCode("USD")),
            )
    }
}
