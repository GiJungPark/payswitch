package io.github.gijungpark.payswitch.infrastructure.persistence.idempotency

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** `idempotency_request` table MyBatis mapper. SQL은 같은 package의 `IdempotencyRequestMapper.xml`에 있다. */
@Mapper
internal interface IdempotencyRequestMapper {

    fun insert(row: IdempotencyRequestRow): Int

    fun find(
        @Param("merchantId") merchantId: String,
        @Param("operationType") operationType: String,
        @Param("idempotencyKey") idempotencyKey: String,
    ): IdempotencyRequestRow?
}
