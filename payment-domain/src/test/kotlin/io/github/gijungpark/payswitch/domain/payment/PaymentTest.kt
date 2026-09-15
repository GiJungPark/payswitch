package io.github.gijungpark.payswitch.domain.payment

import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private val KRW_ZERO = Money(0, CurrencyCode.KRW)
private val APPROVED_AMOUNT = Money(10_000, CurrencyCode.KRW)

/** 구현과 독립적으로 docs/domain-model.md의 승인 결과 전이를 기록한 기대값. */
private val ALLOWED_TRANSITIONS = mapOf(
    PaymentStatus.CREATED to setOf(
        PaymentStatus.APPROVED,
        PaymentStatus.DECLINED,
        PaymentStatus.FAILED,
        PaymentStatus.RESOLUTION_REQUIRED,
    ),
    PaymentStatus.RESOLUTION_REQUIRED to setOf(
        PaymentStatus.APPROVED,
        PaymentStatus.DECLINED,
        PaymentStatus.FAILED,
        PaymentStatus.MANUAL_REVIEW_REQUIRED,
    ),
)

enum class PaymentAction(val target: PaymentStatus, private val action: (Payment) -> Unit) {
    APPROVE(PaymentStatus.APPROVED, { it.approve(APPROVED_AMOUNT) }),
    DECLINE(PaymentStatus.DECLINED, { it.decline() }),
    FAIL(PaymentStatus.FAILED, { it.fail() }),
    REQUIRE_RESOLUTION(PaymentStatus.RESOLUTION_REQUIRED, { it.requireResolution() }),
    REQUIRE_MANUAL_REVIEW(PaymentStatus.MANUAL_REVIEW_REQUIRED, { it.requireManualReview() }),
    ;

    fun applyTo(payment: Payment) = action(payment)
}

class PaymentTest {

    @Test
    fun startsAsCreatedKrwPaymentWithZeroAmountsAndVersionZero() {
        val payment = newPayment()

        assertEquals(PaymentId("P-1"), payment.paymentId)
        assertEquals(MerchantId("M-1"), payment.merchantId)
        assertEquals(ClientReference("ORDER-1"), payment.clientReference)
        assertEquals(CurrencyCode.KRW, payment.transactionCurrency)
        assertEquals(PaymentStatus.CREATED, payment.status)
        assertEquals(KRW_ZERO, payment.approvedAmount)
        assertEquals(KRW_ZERO, payment.cancelledAmount)
        assertEquals(KRW_ZERO, payment.reservedCancelAmount)
        assertEquals(0L, payment.version)
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("allowedTransitions")
    fun appliesAllowedTransitionAndIncrementsVersion(from: PaymentStatus, action: PaymentAction) {
        val payment = paymentIn(from)
        val versionBefore = payment.version

        action.applyTo(payment)

        assertEquals(action.target, payment.status)
        assertEquals(versionBefore + 1, payment.version)
        val expectedApprovedAmount = if (action.target == PaymentStatus.APPROVED) APPROVED_AMOUNT else KRW_ZERO
        assertEquals(expectedApprovedAmount, payment.approvedAmount)
        assertEquals(KRW_ZERO, payment.cancelledAmount)
        assertEquals(KRW_ZERO, payment.reservedCancelAmount)
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("rejectedTransitions")
    fun rejectsDisallowedTransitionWithoutChangingState(from: PaymentStatus, action: PaymentAction) {
        val payment = paymentIn(from)
        val approvedAmountBefore = payment.approvedAmount
        val versionBefore = payment.version

        assertThrows<IllegalStateException> { action.applyTo(payment) }

        assertEquals(from, payment.status)
        assertEquals(approvedAmountBefore, payment.approvedAmount)
        assertEquals(versionBefore, payment.version)
    }

    @ParameterizedTest(name = "repeated {0}")
    @MethodSource("repeatedTransitions")
    fun ignoresRepeatedSameResult(action: PaymentAction) {
        val payment = paymentIn(action.target)
        val approvedAmountBefore = payment.approvedAmount
        val versionBefore = payment.version

        action.applyTo(payment)

        assertEquals(action.target, payment.status)
        assertEquals(approvedAmountBefore, payment.approvedAmount)
        assertEquals(versionBefore, payment.version)
    }

    @Test
    fun rejectsApprovalWithDifferentAmountAfterApproval() {
        val payment = paymentIn(PaymentStatus.APPROVED)

        assertThrows<IllegalStateException> { payment.approve(Money(9_999, CurrencyCode.KRW)) }

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(APPROVED_AMOUNT, payment.approvedAmount)
        assertEquals(1L, payment.version)
    }

    @ParameterizedTest(name = "{1} from {0}")
    @MethodSource("invalidApprovalAmounts")
    fun rejectsApprovalAmountThatIsNotPositiveKrw(from: PaymentStatus, amount: Money) {
        val payment = paymentIn(from)
        val approvedAmountBefore = payment.approvedAmount
        val versionBefore = payment.version

        assertThrows<IllegalArgumentException> { payment.approve(amount) }

        assertEquals(from, payment.status)
        assertEquals(approvedAmountBefore, payment.approvedAmount)
        assertEquals(versionBefore, payment.version)
    }

    @Test
    fun approvesMaximumKrwAmount() {
        val payment = newPayment()
        val maximum = Money(Long.MAX_VALUE, CurrencyCode.KRW)

        payment.approve(maximum)

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(maximum, payment.approvedAmount)
        assertEquals(1L, payment.version)
    }

    @Test
    fun resolvesToApprovalOnceAndIgnoresDuplicateSameAmount() {
        val payment = newPayment()

        payment.requireResolution()
        payment.approve(APPROVED_AMOUNT)
        payment.approve(Money(10_000, CurrencyCode.KRW))

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(APPROVED_AMOUNT, payment.approvedAmount)
        assertEquals(2L, payment.version)
    }

    @Test
    fun exposesNoPublicSetters() {
        assertEquals(emptyList<String>(), Payment::class.java.methods.map { it.name }.filter { it.startsWith("set") })
    }

    private fun newPayment(): Payment =
        Payment.createKrwAuthorization(PaymentId("P-1"), MerchantId("M-1"), ClientReference("ORDER-1"))

    private fun paymentIn(status: PaymentStatus): Payment {
        val payment = newPayment()
        when (status) {
            PaymentStatus.CREATED -> Unit
            PaymentStatus.APPROVED -> payment.approve(APPROVED_AMOUNT)
            PaymentStatus.DECLINED -> payment.decline()
            PaymentStatus.FAILED -> payment.fail()
            PaymentStatus.RESOLUTION_REQUIRED -> payment.requireResolution()
            PaymentStatus.MANUAL_REVIEW_REQUIRED -> {
                payment.requireResolution()
                payment.requireManualReview()
            }
        }
        assertEquals(status, payment.status)
        return payment
    }

    companion object {

        @JvmStatic
        fun allowedTransitions(): List<Arguments> =
            PaymentStatus.entries.flatMap { from ->
                PaymentAction.entries
                    .filter { it.target in ALLOWED_TRANSITIONS[from].orEmpty() }
                    .map { Arguments.of(from, it) }
            }

        @JvmStatic
        fun rejectedTransitions(): List<Arguments> =
            PaymentStatus.entries.flatMap { from ->
                PaymentAction.entries
                    .filter { it.target != from && it.target !in ALLOWED_TRANSITIONS[from].orEmpty() }
                    .map { Arguments.of(from, it) }
            }

        @JvmStatic
        fun repeatedTransitions(): List<PaymentAction> = PaymentAction.entries

        @JvmStatic
        fun invalidApprovalAmounts(): List<Arguments> =
            listOf(PaymentStatus.CREATED, PaymentStatus.RESOLUTION_REQUIRED, PaymentStatus.APPROVED).flatMap { from ->
                listOf(
                    KRW_ZERO,
                    Money(10_000, CurrencyCode("USD")),
                    Money(0, CurrencyCode("USD")),
                ).map { Arguments.of(from, it) }
            }
    }
}
