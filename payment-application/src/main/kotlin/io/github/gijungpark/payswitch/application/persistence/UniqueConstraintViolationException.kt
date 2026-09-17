package io.github.gijungpark.payswitch.application.persistence

/**
 * 멱등성 경쟁 판정에 사용하는 DB unique 제약.
 */
enum class IdempotencyUniqueConstraint {
    /** `idempotency_request`의 `merchant_id`·`operation_type`·`idempotency_key` 조합 */
    IDEMPOTENCY_KEY,

    /** `payment`의 `merchant_id`·`client_reference` 조합 */
    MERCHANT_CLIENT_REFERENCE,
}

/**
 * 다른 요청이 먼저 commit한 row와 [constraint]가 충돌해 transaction이 rollback됐음을 나타낸다.
 */
class UniqueConstraintViolationException(
    val constraint: IdempotencyUniqueConstraint,
    cause: Throwable? = null,
) : RuntimeException("unique constraint $constraint was violated", cause)
