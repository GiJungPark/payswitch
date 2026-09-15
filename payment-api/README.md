[PaySwitch 홈](../README.md)

# payment-api

application과 infrastructure를 조립하는 PaySwitch Spring Boot 실행 모듈이다.

## 책임

- Spring Boot servlet application으로 결제 스위치 프로세스를 실행한다.
- `payment-application`과 `payment-infrastructure`의 component를 하나의 application context로 조립한다.
- executable jar를 만든다.
- 현재는 application context만 구성하며 REST payment API는 후속 Issue에서 구현한다.

## 주요 진입점

- [PaymentApiApplication.kt](src/main/kotlin/io/github/gijungpark/payswitch/PaymentApiApplication.kt): `main` 함수. package root `io.github.gijungpark.payswitch`에 두어 하위 package 전체를 component scan한다.
- [application.yaml](src/main/resources/application.yaml): 기본 설정
- [PaymentApiApplicationTests.kt](src/test/kotlin/io/github/gijungpark/payswitch/PaymentApiApplicationTests.kt): application context smoke test
- Build: [build.gradle.kts](build.gradle.kts)

## 의존 방향

```text
payment-api ─┬─> payment-application ─> payment-domain
             └─> payment-infrastructure
```

- project dependency는 [payment-application](../payment-application/README.md)과 [payment-infrastructure](../payment-infrastructure/README.md)으로 제한한다.
- [payment-domain](../payment-domain/README.md)을 직접 의존하지 않는다.
- Library dependency 버전은 Spring Boot BOM으로 관리한다.

## 실행·검증

Java 21 toolchain이 필요하다.

```bash
./gradlew :payment-api:bootRun
./gradlew :payment-api:test
./gradlew :payment-api:bootJar
java -jar payment-api/build/libs/payment-api-0.0.1-SNAPSHOT.jar
```

## 관련 문서

- [도메인 모델과 상태 정책](../docs/domain-model.md)
- [ADR 0001: UNKNOWN 응답 정책](../docs/adr/0001-unknown-response-policy.md)
- [ADR 0003: 동기·비동기 경계](../docs/adr/0003-sync-async-boundary.md)
- [ADR 0004: 기관 simulator 독립 구현](../docs/adr/0004-independent-simulator.md)
