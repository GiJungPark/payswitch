[PaySwitch 홈](../README.md)

# payment-application

결제 use case와 외부 연동 port를 정의하는 application 모듈이다.

## 책임

- 승인·조회 등 거래 use case의 흐름과 transaction 경계를 조정한다.
- 저장소와 기관 connector가 구현할 port를 정의한다.
- 현재는 `PaymentRepository`와 `FinancialTransactionRepository`의 insert·조회 port를 제공하며 use case와 기관 connector port는 후속 Issue에서 구현한다.

## 주요 진입점

- [`PaymentRepository.kt`](src/main/kotlin/io/github/gijungpark/payswitch/application/payment/PaymentRepository.kt): 결제 insert와 ID·가맹점 client reference 조회 port
- [`FinancialTransactionRepository.kt`](src/main/kotlin/io/github/gijungpark/payswitch/application/transaction/FinancialTransactionRepository.kt): 금융거래 insert와 ID·결제 ID 조회 port
- Build: [build.gradle.kts](build.gradle.kts)

## 의존 방향

```text
payment-api ─┐
payment-infrastructure ┴─> payment-application ─> payment-domain
```

- 유일한 project dependency는 [payment-domain](../payment-domain/README.md)이다.
- infrastructure, api 또는 simulator module을 의존하지 않는다.

## 실행·검증

실행 가능한 애플리케이션이 아닌 library 모듈이다.

```bash
./gradlew :payment-application:build
./gradlew :payment-application:dependencies
```

## 관련 문서

- [도메인 모델과 상태 정책](../docs/domain-model.md)
- [ADR 0003: 동기·비동기 경계](../docs/adr/0003-sync-async-boundary.md)
- [장애 시나리오](../docs/failure-scenarios.md)
