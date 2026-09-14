# 도메인 모델과 상태 정책

[문서 홈](README.md) · [프로젝트 홈](../README.md)

## 모델 경계

하나의 상태 값으로 결제 전체와 모든 금융거래를 표현하지 않는다.

```text
Payment
├── Authorization FinancialTransaction
├── Cancellation FinancialTransaction 0..N
├── Reversal FinancialTransaction 0..N
└── InstitutionMessageAttempt 1..N per transaction
```

### Payment

가맹점 주문에 대응하는 결제 전체의 현재 상태와 금액 합계를 관리한다.

```text
paymentId
merchantId
clientReference
approvedAmount
cancelledAmount
reservedCancelAmount
transactionCurrency
status
version
```

주요 불변식은 다음과 같다.

- `cancelledAmount + reservedCancelAmount`는 `approvedAmount`를 초과할 수 없다.
- 승인되지 않은 결제는 취소할 수 없다.
- 전액 취소된 결제는 추가 취소할 수 없다.
- `clientReference`는 가맹점의 최초 승인 요청 단위로 유일하다.

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> APPROVED
    CREATED --> DECLINED
    CREATED --> FAILED
    CREATED --> RESOLUTION_REQUIRED
    RESOLUTION_REQUIRED --> APPROVED
    RESOLUTION_REQUIRED --> DECLINED
    RESOLUTION_REQUIRED --> FAILED
    RESOLUTION_REQUIRED --> MANUAL_REVIEW_REQUIRED
    APPROVED --> PARTIALLY_CANCELLED
    APPROVED --> CANCELLED
    PARTIALLY_CANCELLED --> PARTIALLY_CANCELLED
    PARTIALLY_CANCELLED --> CANCELLED
    APPROVED --> MANUAL_REVIEW_REQUIRED
    PARTIALLY_CANCELLED --> MANUAL_REVIEW_REQUIRED
```

`Payment.status`는 요약 상태다. 승인이나 취소의 자세한 진행 상황은 `FinancialTransaction.status`에서 확인한다.

### FinancialTransaction

기관에 전달되는 승인, 취소 및 망취소를 각각 독립된 거래로 관리한다.

```text
transactionId
paymentId
type: AUTHORIZE | CANCEL | REVERSAL
originalTransactionId
idempotencyKey
requestHash
amount
currency
status
```

```mermaid
stateDiagram-v2
    [*] --> RECEIVED
    RECEIVED --> PROCESSING
    PROCESSING --> SUCCEEDED
    PROCESSING --> DECLINED
    PROCESSING --> FAILED: definitely not delivered
    PROCESSING --> UNKNOWN: may have been delivered
    UNKNOWN --> SUCCEEDED: authoritative inquiry or valid late response
    UNKNOWN --> DECLINED: authoritative inquiry
    UNKNOWN --> FAILED: confirmed not processed
    UNKNOWN --> MANUAL_REVIEW_REQUIRED: resolution policy exhausted
```

`FAILED`와 `DECLINED`는 다르다.

- `DECLINED`: 기관이 전문을 정상적으로 처리하고 업무상 거절했다.
- `FAILED`: routing 실패, 기관 점검 또는 연결 전 실패처럼 기관이 거래를 처리하지 않았음을 확정할 수 있다.
- `UNKNOWN`: bytes 전송 이후 timeout처럼 기관 처리 여부를 확정할 수 없다.

### InstitutionMessageAttempt

하나의 금융거래에 대한 실제 송수신 시도를 기록한다. 금융거래를 자동 재전송하지 않더라도 상태 조회 과정에서 여러 attempt가 생길 수 있다.

```text
attemptId
transactionId
institutionId
businessDate
traceNumber
messageType
attemptNumber
status
sentAt
receivedAt
failureReason
```

상태는 `CREATED`, `SENT`, `RESPONSE_RECEIVED`, `TIMED_OUT`, `CONNECTION_FAILED`, `MALFORMED_RESPONSE`를 사용한다.

## API 결과 정책

| 상황 | HTTP | 거래 상태 | 정책 |
|---|---:|---|---|
| 요청 형식 오류 | 400 | 생성하지 않음 | 필드 오류를 반환한다. |
| 동일 멱등키·동일 요청 | 이전과 동일 | 기존 상태 | 새 기관 요청 없이 기존 응답을 반환한다. |
| 동일 멱등키·다른 요청 | 409 | 기존 상태 유지 | `IDEMPOTENCY_KEY_REUSED`를 반환한다. |
| 기관의 확정 승인 | 200 | `SUCCEEDED` | 승인번호와 결과를 반환한다. |
| 기관의 확정 거절 | 200 | `DECLINED` | 기관 응답 코드를 내부 코드로 변환한다. |
| 기관 전달 전 확정 실패 | 503 | `FAILED` | route/점검/연결 실패 사유를 반환한다. |
| 처리 여부 불확실 | 202 | `UNKNOWN` | 거래 ID, 조회 URL과 재조회 권고 시간을 반환한다. |

`UNKNOWN` 응답 예시는 다음과 같다.

```http
HTTP/1.1 202 Accepted
Location: /v1/payments/P202609130001
Retry-After: 3
```

```json
{
  "paymentId": "P202609130001",
  "transactionId": "T202609130001",
  "status": "UNKNOWN",
  "message": "기관 처리 결과를 확인 중입니다."
}
```

## UNKNOWN 해결 정책

1. `UNKNOWN`이 된 승인·취소·망취소 전문을 자동으로 다시 보내지 않는다.
2. 기관별 설정에 따라 조회 전문을 제한된 횟수와 시간 안에서 재시도한다.
3. 조회 결과 `NOT_FOUND`는 곧바로 거절로 확정하지 않고 resolution window 동안 `UNKNOWN`을 유지한다.
4. 최종 조회 결과가 권위 있는 결과라면 해당 금융거래를 확정한다.
5. 기관 계약이 허용하면 별도의 `REVERSAL` 거래를 생성한다.
6. 망취소도 결과가 불확실하거나 해결 한도를 초과하면 결제를 `MANUAL_REVIEW_REQUIRED`로 전환한다.

조회 횟수, 간격, resolution window 및 망취소 지원 여부는 기관별 정책으로 관리한다.

## 늦은 응답 정책

- 아직 `UNKNOWN`이고 보상 거래가 시작되지 않았다면 MAC, trace number와 원요청 내용을 검증한 뒤 확정 결과로 반영할 수 있다.
- 동일한 확정 결과가 다시 도착하면 중복 응답으로 기록하고 상태를 변경하지 않는다.
- 이미 망취소가 시작됐거나 늦은 응답이 현재 상태와 충돌하면 자동으로 상태를 뒤집지 않는다.
- 상충 결과는 기관 조회 결과로 다시 확인하고 `reconciliation_mismatch`와 운영자 감사 이력에 남긴다.

## 취소 정책

- 전체 취소와 부분 취소는 모두 원승인을 참조하는 별도 `CANCEL` 거래다.
- 각 취소 요청은 독립된 멱등키와 요청 hash를 가진다.
- 동시에 실행되는 부분 취소는 Payment의 `version`과 조건부 금액 갱신으로 보호한다.
- 취소 거래가 `UNKNOWN`이면 확정 전까지 해당 금액을 `reservedCancelAmount`로 예약하여 추가 취소가 잔액을 초과하지 않게 한다.
- 최종 실패가 확인되면 예약 금액을 해제한다.
