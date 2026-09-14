# 이벤트 계약과 전달 정책

## 역할

온라인 승인·취소 결과는 API 응답 경로에서 동기식으로 결정한다. Domain event는 거래 commit 이후의 정산 대상 생성, 통계, 알림과 운영 처리를 분리하기 위해 사용한다.

```text
FinancialTransaction 상태 변경 + Outbox event 저장
                     │ 같은 DB transaction
                     ▼
               DB poller 또는 Kafka
                     ▼
               Idempotent consumer
```

## Event envelope

```json
{
  "eventId": "01K...",
  "eventType": "PaymentCancellationSucceeded",
  "eventVersion": 1,
  "aggregateType": "PAYMENT",
  "aggregateId": "P202609130001",
  "occurredAt": "2026-09-13T10:00:00Z",
  "correlationId": "01K...",
  "causationId": "T202609130002",
  "payload": {}
}
```

- `eventId`는 전달 단위의 전역 유일 ID다.
- `aggregateId`는 동일 결제 이벤트의 ordering key로 사용한다.
- `causationId`에는 이벤트를 발생시킨 금융거래 또는 이전 event ID를 기록한다.
- payload 변경은 기존 필드 의미를 바꾸지 않고 새 event version으로 관리한다.

## Domain events

| Event | 발생 조건 | 주요 payload |
|---|---|---|
| `PaymentAuthorizationSucceeded` | 승인 거래 확정 성공 | transaction ID, 승인금액, 통화, 기관, 승인번호 |
| `PaymentAuthorizationDeclined` | 기관의 확정 거절 | transaction ID, 내부·기관 응답 코드 |
| `FinancialTransactionBecameUnknown` | 기관 처리 여부 불확실 | transaction ID, 거래 유형, failure stage |
| `PaymentCancellationSucceeded` | 전체 또는 부분 취소 성공 | 취소 거래 ID, 취소금액, 누적 취소금액, 잔액, `fullCancellation` |
| `PaymentReversalSucceeded` | 망취소 성공 | 망취소·원거래 ID, 기관 결과 |
| `ManualReviewRequired` | 자동 resolution 한도 초과 또는 결과 상충 | 관련 거래 ID, 원인, 마지막 확인 결과 |
| `SettlementTargetCreated` | 정산 입력 snapshot 생성 | 대상 거래 ID, 금액, 통화, 영업일 |
| `SettlementCompleted` | 정산 job 완료 | 정산 ID, 가맹점, 통화, 합계 |
| `ReconciliationMismatchDetected` | 기관 대사 불일치 발견 | 대사 ID, 불일치 유형, 내부·기관 값 |
| `BatchFailed` | batch job 실패 | job/step execution ID, 영업일, 오류 분류 |

부분 취소와 전체 취소는 `PaymentCancellationSucceeded` 하나를 사용한다. consumer가 다시 계산하지 않도록 event에 해당 취소금액, 누적 취소금액, 취소 가능 잔액과 `fullCancellation`을 함께 제공한다.

## 전달 보장

- 전달 방식은 at-least-once다.
- event 발생과 Outbox 저장은 업무 상태 변경과 같은 MySQL transaction에서 수행한다.
- consumer의 업무 반영과 `consumer_inbox` 저장도 같은 transaction에서 수행한다.
- 동일 `eventId`를 다시 받으면 성공한 no-op으로 처리한다.
- consumer는 서로 다른 aggregate의 전역 순서를 가정하지 않는다.
- 동일 aggregate의 역순 event를 감지하기 위해 aggregate version을 payload에 포함하며, 처리할 수 없는 gap은 retry 또는 격리한다.

## Kafka 도입 전후

### Milestone 3

DB poller가 미발행 Outbox row를 가져와 in-process consumer에 전달한다. 여러 poller가 실행될 때는 queue 성격의 row claiming을 사용한다.

### Milestone 6

Outbox publisher가 Kafka에 event를 발행하고 `paymentId`를 record key로 사용한다. 기존 consumer inbox와 event handler는 유지한다. Kafka 발행 성공 후 Outbox 완료 표시 전에 프로세스가 종료될 수 있으므로 중복 발행을 정상 상황으로 취급한다.

## 금지 사항

- event consumer에서 온라인 승인 결과를 뒤늦게 최초 결정하지 않는다.
- event 이름에 구현 기술이나 consumer 이름을 넣지 않는다.
- 기존 event payload의 필드 의미를 조용히 변경하지 않는다.
- consumer가 event payload 없이 현재 결제 상태만 조회하여 과거 event의 의미를 재구성하지 않는다.

