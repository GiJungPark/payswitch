package io.github.gijungpark.payswitch.infrastructure.persistence.payment

import io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

private val APPROVED_ROW = PaymentRow(
    paymentId = "P-1",
    merchantId = "M-1",
    clientReference = "ORDER-1",
    approvedAmountMinor = 10_000,
    cancelledAmountMinor = 0,
    reservedCancelAmountMinor = 0,
    transactionCurrency = "KRW",
    status = "APPROVED",
    version = 1,
)

/** persistence rehydration 경계: 도달 가능한 상태만 손실 없이 복원하고 나머지는 거절한다. */
internal class PaymentRowTest {

    @ParameterizedTest
    @EnumSource(ReachablePayment::class)
    fun restoresEveryReachableStateWithoutLoss(state: ReachablePayment) {
        val row = PaymentRow.from(state.create())

        assertEquals(row, PaymentRow.from(row.toDomain()))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreachableRows")
    fun rejectsRowOutsideReachableDomainState(description: String, row: PaymentRow) {
        assertThrows<PersistedStateRestoreException> { row.toDomain() }
    }

    companion object {
        @JvmStatic
        fun unreachableRows(): List<Arguments> = listOf(
            Arguments.of("CREATED at version 1", APPROVED_ROW.copy(status = "CREATED", approvedAmountMinor = 0)),
            Arguments.of("APPROVED at version 0", APPROVED_ROW.copy(version = 0)),
            Arguments.of("APPROVED at version 3", APPROVED_ROW.copy(version = 3)),
            Arguments.of(
                "RESOLUTION_REQUIRED at version 2",
                APPROVED_ROW.copy(status = "RESOLUTION_REQUIRED", approvedAmountMinor = 0, version = 2),
            ),
            Arguments.of(
                "MANUAL_REVIEW_REQUIRED at version 1",
                APPROVED_ROW.copy(status = "MANUAL_REVIEW_REQUIRED", approvedAmountMinor = 0),
            ),
            Arguments.of(
                "negative version",
                APPROVED_ROW.copy(status = "CREATED", approvedAmountMinor = 0, version = -1),
            ),
            Arguments.of("unknown status", APPROVED_ROW.copy(status = "CANCELLED")),
            Arguments.of("lowercase status", APPROVED_ROW.copy(status = "approved")),
            Arguments.of("APPROVED with zero amount", APPROVED_ROW.copy(approvedAmountMinor = 0)),
            Arguments.of("APPROVED with negative amount", APPROVED_ROW.copy(approvedAmountMinor = -1)),
            Arguments.of("DECLINED with approved amount", APPROVED_ROW.copy(status = "DECLINED")),
            Arguments.of("CREATED with approved amount", APPROVED_ROW.copy(status = "CREATED", version = 0)),
            Arguments.of("cancelled amount before cancellation exists", APPROVED_ROW.copy(cancelledAmountMinor = 1)),
            Arguments.of(
                "reserved cancel amount before cancellation exists",
                APPROVED_ROW.copy(reservedCancelAmountMinor = 1),
            ),
            Arguments.of("non-KRW approved payment", APPROVED_ROW.copy(transactionCurrency = "USD")),
            Arguments.of(
                "non-KRW created payment",
                APPROVED_ROW.copy(status = "CREATED", approvedAmountMinor = 0, version = 0, transactionCurrency = "USD"),
            ),
            Arguments.of("malformed currency", APPROVED_ROW.copy(transactionCurrency = "krw")),
            Arguments.of("blank payment id", APPROVED_ROW.copy(paymentId = " ")),
            Arguments.of("blank merchant id", APPROVED_ROW.copy(merchantId = "")),
            Arguments.of("blank client reference", APPROVED_ROW.copy(clientReference = " ")),
        )
    }
}
