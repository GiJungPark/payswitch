package io.github.gijungpark.payswitch.application.authorization

import io.github.gijungpark.payswitch.application.idempotency.RequestHash
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType
import java.io.ByteArrayOutputStream

/**
 * 승인 요청의 canonical representation과 request hash.
 *
 * 다음 필드를 이 순서로 이어 붙인다. 멱등키는 포함하지 않는다.
 *
 * 1. operation type (`AUTHORIZE`)
 * 2. `merchantId`
 * 3. `clientReference`
 * 4. `amountMinor` (부호·앞자리 0 없는 ASCII 10진수)
 * 5. `currency`
 *
 * 각 필드는 `<UTF-8 byte 길이의 ASCII 10진수>:<UTF-8 bytes>`로 표현하고 구분자나 줄바꿈 없이 연결한다.
 * 길이 접두사로 필드 경계를 결정하므로 값에 `:`나 숫자가 있어도 모호하지 않으며 locale과 기본 charset을 사용하지 않는다.
 * 예: `9:AUTHORIZE5:M-00110:ORDER-00015:100003:KRW`
 */
object AuthorizationRequestCanonicalForm {

    fun encode(command: AuthorizePaymentCommand): ByteArray {
        val fields = listOf(
            FinancialTransactionType.AUTHORIZE.name,
            command.merchantId.value,
            command.clientReference.value,
            command.amount.amountMinor.toString(),
            command.amount.currency.value,
        )
        val output = ByteArrayOutputStream()
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            output.write(bytes.size.toString().toByteArray(Charsets.US_ASCII))
            output.write(':'.code)
            output.write(bytes)
        }
        return output.toByteArray()
    }

    fun hash(command: AuthorizePaymentCommand): RequestHash = RequestHash.sha256Of(encode(command))
}
