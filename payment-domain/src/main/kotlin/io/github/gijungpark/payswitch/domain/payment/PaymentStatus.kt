package io.github.gijungpark.payswitch.domain.payment

/**
 * 결제 전체의 요약 상태.
 *
 * 현재는 승인 결과 전이만 정의한다. 취소 관련 상태와 전이는 취소 Issue에서 추가한다.
 */
enum class PaymentStatus {
    CREATED,
    APPROVED,
    DECLINED,
    FAILED,

    /** 승인 금융거래가 `UNKNOWN`인 결제의 요약 상태. */
    RESOLUTION_REQUIRED,
    MANUAL_REVIEW_REQUIRED,
    ;

    internal fun canTransitionTo(target: PaymentStatus): Boolean =
        when (this) {
            CREATED -> target in setOf(APPROVED, DECLINED, FAILED, RESOLUTION_REQUIRED)
            RESOLUTION_REQUIRED -> target in setOf(APPROVED, DECLINED, FAILED, MANUAL_REVIEW_REQUIRED)
            APPROVED, DECLINED, FAILED, MANUAL_REVIEW_REQUIRED -> false
        }
}
