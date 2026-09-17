package io.github.gijungpark.payswitch.application.authorization

import io.github.gijungpark.payswitch.application.idempotency.IdempotencyKey
import io.github.gijungpark.payswitch.application.idempotency.RequestHash
import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.money.Money
import io.github.gijungpark.payswitch.domain.payment.ClientReference
import io.github.gijungpark.payswitch.domain.payment.MerchantId
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * request hash byte contract golden test. 기대값은 docs/database-schema.md의 예시와 같고 `shasum -a 256`으로 독립 계산했다.
 */
class AuthorizationRequestCanonicalFormTest {

    @Test
    fun encodesLengthPrefixedFieldsInDocumentedOrder() {
        val bytes = AuthorizationRequestCanonicalForm.encode(command())

        assertArrayEquals("9:AUTHORIZE5:M-00110:ORDER-00015:100003:KRW".toByteArray(Charsets.US_ASCII), bytes)
        assertArrayEquals(
            byteArrayOf(
                0x39, 0x3a, 0x41, 0x55, 0x54, 0x48, 0x4f, 0x52, 0x49, 0x5a, 0x45,
                0x35, 0x3a, 0x4d, 0x2d, 0x30, 0x30, 0x31,
                0x31, 0x30, 0x3a, 0x4f, 0x52, 0x44, 0x45, 0x52, 0x2d, 0x30, 0x30, 0x30, 0x31,
                0x35, 0x3a, 0x31, 0x30, 0x30, 0x30, 0x30,
                0x33, 0x3a, 0x4b, 0x52, 0x57,
            ),
            bytes,
        )
        assertEquals(
            RequestHash("3b3d469f85cd8f19e8a05fe561020bc23b8d950564471067c752624ee1205211"),
            AuthorizationRequestCanonicalForm.hash(command()),
        )
    }

    @Test
    fun excludesIdempotencyKey() {
        val first = command(idempotencyKey = "KEY-1")
        val second = command(idempotencyKey = "completely-different-key")

        assertArrayEquals(AuthorizationRequestCanonicalForm.encode(first), AuthorizationRequestCanonicalForm.encode(second))
        assertEquals(AuthorizationRequestCanonicalForm.hash(first), AuthorizationRequestCanonicalForm.hash(second))
    }

    @Test
    fun usesUtf8ByteLengthForNonAsciiValues() {
        val command = command(merchantId = "M-001", clientReference = "주문-1")

        assertArrayEquals(
            "9:AUTHORIZE5:M-0018:주문-15:100003:KRW".toByteArray(Charsets.UTF_8),
            AuthorizationRequestCanonicalForm.encode(command),
        )
        assertEquals(
            RequestHash("e9ab25a00bfb7c3f29f47a861a557a10038876b42f1f1aae6671f186a88b181c"),
            AuthorizationRequestCanonicalForm.hash(command),
        )
    }

    @Test
    fun encodesAmountBoundariesAsPlainDecimal() {
        assertEquals(
            RequestHash("5e62e9212611ac2eaa25a6d69bc29671023b21fef724f06cf337d45f8b3cb74e"),
            AuthorizationRequestCanonicalForm.hash(command(amountMinor = 1)),
        )
        assertArrayEquals(
            "9:AUTHORIZE5:M-00110:ORDER-000119:92233720368547758073:KRW".toByteArray(Charsets.US_ASCII),
            AuthorizationRequestCanonicalForm.encode(command(amountMinor = Long.MAX_VALUE)),
        )
        assertEquals(
            RequestHash("bdc71d2a12da1391c5a208b6f5d249b9b07111405931bf284e9f5105f359bad4"),
            AuthorizationRequestCanonicalForm.hash(command(amountMinor = Long.MAX_VALUE)),
        )
    }

    @Test
    fun includesCurrency() {
        assertEquals(
            RequestHash("534c24f01e5021dbff9fcabcfa142e450aeab83f9aff776f301353bbd8e25c0b"),
            AuthorizationRequestCanonicalForm.hash(command(currency = "USD")),
        )
    }

    @Test
    fun keepsFieldBoundariesUnambiguous() {
        assertNotEquals(
            AuthorizationRequestCanonicalForm.hash(command(merchantId = "AB", clientReference = "C")),
            AuthorizationRequestCanonicalForm.hash(command(merchantId = "A", clientReference = "BC")),
        )
        assertNotEquals(
            AuthorizationRequestCanonicalForm.hash(command(clientReference = "ORDER-1", amountMinor = 23)),
            AuthorizationRequestCanonicalForm.hash(command(clientReference = "ORDER-12", amountMinor = 3)),
        )
        assertNotEquals(
            AuthorizationRequestCanonicalForm.hash(command(merchantId = "M", clientReference = "1:X")),
            AuthorizationRequestCanonicalForm.hash(command(merchantId = "M1:", clientReference = "X")),
        )
    }

    @Test
    fun doesNotDependOnDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"))
            assertEquals(
                RequestHash("bdc71d2a12da1391c5a208b6f5d249b9b07111405931bf284e9f5105f359bad4"),
                AuthorizationRequestCanonicalForm.hash(command(amountMinor = Long.MAX_VALUE)),
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun command(
        merchantId: String = "M-001",
        clientReference: String = "ORDER-0001",
        idempotencyKey: String = "KEY-1",
        amountMinor: Long = 10_000,
        currency: String = "KRW",
    ) = AuthorizePaymentCommand(
        merchantId = MerchantId(merchantId),
        clientReference = ClientReference(clientReference),
        idempotencyKey = IdempotencyKey(idempotencyKey),
        amount = Money(amountMinor, CurrencyCode(currency)),
    )
}
