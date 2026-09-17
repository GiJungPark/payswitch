package io.github.gijungpark.payswitch.application.authorization

import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRecord
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRepository
import io.github.gijungpark.payswitch.application.idempotency.RequestHash
import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.application.persistence.UniqueConstraintViolationException
import io.github.gijungpark.payswitch.application.persistence.UnitOfWork
import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import io.github.gijungpark.payswitch.domain.money.CurrencyCode
import io.github.gijungpark.payswitch.domain.payment.Payment
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransaction
import io.github.gijungpark.payswitch.domain.transaction.FinancialTransactionType

/**
 * KRW 승인 요청을 접수한다.
 *
 * 1. 같은 가맹점·operation·멱등키 기록이 있으면 request hash로 [AuthorizationIntakeResult.Reused] 또는
 *    `IDEMPOTENCY_KEY_REUSED`를 판정한다. 멱등키 판정은 client reference 판정보다 항상 우선한다.
 * 2. 기록이 없고 같은 가맹점·client reference 결제가 있으면 승인 금액·통화를 비교한다. 같으면 새 멱등키를 기존 승인 거래와
 *    현재 request hash에 결합해 저장한 뒤 [AuthorizationIntakeResult.Reused]를, 다르면 `CLIENT_REFERENCE_CONFLICT`를 반환한다.
 * 3. 둘 다 없으면 `CREATED` 결제, `RECEIVED` 승인 금융거래와 멱등키 기록을 하나의 [UnitOfWork]에서 저장한다.
 * 4. 2·3의 저장이 다른 요청의 commit과 unique 제약에서 충돌하면 rollback 후 commit된 row로 1부터 다시 판정한다.
 *    판정은 최대 [MAX_ATTEMPTS]회 수행하며 그 안에 결과를 확정하지 못하면 [IllegalStateException]으로 실패한다.
 *
 * 금융거래 상태는 진행시키지 않으며 기관 connector를 호출하지 않는다.
 */
class AuthorizePaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val transactionRepository: FinancialTransactionRepository,
    private val idempotencyRepository: IdempotencyRepository,
    private val unitOfWork: UnitOfWork,
    private val idGenerator: AuthorizationIdGenerator,
) {

    fun authorize(command: AuthorizePaymentCommand): AuthorizationIntakeResult {
        val requestHash = AuthorizationRequestCanonicalForm.hash(command)
        var lastConflict: UniqueConstraintViolationException? = null
        repeat(MAX_ATTEMPTS) {
            try {
                return when (val decision = decide(command, requestHash)) {
                    is Decision.Decided -> decision.result
                    is Decision.BindKey -> bindKey(command, requestHash, decision)
                    Decision.Register -> register(command, requestHash)
                }
            } catch (e: UniqueConstraintViolationException) {
                lastConflict = e
            }
        }
        throw IllegalStateException("could not resolve authorization intake after $MAX_ATTEMPTS attempts", lastConflict)
    }

    private fun decide(command: AuthorizePaymentCommand, requestHash: RequestHash): Decision {
        val record = idempotencyRepository.find(command.merchantId, OPERATION, command.idempotencyKey)
        if (record != null) {
            if (record.requestHash != requestHash) {
                return Decision.Decided(
                    AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.IDEMPOTENCY_KEY_REUSED),
                )
            }
            val transaction = checkNotNull(transactionRepository.findById(record.transactionId)) {
                "idempotency record references missing transaction ${record.transactionId}"
            }
            return Decision.Decided(AuthorizationIntakeResult.Reused(loadPayment(transaction), transaction))
        }

        val payment = paymentRepository.findByMerchantIdAndClientReference(command.merchantId, command.clientReference)
            ?: return Decision.Register
        val authorization = transactionRepository.findByPaymentId(payment.paymentId)
            .singleOrNull { it.type == OPERATION }
            ?: throw IllegalStateException("payment ${payment.paymentId} has no single authorization transaction")
        if (authorization.amount != command.amount) {
            return Decision.Decided(
                AuthorizationIntakeResult.Conflict(AuthorizationConflictReason.CLIENT_REFERENCE_CONFLICT),
            )
        }
        return Decision.BindKey(payment, authorization)
    }

    /** 새 멱등키를 기존 승인 거래에 결합한다. 같은 키가 먼저 commit되면 [UniqueConstraintViolationException]을 던진다. */
    private fun bindKey(
        command: AuthorizePaymentCommand,
        requestHash: RequestHash,
        decision: Decision.BindKey,
    ): AuthorizationIntakeResult {
        val record = recordOf(command, requestHash, decision.transaction)
        unitOfWork.execute { idempotencyRepository.insert(record) }
        return AuthorizationIntakeResult.Reused(decision.payment, decision.transaction)
    }

    /** 세 row를 등록한다. 멱등키나 client reference가 먼저 commit되면 [UniqueConstraintViolationException]을 던진다. */
    private fun register(command: AuthorizePaymentCommand, requestHash: RequestHash): AuthorizationIntakeResult {
        require(command.amount.currency == CurrencyCode.KRW) {
            "only KRW authorization is supported: ${command.amount.currency}"
        }
        val payment = Payment.createKrwAuthorization(
            idGenerator.newPaymentId(),
            command.merchantId,
            command.clientReference,
        )
        val transaction = FinancialTransaction.createAuthorization(
            idGenerator.newTransactionId(),
            payment.paymentId,
            command.amount,
        )
        val record = recordOf(command, requestHash, transaction)

        unitOfWork.execute {
            paymentRepository.insert(payment)
            transactionRepository.insert(transaction)
            idempotencyRepository.insert(record)
        }
        return AuthorizationIntakeResult.Registered(payment, transaction)
    }

    private fun recordOf(
        command: AuthorizePaymentCommand,
        requestHash: RequestHash,
        transaction: FinancialTransaction,
    ) = IdempotencyRecord(
        merchantId = command.merchantId,
        operationType = OPERATION,
        idempotencyKey = command.idempotencyKey,
        requestHash = requestHash,
        transactionId = transaction.transactionId,
    )

    private fun loadPayment(transaction: FinancialTransaction): Payment =
        checkNotNull(paymentRepository.findById(transaction.paymentId)) {
            "transaction ${transaction.transactionId} references missing payment ${transaction.paymentId}"
        }

    private sealed interface Decision {
        data class Decided(val result: AuthorizationIntakeResult) : Decision

        data class BindKey(val payment: Payment, val transaction: FinancialTransaction) : Decision

        data object Register : Decision
    }

    private companion object {
        val OPERATION = FinancialTransactionType.AUTHORIZE

        /** 신규 등록 충돌 → 기존 거래에 키 결합 충돌 → 같은 키 기록 판정까지 필요한 최대 판정 횟수. */
        const val MAX_ATTEMPTS = 3
    }
}
