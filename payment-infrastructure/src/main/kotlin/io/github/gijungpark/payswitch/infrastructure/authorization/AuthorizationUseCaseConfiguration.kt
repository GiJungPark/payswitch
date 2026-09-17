package io.github.gijungpark.payswitch.infrastructure.authorization

import io.github.gijungpark.payswitch.application.authorization.AuthorizationIdGenerator
import io.github.gijungpark.payswitch.application.authorization.AuthorizePaymentUseCase
import io.github.gijungpark.payswitch.application.idempotency.IdempotencyRepository
import io.github.gijungpark.payswitch.application.payment.PaymentRepository
import io.github.gijungpark.payswitch.application.persistence.UnitOfWork
import io.github.gijungpark.payswitch.application.transaction.FinancialTransactionRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * framework에 독립적인 승인 접수 use case를 MyBatis 저장소와 Spring transaction adapter로 조립한다.
 */
@Configuration(proxyBeanMethods = false)
internal class AuthorizationUseCaseConfiguration {

    @Bean
    fun authorizationIdGenerator(): AuthorizationIdGenerator = UuidAuthorizationIdGenerator()

    @Bean
    fun authorizePaymentUseCase(
        paymentRepository: PaymentRepository,
        transactionRepository: FinancialTransactionRepository,
        idempotencyRepository: IdempotencyRepository,
        unitOfWork: UnitOfWork,
        idGenerator: AuthorizationIdGenerator,
    ): AuthorizePaymentUseCase =
        AuthorizePaymentUseCase(paymentRepository, transactionRepository, idempotencyRepository, unitOfWork, idGenerator)
}
