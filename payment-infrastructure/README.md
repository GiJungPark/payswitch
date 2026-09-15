[PaySwitch 홈](../README.md)

# payment-infrastructure

application port의 기술 구현을 담는 infrastructure 모듈이다.

## 책임

- 영속성, 기관 connector와 Outbox 같은 기술 adapter를 구현한다.
- 기관별 protocol mapping을 connector 경계 안에 둔다.
- 현재는 build 기준선만 있으며 MyBatis, TCP connector 등은 후속 Issue에서 추가한다.

## 주요 진입점

- Source root: `src/main/kotlin/io/github/gijungpark/payswitch/infrastructure/` (첫 adapter와 함께 생성)
- Build: [build.gradle.kts](build.gradle.kts)

## 의존 방향

```text
payment-api ─> payment-infrastructure ─┬─> payment-application
                                       └─> payment-domain
```

- project dependency는 [payment-application](../payment-application/README.md)과 [payment-domain](../payment-domain/README.md)으로 제한한다.
- `bank-a-simulator`의 codec이나 코드를 공유하지 않는다.

## 실행·검증

실행 가능한 애플리케이션이 아닌 library 모듈이다. [payment-api](../payment-api/README.md)가 조립하여 실행한다.

```bash
./gradlew :payment-infrastructure:build
./gradlew :payment-infrastructure:dependencies
```

## 관련 문서

- [데이터 모델](../docs/database-schema.md)
- [Bank A TCP 전문 v1](../docs/protocol-bank-a.md)
- [이벤트 계약과 전달 정책](../docs/events.md)
- [ADR 0004: 기관 simulator 독립 구현](../docs/adr/0004-independent-simulator.md)
