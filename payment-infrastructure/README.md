[PaySwitch 홈](../README.md)

# payment-infrastructure

application port의 기술 구현을 담는 infrastructure 모듈이다.

## 책임

- 영속성, 기관 connector와 Outbox 같은 기술 adapter를 구현한다.
- 기관별 protocol mapping을 connector 경계 안에 둔다.
- 현재는 `payment`·`financial_transaction` Flyway migration과 insert·조회용 MyBatis 저장소 adapter를 구현한다.
- 상태 변경 update, optimistic lock, 멱등성 저장소와 TCP connector는 후속 Issue에서 추가한다.

## 주요 진입점

Source root: `src/main/kotlin/io/github/gijungpark/payswitch/infrastructure/`

| 경로 | 책임 |
|---|---|
| [`V1__create_payment_and_financial_transaction.sql`](src/main/resources/db/migration/V1__create_payment_and_financial_transaction.sql) | 결제·금융거래 table과 PK, FK, unique, CHECK 제약 |
| `persistence/FlywayMigrationConfiguration` | context 시작 시 `classpath:db/migration`을 적용하는 Flyway bean |
| `persistence/payment/MyBatisPaymentRepository` | `PaymentRepository` 구현. ID와 `merchantId`·`clientReference`로 조회 |
| `persistence/transaction/MyBatisFinancialTransactionRepository` | `FinancialTransactionRepository` 구현. ID와 `paymentId`로 조회 |
| `persistence/*/*Row`, `*Mapper` + 같은 package의 `*Mapper.xml` | row mapping, 명시적 SQL과 domain 복원 |

- Build: [build.gradle.kts](build.gradle.kts)

### Persistence rehydration

- `PaymentRow`와 `FinancialTransactionRow`는 금액을 통화 최소 단위 `Long`, 통화를 ISO 4217 코드, 식별자를 원래 문자열, enum을 이름으로 보유한다.
- 현재 지원 통화는 `KRW`뿐이다. migration의 통화 CHECK가 다른 통화 insert를 거절하고, 복원 경로도 `KRW`가 아닌 row를 거절한다.
- `toDomain()`은 domain의 공개 생성 factory와 상태 전이 method를 `(status, version)`에 대응하는 전이 경로대로 재생한다. private constructor나 reflection으로 상태를 주입하지 않는다.
- 도달할 수 없는 `(status, version)` 조합, 알 수 없는 enum, domain 검증 실패 또는 재생 결과와 row의 불일치는 `PersistedStateRestoreException`으로 거절한다.
- domain에 새 상태나 전이가 추가되면 복원 경로 표도 함께 갱신해야 하며, 갱신 전에는 해당 row를 복원하지 않고 실패한다.

### DB 예외

- unique·PK 중복은 `org.springframework.dao.DuplicateKeyException`, FK와 CHECK 위반은 `DataIntegrityViolationException` 계열로 전파된다.
- application 전용 예외로의 변환은 멱등성 Issue에서 정한다.

## 의존 방향

```text
payment-api ─> payment-infrastructure ─┬─> payment-application
                                       └─> payment-domain
```

- project dependency는 [payment-application](../payment-application/README.md)과 [payment-domain](../payment-domain/README.md)으로 제한한다.
- MyBatis, Spring, Flyway와 JDBC 타입은 이 모듈 안에만 두며 domain과 application port에 노출하지 않는다.
- `bank-a-simulator`의 codec이나 코드를 공유하지 않는다.

| Dependency | 용도 | 버전 관리 |
|---|---|---|
| `org.mybatis.spring.boot:mybatis-spring-boot-starter` | MyBatis mapper와 Spring JDBC 조립 | `4.0.0` 명시 |
| `org.flywaydb:flyway-core`, `flyway-mysql` | versioned migration | Spring Boot BOM |
| `com.mysql:mysql-connector-j` | MySQL JDBC driver (runtime) | Spring Boot BOM |
| `org.springframework.boot:spring-boot-starter-test` | 통합 테스트 context (test) | Spring Boot BOM |
| `org.testcontainers:testcontainers-mysql` | 실제 MySQL container (test) | Spring Boot BOM의 Testcontainers `2.0.5` |

Spring Boot 4의 Flyway auto-configuration은 별도 `spring-boot-flyway` module에 있으므로 이 모듈은 `FlywayMigrationConfiguration`에서 `Flyway` bean을 직접 등록한다. `spring.flyway.*` 속성은 적용되지 않는다.

## 실행·검증

실행 가능한 애플리케이션이 아닌 library 모듈이다. [payment-api](../payment-api/README.md)가 조립하여 실행한다.

통합 테스트는 Docker가 필요하며 H2나 mock으로 대체하지 않는다. `MySqlIntegrationTest`가 `mysql:8.4` Testcontainer를 JVM당 한 번 시작하고 빈 DB에 migration을 적용한다.

```bash
./gradlew :payment-infrastructure:clean :payment-infrastructure:test
./gradlew :payment-infrastructure:build
./gradlew :payment-infrastructure:dependencies
```

| 테스트 | 검증 |
|---|---|
| `MigrationSchemaIntegrationTest` | migration 이력, column 타입과 문서화된 제약 이름·구성 |
| `SchemaConstraintIntegrationTest` | raw SQL의 중복, 음수·0 금액, `KRW` 외 통화(`USD` 포함)·잘못된 상태·type·version, 없는 결제 참조 거절 |
| `MyBatis*RepositoryIntegrationTest` | 도달 가능한 모든 상태의 저장·조회 동일성, 조회 조건, 중복 insert 거절과 두 저장소가 참여한 Spring transaction의 rollback |
| `*RowTest` | 복원 경계의 허용 상태 round-trip과 도달 불가능한 row 거절 |

## 관련 문서

- [데이터 모델](../docs/database-schema.md)
- [도메인 모델과 상태 정책](../docs/domain-model.md)
- [Bank A TCP 전문 v1](../docs/protocol-bank-a.md)
- [이벤트 계약과 전달 정책](../docs/events.md)
- [ADR 0004: 기관 simulator 독립 구현](../docs/adr/0004-independent-simulator.md)
