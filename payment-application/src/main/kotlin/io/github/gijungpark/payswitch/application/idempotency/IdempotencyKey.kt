package io.github.gijungpark.payswitch.application.idempotency

/**
 * 호출자가 같은 operation의 재시도를 식별하려고 보낸 멱등키.
 *
 * blank 값과 [MAX_LENGTH] code point를 넘는 값은 DB에 접근하기 전에 거절한다.
 * 길이는 `idempotency_request.idempotency_key` `VARCHAR(128)`의 문자 수 기준과 같도록 Unicode code point로 센다.
 * 대소문자와 앞뒤 공백을 포함한 원래 문자열을 그대로 비교한다.
 */
@JvmInline
value class IdempotencyKey(val value: String) {

    init {
        require(value.isNotBlank()) { "idempotencyKey must not be blank" }
        val length = value.codePointCount(0, value.length)
        require(length <= MAX_LENGTH) { "idempotencyKey must be at most $MAX_LENGTH characters: $length" }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 128
    }
}
