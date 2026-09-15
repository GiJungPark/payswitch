[PaySwitch 홈](../README.md)

# payment-domain

결제 스위치의 순수 도메인 모델 모듈이다.

## 책임

- `Payment`, `FinancialTransaction`, `InstitutionMessageAttempt`의 경계와 상태 전이 규칙을 담는다.
- 금액 불변식, 통화와 minor unit 규칙을 framework 없이 표현한다.
- 현재는 KRW 승인용 `Payment`와 `AUTHORIZE` `FinancialTransaction`의 생성, 상태 전이와 version 규칙을 구현한다.
- 취소·망취소, 멱등키와 `InstitutionMessageAttempt`는 후속 Issue에서 구현한다.
- 영속성 row mapping과 domain 복원은 [payment-infrastructure](../payment-infrastructure/README.md#persistence-rehydration)가 담당하며 이 모듈은 framework에 독립적인 상태 전이와 불변식만 제공한다.

## 주요 진입점

Source root: `src/main/kotlin/io/github/gijungpark/payswitch/domain/`

| Package | 타입 | 책임 |
|---|---|---|
| `money` | `Money`, `CurrencyCode` | minor unit 정수 금액과 통화를 함께 보유하고 음수를 거절한다. |
| `payment` | `Payment`, `PaymentStatus` | 결제 요약 상태와 승인·취소·예약 취소 금액. `Payment.createKrwAuthorization`으로 생성한다. |
| `payment` | `PaymentId`, `MerchantId`, `ClientReference` | blank를 거절하는 불변 식별자 |
| `transaction` | `FinancialTransaction`, `FinancialTransactionStatus`, `FinancialTransactionType` | 개별 금융거래의 독립 상태. `FinancialTransaction.createAuthorization`으로 생성한다. |
| `transaction` | `TransactionId` | blank를 거절하는 불변 금융거래 식별자 |

- 초기값, 허용 전이, version과 중복 적용 정책은 [도메인 모델과 상태 정책](../docs/domain-model.md#구현된-승인-도메인-규칙)을 따른다.
- 상태 변경 method는 자기 객체만 변경한다. `FinancialTransaction`은 `PaymentId`로만 결제를 참조한다.
- Build: [build.gradle.kts](build.gradle.kts)

## 의존 방향

```text
payment-application ─┐
payment-infrastructure ┴─> payment-domain ─> (Kotlin 표준 라이브러리만)
```

- Spring 또는 다른 project module을 의존하지 않는다.
- JUnit BOM, JUnit Jupiter와 JUnit Platform launcher는 test classpath에만 둔다.
- `payment-api`는 이 모듈을 직접 의존하지 않는다.
- `bank-a-simulator`는 이 모듈을 의존하지 않는다.

## 실행·검증

실행 가능한 애플리케이션이 아닌 library 모듈이다.

```bash
./gradlew :payment-domain:clean :payment-domain:test
./gradlew :payment-domain:build
./gradlew :payment-domain:dependencies
```

단위 테스트는 `src/test/kotlin/io/github/gijungpark/payswitch/domain/`에 있으며 허용·거절 전이, 중복·상충 결과와 금액·식별자 경계값을 검증한다.

## 관련 문서

- [도메인 모델과 상태 정책](../docs/domain-model.md)
- [ADR 0002: 금융거래 모델](../docs/adr/0002-financial-transaction-model.md)
- [ADR 0001: UNKNOWN 응답 정책](../docs/adr/0001-unknown-response-policy.md)
