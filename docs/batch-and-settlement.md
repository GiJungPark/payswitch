# 배치·외화·정산·대사

[문서 홈](README.md) · [프로젝트 홈](../README.md)

## 온라인 거래와 배치의 경계

Milestone 3에서 거래 상태 변경과 Outbox event를 같은 트랜잭션에 저장한다. Kafka 도입 전에는 DB poller가 event를 소비하여 `settlement_target`을 멱등하게 만든다.

```text
FinancialTransaction commit
        +
Outbox event commit
        │
   DB Outbox poller
        │
SettlementTarget snapshot
        │
   Spring Batch
        │
Settlement / Reconciliation
```

Milestone 6에서는 transport를 Kafka로 변경하지만 event 계약, inbox 중복 처리와 Spring Batch 입력 모델은 유지한다.

## 외화 정책

### 통화 모델

```text
transaction_amount_minor
transaction_currency
settlement_amount_minor
settlement_currency
applied_exchange_rate
exchange_rate_applied_at
exchange_rate_source
```

- DCC는 구현하지 않는다. 승인 금액과 승인 통화는 요청 거래와 동일하다.
- 거래 통화와 가맹점 정산 통화가 다를 때만 환율을 적용한다.
- 프로젝트의 기본 정책은 승인 성공 시점에 환율을 고정하는 `LOCK_AT_AUTHORIZATION`이다.
- 적용 환율과 계산 결과를 거래 snapshot으로 저장하여 이후 환율 변경이 과거 정산을 바꾸지 않게 한다.
- 환율 계산과 수수료 계산의 순서, scale과 rounding mode를 명시적으로 관리한다.

### 부분 취소와 반올림

- 부분 취소는 원승인에 저장된 환율을 사용한다.
- 각 부분 취소의 정산 통화 금액은 정의된 rounding mode로 계산한다.
- 마지막 전액 취소에서는 개별 계산을 다시 하지 않고 `원승인 정산금액 - 이전 취소 정산금액 합계`를 사용한다.
- 이에 따라 여러 부분 취소 이후에도 정산 통화 취소 합계가 원승인 정산금액과 정확히 일치한다.

## 일 마감과 정산

정산 기준 시각 이전에 최종 확정된 거래만 대상에 포함한다. `UNKNOWN`과 `MANUAL_REVIEW_REQUIRED` 거래는 자동 정산에서 제외하고 운영 지표와 대사 대상으로 분류한다.

```text
settlement_target 고정
→ 기관/가맹점/통화별 집계
→ 승인·취소 금액 계산
→ 수수료 계산
→ settlement_detail 저장
→ settlement 합계 저장
→ 결과 파일 생성
```

### 재실행 정책

- JobInstance는 `jobName + settlementBusinessDate + institutionId`로 식별한다.
- 실패한 execution은 완료되지 않은 step부터 재시작한다.
- 이미 성공한 동일 JobInstance의 일반 재실행은 거절한다.
- 운영자가 재처리를 요청하면 별도의 운영 요청 ID와 사유를 남긴다.
- `settlement`와 `settlement_target`의 유일 제약으로 중복 반영을 방지한다.
- writer는 chunk 재수행을 고려해 upsert 또는 조건부 상태 전이를 사용한다.

## 기관 대사

Bank A simulator는 영업일별 기관 원장 파일을 독립적으로 생성한다. PaySwitch는 이를 읽어 내부 거래 snapshot과 비교한다.

| 불일치 | 설명 | 기본 처리 |
|---|---|---|
| `PG_ONLY` | PG에만 확정 거래가 있음 | 기관 조회 후 수동 확인 |
| `INSTITUTION_ONLY` | 기관에만 거래가 있음 | 원전문 검색 후 망취소 검토 |
| `STATUS_MISMATCH` | 승인·취소 상태가 다름 | 기관 조회 결과로 resolution |
| `AMOUNT_MISMATCH` | 금액 또는 통화가 다름 | 자동 정산 제외 |
| `DUPLICATE` | 기관 또는 PG 내역이 중복됨 | 중복 원인 조사 |
| `UNRESOLVED` | `UNKNOWN` 해결 기한 초과 | 수동 확인 필수 |

대사 결과는 자동으로 과거 거래를 덮어쓰지 않는다. 변경이 필요하면 보정 사유, 이전 값, 변경 값과 운영자를 감사 로그에 남긴다.

## 배치 실패 정책

- 일시적인 DB lock 또는 기관 파일 접근 오류는 제한된 횟수만 retry한다.
- 필드 하나가 잘못된 기관 row는 skip하고 오류 원문과 사유를 별도 기록한다.
- 금액 합계 불일치나 필수 파일 누락은 job 전체를 실패시킨다.
- 일부 기관의 실패가 다른 기관 정산을 막지 않도록 기관별 JobInstance를 사용한다.
- 실행별 read/write/skip/retry count와 처리 시간을 Micrometer로 노출한다.
