package io.github.gijungpark.payswitch.application.authorization

import io.github.gijungpark.payswitch.domain.payment.Payment
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransaction

/**
 * 승인 접수 결과.
 *
 * 기관 전문은 [Registered]에서만 보낼 수 있다. [Reused]는 이미 접수된 거래이므로 기관에 다시 보내지 않는다.
 */
sealed interface AuthorizationIntakeResult {

    /** 이 요청이 결제, 승인 금융거래와 멱등키 기록을 처음 등록했다. */
    data class Registered(
        val payment: Payment,
        val transaction: FinancialTransaction,
    ) : AuthorizationIntakeResult

    /**
     * 같은 요청이 이미 등록돼 저장된 결제와 승인 금융거래를 반환한다. 결제·금융거래 row는 만들거나 바꾸지 않는다.
     *
     * 다른 멱등키로 같은 client reference의 같은 요청이 들어온 경우에는 그 멱등키를 기존 승인 거래와 request hash에
     * 결합한 기록을 저장한 뒤 반환한다.
     */
    data class Reused(
        val payment: Payment,
        val transaction: FinancialTransaction,
    ) : AuthorizationIntakeResult

    /** 기존 요청과 상충해 아무것도 변경하지 않았다. */
    data class Conflict(
        val reason: AuthorizationConflictReason,
    ) : AuthorizationIntakeResult
}

enum class AuthorizationConflictReason {
    /** 같은 가맹점·operation·멱등키가 다른 request hash로 이미 사용됐다. */
    IDEMPOTENCY_KEY_REUSED,

    /** 다른 멱등키로 같은 가맹점·client reference의 금액 또는 통화가 다른 승인이 이미 접수됐다. */
    CLIENT_REFERENCE_CONFLICT,
}
