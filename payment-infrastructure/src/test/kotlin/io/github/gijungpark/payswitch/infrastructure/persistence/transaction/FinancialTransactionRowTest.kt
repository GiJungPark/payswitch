package io.github.gijungpark.payswitch.infrastructure.persistence.transaction

import io.github.gijungpark.payswitch.infrastructure.persistence.PersistedStateRestoreException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

private val SUCCEEDED_ROW = FinancialTransactionRow(
    transactionId = "T-1",
    paymentId = "P-1",
    transactionType = "AUTHORIZE",
    originalTransactionId = null,
    amountMinor = 10_000,
    currency = "KRW",
    status = "SUCCEEDED",
    version = 2,
)

/** persistence rehydration 경계: 도달 가능한 상태만 손실 없이 복원하고 나머지는 거절한다. */
internal class FinancialTransactionRowTest {

    @ParameterizedTest
    @EnumSource(ReachableTransaction::class)
    fun restoresEveryReachableStateWithoutLoss(state: ReachableTransaction) {
        val row = FinancialTransactionRow.from(state.create())

        assertEquals(row, FinancialTransactionRow.from(row.toDomain()))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreachableRows")
    fun rejectsRowOutsideReachableDomainState(description: String, row: FinancialTransactionRow) {
        assertThrows<PersistedStateRestoreException> { row.toDomain() }
    }

    companion object {
        @JvmStatic
        fun unreachableRows(): List<Arguments> = listOf(
            Arguments.of("RECEIVED at version 1", SUCCEEDED_ROW.copy(status = "RECEIVED", version = 1)),
            Arguments.of("PROCESSING at version 0", SUCCEEDED_ROW.copy(status = "PROCESSING", version = 0)),
            Arguments.of("SUCCEEDED at version 1", SUCCEEDED_ROW.copy(version = 1)),
            Arguments.of("SUCCEEDED at version 4", SUCCEEDED_ROW.copy(version = 4)),
            Arguments.of("UNKNOWN at version 3", SUCCEEDED_ROW.copy(status = "UNKNOWN", version = 3)),
            Arguments.of("MANUAL_REVIEW_REQUIRED at version 2", SUCCEEDED_ROW.copy(status = "MANUAL_REVIEW_REQUIRED")),
            Arguments.of("negative version", SUCCEEDED_ROW.copy(status = "RECEIVED", version = -1)),
            Arguments.of("unknown status", SUCCEEDED_ROW.copy(status = "APPROVED")),
            Arguments.of("unknown transaction type", SUCCEEDED_ROW.copy(transactionType = "CANCEL")),
            Arguments.of("authorization with original transaction", SUCCEEDED_ROW.copy(originalTransactionId = "T-0")),
            Arguments.of("zero amount", SUCCEEDED_ROW.copy(amountMinor = 0)),
            Arguments.of("negative amount", SUCCEEDED_ROW.copy(amountMinor = -1)),
            Arguments.of("non-KRW currency", SUCCEEDED_ROW.copy(currency = "USD")),
            Arguments.of("malformed currency", SUCCEEDED_ROW.copy(currency = "KR")),
            Arguments.of("blank transaction id", SUCCEEDED_ROW.copy(transactionId = " ")),
            Arguments.of("blank payment id", SUCCEEDED_ROW.copy(paymentId = "")),
        )
    }
}
