[PaySwitch 홈](../README.md)

# bank-a-simulator

PaySwitch와 독립된 프로세스로 실행하는 Bank A 기관 simulator 모듈이다.

## 책임

- 공개 전문 명세를 기준으로 Bank A 기관 역할을 독립적으로 구현한다.
- 이후 timeout, 응답 유실, 지연, 중복, 순서 변경, 잘못된 MAC과 connection 종료를 주입한다.
- executable jar를 만든다.
- 현재는 application context만 구성한다. TCP server가 없으므로 실행하면 context를 시작한 뒤 바로 종료한다. codec과 socket 통신은 후속 Issue에서 구현한다.

## 주요 진입점

- [BankASimulatorApplication.kt](src/main/kotlin/io/github/gijungpark/payswitch/simulator/banka/BankASimulatorApplication.kt): `main` 함수
- [application.yaml](src/main/resources/application.yaml): 기본 설정
- [BankASimulatorApplicationTests.kt](src/test/kotlin/io/github/gijungpark/payswitch/simulator/banka/BankASimulatorApplicationTests.kt): application context smoke test
- Build: [build.gradle.kts](build.gradle.kts)

## 의존 방향

```text
bank-a-simulator ─> (Spring Boot, Kotlin만)
```

- 어떤 PaySwitch project module(`payment-domain`, `payment-application`, `payment-infrastructure`, `payment-api`)도 의존하지 않는다.
- PaySwitch와는 [Bank A TCP 전문 v1](../docs/protocol-bank-a.md)과 수작업 검증 golden byte fixture만 계약으로 공유한다.
- Library dependency 버전은 Spring Boot BOM으로 관리한다.

## 실행·검증

Java 21 toolchain이 필요하다.

```bash
./gradlew :bank-a-simulator:bootRun
./gradlew :bank-a-simulator:test
./gradlew :bank-a-simulator:bootJar
java -jar bank-a-simulator/build/libs/bank-a-simulator-0.0.1-SNAPSHOT.jar
./gradlew :bank-a-simulator:dependencies
```

## 관련 문서

- [ADR 0004: 기관 simulator 독립 구현](../docs/adr/0004-independent-simulator.md)
- [Bank A TCP 전문 v1](../docs/protocol-bank-a.md)
- [장애 시나리오](../docs/failure-scenarios.md)
