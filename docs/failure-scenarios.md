# 장애 시나리오

[문서 홈](README.md) · [프로젝트 홈](../README.md)

모든 장애 테스트는 기관 호출 횟수, 금융거래 상태, 결제 요약 상태, API 결과와 복구 결과를 함께 검증한다.

## 온라인 거래

| 시나리오 | 즉시 결과 | 저장 상태 | 복구 및 검증 |
|---|---|---|---|
| validation 실패 | `400` | 금융거래 미생성 | 기관 호출 0회 |
| 동일 멱등키·동일 요청 | 기존 거래의 현재 결과 | 기존 상태 유지 | 기관 호출 추가 없음 |
| 동일 멱등키·다른 요청 | `409` | 기존 상태 유지 | 요청 hash 충돌 기록 |
| routing 규칙 없음 | `503` | `FAILED` | 기관 호출 0회 |
| 기관 점검 상태 | `503` | `FAILED` | 해당 기관 connection 미사용 |
| connection 획득 실패 | `503` | `FAILED` | bytes 미전송이 확실한지 검증 |
| 기관 업무 거절 | `200` | `DECLINED` | 응답 코드 mapping 검증 |
| write 성공 후 timeout | `202` | `UNKNOWN` | 승인 재전송 없이 조회 수행 |
| 승인 후 응답 유실 | `202` | `UNKNOWN` | 조회 결과로 `SUCCEEDED` 전환 |
| 지연 승인 응답 | `202` 후 상태 변경 | `UNKNOWN` 후 `SUCCEEDED` | MAC·trace·원요청 일치 검증 |
| 잘못된 길이 또는 MAC | `202` | `UNKNOWN` | 잘못된 응답 격리 후 조회 수행 |
| 중복 확정 응답 | 기존 결과 | 상태 유지 | 중복 metric과 attempt 기록 |
| 알 수 없는 trace 응답 | 영향 없음 | 상태 유지 | orphan 응답 격리와 경고 기록 |

## UNKNOWN 해결

### 조회 결과가 거래 없음

`NOT_FOUND` 한 번으로 승인 실패를 확정하지 않는다.

```text
UNKNOWN
  → 조회 NOT_FOUND
  → resolution window 동안 backoff 조회
  → 확정 결과 수신: SUCCEEDED 또는 FAILED
  → 해결 기한 초과: REVERSAL 생성 또는 MANUAL_REVIEW_REQUIRED
```

조회 횟수와 시간은 test clock을 사용해 실제 대기 없이 검증한다.

### 망취소 timeout

망취소는 원승인의 상태 필드가 아니라 별도 `REVERSAL` 금융거래다.

```text
Authorization = UNKNOWN
Reversal = UNKNOWN
Payment = MANUAL_REVIEW_REQUIRED
```

동일 망취소를 자동 재전송하지 않고 조회·대사 결과로 해결한다.

### 망취소 이후 늦은 승인 응답

늦은 승인 결과만 보고 Payment를 자동으로 `APPROVED`로 변경하지 않는다. 승인과 망취소 결과가 상충할 수 있으므로 기관 최종 조회를 수행하고 대사 불일치로 기록한다.

## 취소와 동시성

### 동시 부분 취소

승인 10,000원에 대해 7,000원과 5,000원 취소를 동시에 요청한다.

- 하나의 요청만 기관으로 전달돼야 한다.
- 다른 요청은 취소 가능 금액 부족으로 거절돼야 한다.
- `cancelledAmount + reservedCancelAmount <= approvedAmount`가 항상 유지돼야 한다.

### 취소 결과 불확실

5,000원 취소가 `UNKNOWN`이면 5,000원을 `reservedCancelAmount`로 유지한다. 결과 확정 전에는 같은 잔액을 사용하는 추가 취소를 허용하지 않는다.

- 최종 성공: reserved 금액을 cancelled 금액으로 이동한다.
- 최종 실패: reserved 금액을 해제한다.
- 해결 기한 초과: 예약을 유지하고 수동 확인 대상으로 전환한다.

### 외화 부분 취소 반올림

여러 부분 취소를 순차 실행한 뒤 마지막 취소가 남은 거래 통화와 정산 통화 금액을 모두 소진하는지 검증한다. 부분 취소 정산금액 합계는 원승인 정산금액과 정확히 같아야 한다.

## 자원 격리

- Bank A의 모든 요청을 timeout으로 만들어도 Bank B 승인 latency와 thread 수가 영향을 받지 않아야 한다.
- Bank A의 in-flight 제한을 초과한 요청은 정해진 시간 안에 fail-fast 해야 한다.
- reconnect loop는 상한이 있는 backoff를 사용하고 CPU busy loop를 만들지 않아야 한다.
- connection 교체 중 완료되지 않은 요청은 성공으로 추정하지 않고 `UNKNOWN`으로 이동해야 한다.

## Outbox와 consumer

| 장애 시점 | 기대 결과 |
|---|---|
| 거래 commit 전 종료 | 거래와 Outbox가 모두 rollback된다. |
| 거래·Outbox commit 직후 종료 | 재기동한 poller가 미발행 event를 처리한다. |
| event 전송 후 `published_at` 갱신 전 종료 | event가 중복 전달돼도 inbox가 중복 반영을 막는다. |
| consumer 업무 반영 전 종료 | event가 다시 처리된다. |
| consumer 업무 반영과 inbox commit 후 종료 | 재전달된 event가 no-op 처리된다. |
| Kafka 처리 후 offset commit 전 종료 | 재수신되지만 업무 결과는 중복되지 않는다. |

## 배치

- chunk commit 이전 실패는 해당 chunk 전체가 다시 처리된다.
- chunk commit 이후 종료는 완료된 chunk를 중복 반영하지 않는다.
- 같은 영업일 JobInstance의 중복 동시 실행은 하나만 허용한다.
- 기관 원장 파일 누락 시 정산을 성공으로 표시하지 않는다.
- 대사 row 일부가 잘못되어도 오류 정책에 따라 skip 내역과 원인을 남긴다.
- 성공한 배치를 운영자가 재처리할 때 새 운영 요청 ID와 사유가 반드시 필요하다.

## 관찰 가능성 검증

각 시나리오에서 다음 식별자가 로그와 상태 이력을 통해 연결돼야 한다.

```text
paymentId
transactionId
attemptId
institutionTraceNumber
correlationId
eventId
batchExecutionId
```

전문 payload는 마스킹하고 상태 전이 사유, 기관, latency와 failure stage는 검색 가능하게 기록한다.
