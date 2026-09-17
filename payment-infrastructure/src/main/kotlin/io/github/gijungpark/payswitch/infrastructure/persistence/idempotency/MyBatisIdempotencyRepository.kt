package io.github.gijungpark.payswitch.infrastructure.persistence.idempotency

import io.github.gijungpark.payswitch.application.idempotency.IdempotencyKey
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRecord
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRepository
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType
import org.springframework.stereotype.Component

/**
 * [IdempotencyRepository]의 MyBatis 구현.
 *
 * DB 제약 위반은 MyBatis-Spring이 변환한 `org.springframework.dao.DataIntegrityViolationException` 계열로 전파되고,
 * 멱등성 경쟁 판정은 [io.github.gijungpark.payswitch.infrastructure.persistence.SpringUnitOfWork]가 수행한다.
 */
@Component
internal class MyBatisIdempotencyRepository(
    private val mapper: IdempotencyRequestMapper,
) : IdempotencyRepository {

    override fun insert(record: IdempotencyRecord) {
        val inserted = mapper.insert(IdempotencyRequestRow.from(record))
        check(inserted == 1) { "expected to insert one idempotency_request row but inserted $inserted" }
    }

    override fun find(
        merchantId: MerchantId,
        operationType: FinancialTransactionType,
        idempotencyKey: IdempotencyKey,
    ): IdempotencyRecord? =
        mapper.find(merchantId.value, operationType.name, idempotencyKey.value)?.toRecord()
}
