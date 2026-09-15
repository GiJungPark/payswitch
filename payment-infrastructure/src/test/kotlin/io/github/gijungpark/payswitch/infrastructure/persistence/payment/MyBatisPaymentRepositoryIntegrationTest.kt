package io.github.gijungpark.payswitch.infrastructure.persistence.payment

import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.payment.Payment
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.infrastructure.persistence.MySqlIntegrationTest
import io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException

class MyBatisPaymentRepositoryIntegrationTest : MySqlIntegrationTest() {

    @Autowired
    private lateinit var repository: PaymentRepository

    @ParameterizedTest
    @EnumSource(ReachablePayment::class)
    fun findsSavedPaymentByIdWithSameState(state: ReachablePayment) {
        val payment = state.create()

        repository.insert(payment)

        assertSameState(payment, repository.findById(payment.paymentId))
    }

    @ParameterizedTest
    @EnumSource(ReachablePayment::class)
    fun findsSavedPaymentByMerchantAndClientReference(state: ReachablePayment) {
        val payment = state.create()

        repository.insert(payment)

        assertSameState(
            payment,
            repository.findByMerchantIdAndClientReference(payment.merchantId, payment.clientReference),
        )
    }

    @Test
    fun keepsMaximumMinorUnitAmount() {
        val payment = newPayment().apply { approve(Money(Long.MAX_VALUE, CurrencyCode.KRW)) }

        repository.insert(payment)

        assertSameState(payment, repository.findById(payment.paymentId))
    }

    @Test
    fun returnsNullWhenPaymentDoesNotExist() {
        repository.insert(newPayment())

        assertNull(repository.findById(PaymentId("P-MISSING")))
        assertNull(repository.findByMerchantIdAndClientReference(MerchantId("M-1"), ClientReference("ORDER-MISSING")))
        assertNull(repository.findByMerchantIdAndClientReference(MerchantId("M-2"), ClientReference("ORDER-1")))
    }

    @Test
    fun rejectsDuplicateMerchantClientReferenceInsert() {
        repository.insert(newPayment(paymentId = "P-1"))

        assertThrows<DuplicateKeyException> { repository.insert(newPayment(paymentId = "P-2")) }
        val stored = repository.findByMerchantIdAndClientReference(MerchantId("M-1"), ClientReference("ORDER-1"))
        assertEquals(PaymentId("P-1"), stored?.paymentId)
    }

    @Test
    fun rejectsDuplicatePaymentIdInsert() {
        repository.insert(newPayment(clientReference = "ORDER-1"))

        assertThrows<DuplicateKeyException> { repository.insert(newPayment(clientReference = "ORDER-2")) }
    }

    @Test
    fun refusesToRestoreRowOutsideReachableDomainState() {
        jdbcTemplate.update(
            """
            INSERT INTO payment (payment_id, merchant_id, client_reference, approved_amount_minor, cancelled_amount_minor,
                                 reserved_cancel_amount_minor, transaction_currency, status, version)
            VALUES ('P-1', 'M-1', 'ORDER-1', 10000, 0, 0, 'KRW', 'CREATED', 7)
            """.trimIndent(),
        )

        assertThrows<PersistedStateRestoreException> { repository.findById(PaymentId("P-1")) }
        assertThrows<PersistedStateRestoreException> {
            repository.findByMerchantIdAndClientReference(MerchantId("M-1"), ClientReference("ORDER-1"))
        }
    }

    private fun assertSameState(expected: Payment, actual: Payment?) {
        assertNotNull(actual)
        assertEquals(PaymentRow.from(expected), PaymentRow.from(actual!!))
        assertEquals(expected.paymentId, actual.paymentId)
        assertEquals(expected.approvedAmount, actual.approvedAmount)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.version, actual.version)
    }
}

internal fun newPayment(
    paymentId: String = "P-1",
    merchantId: String = "M-1",
    clientReference: String = "ORDER-1",
): Payment = Payment.createKrwAuthorization(PaymentId(paymentId), MerchantId(merchantId), ClientReference(clientReference))

private val APPROVED_AMOUNT = Money(10_000, CurrencyCode.KRW)

/** domain 공개 method로만 만들 수 있는 Payment 상태 전체. */
enum class ReachablePayment(private val transitions: (Payment) -> Unit) {
    CREATED({}),
    APPROVED({ it.approve(APPROVED_AMOUNT) }),
    DECLINED({ it.decline() }),
    FAILED({ it.fail() }),
    RESOLUTION_REQUIRED({ it.requireResolution() }),
    RESOLVED_APPROVED({ it.requireResolution(); it.approve(APPROVED_AMOUNT) }),
    RESOLVED_DECLINED({ it.requireResolution(); it.decline() }),
    RESOLVED_FAILED({ it.requireResolution(); it.fail() }),
    MANUAL_REVIEW_REQUIRED({ it.requireResolution(); it.requireManualReview() }),
    ;

    fun create(): Payment = newPayment().also(transitions)
}
