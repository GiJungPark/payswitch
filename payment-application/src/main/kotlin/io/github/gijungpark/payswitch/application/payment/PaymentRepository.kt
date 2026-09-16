package io.github.gijungpark.payswitch.application.payment

import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.payment.Payment
import io.github.gijungpark.payswitch.domain.payment.PaymentId

/**
 * [Payment] 저장소 port.
 *
 * 조회 결과는 domain 규칙상 도달 가능한 persisted state만 복원한다. 상태 변경 저장과 optimistic lock은 후속 Issue에서 정의한다.
 */
interface PaymentRepository {

    /**
     * 새 결제를 저장한다.
     *
     * 같은 `paymentId` 또는 같은 `merchantId`·`clientReference` 조합이 이미 있으면 DB unique 제약 위반으로 실패한다.
     */
    fun insert(payment: Payment)

    fun findById(paymentId: PaymentId): Payment?

    fun findByMerchantIdAndClientReference(merchantId: MerchantId, clientReference: ClientReference): Payment?
}
