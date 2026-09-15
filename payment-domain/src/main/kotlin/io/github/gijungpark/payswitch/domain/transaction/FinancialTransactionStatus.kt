package io.github.gijungpark.payswitch.domain.transaction

/**
 * 개별 금융거래의 상태.
 *
 * - [DECLINED]: 기관이 전문을 처리하고 업무상 거절했다.
 * - [FAILED]: 기관이 거래를 처리하지 않았음을 확정할 수 있다.
 * - [UNKNOWN]: 기관 처리 여부를 확정할 수 없다.
 */
enum class FinancialTransactionStatus {
    RECEIVED,
    PROCESSING,
    SUCCEEDED,
    DECLINED,
    FAILED,
    UNKNOWN,
    MANUAL_REVIEW_REQUIRED,
    ;

    internal fun canTransitionTo(target: FinancialTransactionStatus): Boolean =
        when (this) {
            RECEIVED -> target == PROCESSING
            PROCESSING -> target in setOf(SUCCEEDED, DECLINED, FAILED, UNKNOWN)
            UNKNOWN -> target in setOf(SUCCEEDED, DECLINED, FAILED, MANUAL_REVIEW_REQUIRED)
            SUCCEEDED, DECLINED, FAILED, MANUAL_REVIEW_REQUIRED -> false
        }
}
