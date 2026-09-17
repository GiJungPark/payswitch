package io.github.gijungpark.payswitch.infrastructure.persistence

import io.github.gijungpark.payswitch.application.persistence.IdempotencyUniqueConstraint
import io.github.gijungpark.payswitch.application.persistence.UniqueConstraintViolationException
import io.github.gijungpark.payswitch.application.persistence.UnitOfWork
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException

/**
 * Spring [TransactionTemplate]으로 [UnitOfWork]를 구현한다.
 *
 * 항상 새 physical transaction을 시작하도록 이미 진행 중인 transaction 안에서의 호출은 거절한다.
 * 그래야 unique 경쟁에서 패한 요청이 rollback 후 다른 요청의 commit된 row를 새 snapshot으로 조회할 수 있다.
 */
@Component
internal class SpringUnitOfWork(
    transactionManager: PlatformTransactionManager,
) : UnitOfWork {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Suppress("UNCHECKED_CAST")
    override fun <T> execute(block: () -> T): T {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "UnitOfWork must not join an existing transaction"
        }
        try {
            // TransactionTemplate은 nullable 결과를 반환하므로 block의 결과 타입으로 되돌린다.
            return transactionTemplate.execute { block() } as T
        } catch (e: DataIntegrityViolationException) {
            val constraint = IdempotencyUniqueConstraintClassifier.classify(e) ?: throw e
            throw UniqueConstraintViolationException(constraint, e)
        }
    }
}

/**
 * MySQL duplicate entry 오류 중 멱등성 판정에 쓰는 unique 제약만 분류한다.
 *
 * MySQL 8.0.19 이상은 `Duplicate entry '<값>' for key '<table>.<index>'` 형식으로 table을 포함한 index 이름을
 * 메시지 끝에 둔다. 값에 따옴표가 들어가도 마지막 인용 구간만 index 이름으로 해석한다.
 */
internal object IdempotencyUniqueConstraintClassifier {

    private const val MYSQL_DUPLICATE_ENTRY = 1062
    private val DUPLICATE_KEY_NAME = Regex("""for key '([^']+)'\s*$""")

    private val CONSTRAINTS = mapOf(
        "idempotency_request.PRIMARY" to IdempotencyUniqueConstraint.IDEMPOTENCY_KEY,
        "payment.uk_payment_merchant_client_reference" to IdempotencyUniqueConstraint.MERCHANT_CLIENT_REFERENCE,
    )

    fun classify(exception: Throwable): IdempotencyUniqueConstraint? {
        val duplicate = generateSequence(exception) { it.cause }
            .filterIsInstance<SQLException>()
            .firstOrNull { it.errorCode == MYSQL_DUPLICATE_ENTRY }
            ?: return null
        val keyName = DUPLICATE_KEY_NAME.find(duplicate.message.orEmpty())?.groupValues?.get(1)
            ?: return null
        return CONSTRAINTS[keyName]
    }
}
