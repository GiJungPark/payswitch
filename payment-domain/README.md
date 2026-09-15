[PaySwitch 홈](../README.md)

# payment-domain

결제 스위치의 순수 도메인 모델 모듈이다.

## 책임

- `Payment`, `FinancialTransaction`, `InstitutionMessageAttempt`의 경계와 상태 전이 규칙을 담는다.
- 금액 불변식, 통화와 minor unit 규칙을 framework 없이 표현한다.
- 현재는 build 기준선만 있으며 도메인 behavior는 후속 Issue에서 구현한다.

## 주요 진입점

- Source root: `src/main/kotlin/io/github/gijungpark/payswitch/domain/` (첫 도메인 타입과 함께 생성)
- Build: [build.gradle.kts](build.gradle.kts)

## 의존 방향

```text
payment-application ─┐
payment-infrastructure ┴─> payment-domain ─> (Kotlin 표준 라이브러리만)
```

- Spring 또는 다른 project module을 의존하지 않는다.
- `payment-api`는 이 모듈을 직접 의존하지 않는다.
- `bank-a-simulator`는 이 모듈을 의존하지 않는다.

## 실행·검증

실행 가능한 애플리케이션이 아닌 library 모듈이다.

```bash
./gradlew :payment-domain:build
./gradlew :payment-domain:dependencies
```

## 관련 문서

- [도메인 모델과 상태 정책](../docs/domain-model.md)
- [ADR 0002: 금융거래 모델](../docs/adr/0002-financial-transaction-model.md)
- [ADR 0001: UNKNOWN 응답 정책](../docs/adr/0001-unknown-response-policy.md)
