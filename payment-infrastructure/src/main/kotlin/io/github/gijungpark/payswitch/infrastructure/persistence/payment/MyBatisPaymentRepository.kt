package io.github.gijungpark.payswitch.infrastructure.persistence.payment

import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.payment.Payment
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import org.springframework.stereotype.Component

/**
 * [PaymentRepository]의 MyBatis 구현.
 *
 * DB 제약 위반은 MyBatis-Spring이 변환한 `org.springframework.dao.DataIntegrityViolationException` 계열로 전파된다.
 */
@Component
internal class MyBatisPaymentRepository(
    private val mapper: PaymentMapper,
) : PaymentRepository {

    override fun insert(payment: Payment) {
        val inserted = mapper.insert(PaymentRow.from(payment))
        check(inserted == 1) { "expected to insert one payment row but inserted $inserted" }
    }

    override fun findById(paymentId: PaymentId): Payment? =
        mapper.findById(paymentId.value)?.toDomain()

    override fun findByMerchantIdAndClientReference(
        merchantId: MerchantId,
        clientReference: ClientReference,
    ): Payment? =
        mapper.findByMerchantIdAndClientReference(merchantId.value, clientReference.value)?.toDomain()
}
