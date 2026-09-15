package io.github.gijungpark.payswitch.domain.transaction

/**
 * 기관에 전달되는 금융거래 종류.
 *
 * 현재는 승인만 정의한다. `CANCEL`과 `REVERSAL`은 취소·망취소 Issue에서 추가한다.
 */
enum class FinancialTransactionType {
    AUTHORIZE,
}
