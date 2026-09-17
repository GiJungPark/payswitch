package io.github.gijungpark.payswitch.infrastructure.persistence

import io.github.gijungpark.payswitch.application.persistence.IdempotencyUniqueConstraint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import java.sql.SQLIntegrityConstraintViolationException

class IdempotencyUniqueConstraintClassifierTest {

    @Test
    fun classifiesIdempotencyKeyAndClientReferenceDuplicates() {
        assertEquals(
            IdempotencyUniqueConstraint.IDEMPOTENCY_KEY,
            classify("Duplicate entry 'M-1-AUTHORIZE-KEY-1' for key 'idempotency_request.PRIMARY'"),
        )
        assertEquals(
            IdempotencyUniqueConstraint.MERCHANT_CLIENT_REFERENCE,
            classify("Duplicate entry 'M-1-ORDER-1' for key 'payment.uk_payment_merchant_client_reference'"),
        )
    }

    @Test
    fun usesLastQuotedKeyNameEvenWhenValueContainsKeyText() {
        assertEquals(
            IdempotencyUniqueConstraint.MERCHANT_CLIENT_REFERENCE,
            classify(
                "Duplicate entry 'M-1-x' for key 'idempotency_request.PRIMARY' ' " +
                    "for key 'payment.uk_payment_merchant_client_reference'",
            ),
        )
        assertNull(
            classify(
                "Duplicate entry 'M-1-x' for key 'payment.uk_payment_merchant_client_reference' ' " +
                    "for key 'payment.PRIMARY'",
            ),
        )
    }

    @Test
    fun ignoresOtherUniqueKeysAndErrors() {
        assertNull(classify("Duplicate entry 'P-1' for key 'payment.PRIMARY'"))
        assertNull(classify("Duplicate entry 'T-1' for key 'financial_transaction.PRIMARY'"))
        assertNull(classify("Duplicate entry 'T-1' for key 'idempotency_request.uk_idempotency_request_transaction'"))
        assertNull(classify("Duplicate entry 'x' for key 'PRIMARY'"))
        assertNull(classify("Duplicate entry 'x' for key 'uk_payment_merchant_client_reference'"))
        assertNull(
            IdempotencyUniqueConstraintClassifier.classify(
                DataIntegrityViolationException(
                    "fk",
                    SQLIntegrityConstraintViolationException(
                        "Cannot add or update a child row: ... for key 'payment.uk_payment_merchant_client_reference'",
                        "23000",
                        1452,
                    ),
                ),
            ),
        )
        assertNull(IdempotencyUniqueConstraintClassifier.classify(DataIntegrityViolationException("no cause")))
    }

    private fun classify(message: String): IdempotencyUniqueConstraint? =
        IdempotencyUniqueConstraintClassifier.classify(
            DuplicateKeyException("duplicate", SQLIntegrityConstraintViolationException(message, "23000", 1062)),
        )
}
