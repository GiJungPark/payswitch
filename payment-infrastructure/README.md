[PaySwitch 홈](../README.md)

# payment-infrastructure

application port의 기술 구현을 담는 infrastructure 모듈이다.

## 책임

- 영속성, 기관 connector와 Outbox 같은 기술 adapter를 구현한다.
- 기관별 protocol mapping을 connector 경계 안에 둔다.
- 현재는 `payment`·`financial_transaction`·`idempotency_request` Flyway migration, insert·조회용 MyBatis 저장소 adapter, Spring transaction 기반 `UnitOfWork`와 승인 접수 use case 조립을 구현한다.
- 상태 변경 update, optimistic lock, 멱등키 만료 정리와 TCP connector는 후속 Issue에서 추가한다.

## 주요 진입점

Source root: `src/main/kotlin/io/github/gijungpark/payswitch/infrastructure/`

| 경로 | 책임 |
|---|---|
| [`V1__create_payment_and_financial_transaction.sql`](src/main/resources/db/migration/V1__create_payment_and_financial_transaction.sql) | 결제·금융거래 table과 PK, FK, unique, CHECK 제약 |
| [`V2__create_idempotency_request.sql`](src/main/resources/db/migration/V2__create_idempotency_request.sql) | 멱등키 table과 `(merchant_id, operation_type, idempotency_key)` PK, 금융거래 FK(여러 멱등키가 같은 거래 참조 가능), CHECK 제약 |
| `persistence/FlywayMigrationConfiguration` | context 시작 시 `classpath:db/migration`을 적용하는 Flyway bean |
| `persistence/payment/MyBatisPaymentRepository` | `PaymentRepository` 구현. ID와 `merchantId`·`clientReference`로 조회 |
| `persistence/transaction/MyBatisFinancialTransactionRepository` | `FinancialTransactionRepository` 구현. ID와 `paymentId`로 조회 |
| `persistence/idempotency/MyBatisIdempotencyRepository` | `IdempotencyRepository` 구현. `merchantId`·operation type·멱등키로 조회 |
| `persistence/SpringUnitOfWork` | `UnitOfWork` 구현. `TransactionTemplate`으로 새 transaction을 실행하고 멱등성 unique 위반을 분류 |
| `authorization/AuthorizationUseCaseConfiguration` | `AuthorizePaymentUseCase` bean과 UUID 기반 `AuthorizationIdGenerator` 조립 |
| `persistence/*/*Row`, `*Mapper` + 같은 package의 `*Mapper.xml` | row mapping, 명시적 SQL과 domain 복원 |

- Build: [build.gradle.kts](build.gradle.kts)

### Persistence rehydration

- `IdempotencyRequestRow`는 식별자와 멱등키를 원래 문자열, operation type을 enum 이름, request hash를 소문자 16진수로 보유한다. 값 검증에 실패하면 `PersistedStateRestoreException`으로 거절한다.
- `PaymentRow`와 `FinancialTransactionRow`는 금액을 통화 최소 단위 `Long`, 통화를 ISO 4217 코드, 식별자를 원래 문자열, enum을 이름으로 보유한다.
- 현재 지원 통화는 `KRW`뿐이다. migration의 통화 CHECK가 다른 통화 insert를 거절하고, 복원 경로도 `KRW`가 아닌 row를 거절한다.
- `toDomain()`은 domain의 공개 생성 factory와 상태 전이 method를 `(status, version)`에 대응하는 전이 경로대로 재생한다. private constructor나 reflection으로 상태를 주입하지 않는다.
- 도달할 수 없는 `(status, version)` 조합, 알 수 없는 enum, domain 검증 실패 또는 재생 결과와 row의 불일치는 `PersistedStateRestoreException`으로 거절한다.
- domain에 새 상태나 전이가 추가되면 복원 경로 표도 함께 갱신해야 하며, 갱신 전에는 해당 row를 복원하지 않고 실패한다.

### Transaction 경계와 DB 예외

- 저장소 adapter는 DB 오류를 변환하지 않는다. unique·PK 중복은 `org.springframework.dao.DuplicateKeyException`, FK와 CHECK 위반은 `DataIntegrityViolationException` 계열로 전파된다.
- `SpringUnitOfWork.execute`는 이미 진행 중인 transaction 안에서 호출하면 `IllegalStateException`으로 거절한다. 항상 새 transaction에서 결제, 금융거래, 멱등키 순서로 insert하고, 경쟁에서 진 요청은 rollback 후 autocommit 조회로 commit된 row를 다시 읽는다.
- rollback 후 `DataIntegrityViolationException`의 원인 `SQLException`이 MySQL 오류 1062이고 메시지 끝의 index 이름이 다음 중 하나일 때만 `UniqueConstraintViolationException`으로 변환한다.

| MySQL index | 분류 |
|---|---|
| `idempotency_request.PRIMARY` | `IDEMPOTENCY_KEY` |
| `payment.uk_payment_merchant_client_reference` | `MERCHANT_CLIENT_REFERENCE` |

- `payment.PRIMARY`, `financial_transaction.PRIMARY` 중복과 FK·CHECK 위반은 변환하지 않고 원래 Spring 예외로 전파한다.
- index 이름 해석은 table 이름을 포함하는 MySQL 8.0.19 이상 메시지 형식에 의존한다.

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
| `MyBatisIdempotencyRepositoryIntegrationTest` | 멱등키 저장·정확한 문자열 조회, 같은 거래를 참조하는 여러 멱등키, PK·FK 위반, operation type·hash CHECK |
| `AuthorizePaymentUseCaseIntegrationTest` | 최초 등록 row, 순차 중복·충돌의 row 불변, 재사용 시 새 멱등키 결합과 이후 상충 요청 거절, 등록·결합 실패 rollback, 무관한 PK 중복 전파, barrier로 동시에 시작한 요청의 단일 등록(같은 키는 기록 1개, 다른 키는 모두 같은 거래에 결합) |
| `IdempotencyUniqueConstraintClassifierTest` | MySQL duplicate 메시지의 index 이름 분류 |
| `SchemaConstraintIntegrationTest` | raw SQL의 중복, 음수·0 금액, `KRW` 외 통화(`USD` 포함)·잘못된 상태·type·version, 없는 결제 참조 거절 |
| `MyBatis*RepositoryIntegrationTest` | 도달 가능한 모든 상태의 저장·조회 동일성, 조회 조건, 중복 insert 거절과 두 저장소가 참여한 Spring transaction의 rollback |
| `*RowTest` | 복원 경계의 허용 상태 round-trip과 도달 불가능한 row 거절 |

## 관련 문서

- [데이터 모델](../docs/database-schema.md)
- [도메인 모델과 상태 정책](../docs/domain-model.md)
- [Bank A TCP 전문 v1](../docs/protocol-bank-a.md)
- [이벤트 계약과 전달 정책](../docs/events.md)
- [ADR 0004: 기관 simulator 독립 구현](../docs/adr/0004-independent-simulator.md)
