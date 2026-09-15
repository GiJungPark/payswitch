package io.github.gijungpark.payswitch.domain

import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import io.github.gijungpark.payswitch.domain.payment.PaymentId
import io.github.gijungpark.payswitch.domain.transaction.TransactionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IdentifierTest {

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n", " \t\r\n"])
    fun rejectsBlankValue(blank: String) {
        assertThrows<IllegalArgumentException> { PaymentId(blank) }
        assertThrows<IllegalArgumentException> { MerchantId(blank) }
        assertThrows<IllegalArgumentException> { ClientReference(blank) }
        assertThrows<IllegalArgumentException> { TransactionId(blank) }
    }

    @Test
    fun keepsValueAndComparesByValue() {
        assertEquals("P-1", PaymentId("P-1").value)
        assertEquals("M-1", MerchantId("M-1").value)
        assertEquals("ORDER-1", ClientReference("ORDER-1").value)
        assertEquals("T-1", TransactionId("T-1").value)

        assertEquals(PaymentId("P-1"), PaymentId("P-1"))
        assertEquals(TransactionId("T-1"), TransactionId("T-1"))
    }

    @Test
    fun exposesNoPublicSetters() {
        val types = listOf(PaymentId::class.java, MerchantId::class.java, ClientReference::class.java, TransactionId::class.java)

        types.forEach { type ->
            assertEquals(emptyList<String>(), type.methods.map { it.name }.filter { it.startsWith("set") }, type.name)
        }
    }
}
