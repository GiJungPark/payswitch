[PaySwitch 홈](../README.md)

# payment-application

결제 use case와 외부 연동 port를 정의하는 application 모듈이다.

## 책임

- 승인·조회 등 거래 use case의 흐름과 transaction 경계를 조정한다.
- 저장소와 기관 connector가 구현할 port를 정의한다.
- 현재는 KRW 승인 접수 use case와 결제·금융거래·멱등키 저장소, transaction 경계 port를 제공한다. 기관 connector port와 상태 진행은 후속 Issue에서 구현한다.
- Spring, MyBatis와 JDBC 타입을 의존하지 않는다. transaction과 DB 예외 변환은 [payment-infrastructure](../payment-infrastructure/README.md)가 port를 구현해 제공한다.

## 주요 진입점

Source root: `src/main/kotlin/io/github/gijungpark/payswitch/application/`

| Package | 타입 | 책임 |
|---|---|---|
| `authorization` | `AuthorizePaymentUseCase`, `AuthorizePaymentCommand` | KRW 승인 접수와 중복·충돌 판정 |
| `authorization` | `AuthorizationIntakeResult`, `AuthorizationConflictReason` | 접수 결과 contract |
| `authorization` | `AuthorizationRequestCanonicalForm` | 승인 요청 canonical bytes와 SHA-256 request hash |
| `authorization` | `AuthorizationIdGenerator` | 결제·금융거래 ID 발급 port |
| `idempotency` | `IdempotencyKey`, `RequestHash`, `IdempotencyRecord`, `IdempotencyRepository` | 멱등키 검증, hash 표현과 저장소 port |
| `persistence` | `UnitOfWork`, `UniqueConstraintViolationException` | transaction 경계 port와 멱등성 unique 경쟁 신호 |

- [`PaymentRepository.kt`](src/main/kotlin/io/github/gijungpark/payswitch/application/payment/PaymentRepository.kt): 결제 insert와 ID·가맹점 client reference 조회 port
- [`FinancialTransactionRepository.kt`](src/main/kotlin/io/github/gijungpark/payswitch/application/transaction/FinancialTransactionRepository.kt): 금융거래 insert와 ID·결제 ID 조회 port
- Build: [build.gradle.kts](build.gradle.kts)

### 승인 접수 contract

`AuthorizePaymentUseCase.authorize(command)`는 다음 순서로 판정한다.

1. `IdempotencyKey`는 생성 시 blank와 128 code point 초과를 거절하고, command는 1 미만 금액을 거절한다. 두 검증은 저장소 접근 전에 실행된다.
2. 같은 `merchantId`·`AUTHORIZE`·멱등키 기록이 있으면 request hash가 같을 때 `Reused`, 다를 때 `Conflict(IDEMPOTENCY_KEY_REUSED)`를 반환한다.
3. 기록이 없고 같은 `merchantId`·`clientReference` 결제가 있으면 승인 거래의 금액·통화를 비교한다. 같으면 새 멱등키를 기존 승인 거래와 현재 request hash에 결합한 기록을 `UnitOfWork`에서 저장하고 `Reused`를, 다르면 `Conflict(CLIENT_REFERENCE_CONFLICT)`를 반환한다. `Reused`를 받은 멱등키도 이후 다른 요청에 쓰이면 `IDEMPOTENCY_KEY_REUSED`다.
4. 둘 다 없으면 `KRW`만 허용하고, `CREATED` `Payment`, `RECEIVED` `AUTHORIZE` `FinancialTransaction`, 멱등키 기록을 하나의 `UnitOfWork`에서 저장한 뒤 `Registered`를 반환한다.
5. 3·4의 저장 중 `UniqueConstraintViolationException`이 발생하면 다른 요청이 먼저 commit한 것이므로 rollback 후 2부터 다시 판정한다. 판정은 최대 3회이며 그 안에 확정하지 못하면 `IllegalStateException`으로 실패한다.

| 결과 | 의미 | 기관 전문 |
|---|---|---|
| `Registered(payment, transaction)` | 이 호출이 세 row를 처음 등록했다. | 이후 connector가 보낼 수 있다. |
| `Reused(payment, transaction)` | 저장된 결제와 승인 거래다. 결제·거래 row는 만들거나 바꾸지 않았고, 다른 멱등키로 온 같은 요청이면 그 멱등키 기록만 추가했다. | 다시 보내지 않는다. |
| `Conflict(reason)` | 기존 요청과 상충한다. row를 만들거나 바꾸지 않았다. | 보내지 않는다. |

canonical representation과 hash 저장 형식은 [데이터 모델](../docs/database-schema.md#request-hash)을 따른다.

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

use case 판정(in-memory fake 저장소), 멱등키 검증과 request hash golden test는 `src/test/kotlin/io/github/gijungpark/payswitch/application/`에 있다. JUnit 5는 test classpath에만 두며 Spring Boot BOM으로 버전을 관리한다. DB 경쟁과 transaction은 [payment-infrastructure](../payment-infrastructure/README.md#실행검증) 통합 테스트가 검증한다.

```bash
./gradlew :payment-application:clean :payment-application:test
```

## 관련 문서

- [도메인 모델과 상태 정책](../docs/domain-model.md)
- [ADR 0003: 동기·비동기 경계](../docs/adr/0003-sync-async-boundary.md)
- [장애 시나리오](../docs/failure-scenarios.md)
