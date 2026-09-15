package io.github.gijungpark.payswitch.infrastructure.persistence.payment

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** `payment` table MyBatis mapper. SQL은 같은 package의 `PaymentMapper.xml`에 있다. */
@Mapper
internal interface PaymentMapper {

    fun insert(row: PaymentRow): Int

    fun findById(@Param("paymentId") paymentId: String): PaymentRow?

    fun findByMerchantIdAndClientReference(
        @Param("merchantId") merchantId: String,
        @Param("clientReference") clientReference: String,
    ): PaymentRow?
}
