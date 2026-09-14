# ADR 0001: 불확실한 기관 응답 정책

[ADR 목록](README.md) · [문서 홈](../README.md) · [프로젝트 홈](../../README.md)

- 상태: Accepted
- 날짜: 2026-09-13

## Context

금융기관이 승인·취소 요청을 처리한 뒤 응답이 유실되면 PaySwitch는 실제 처리 결과를 알 수 없다. 이를 실패로 단정하고 같은 요청을 다시 전송하면 이중 승인이나 이중 취소가 발생할 수 있다.

동시에 API 호출자는 timeout을 단순 서버 오류로만 받으면 안전한 재요청 방법을 알 수 없다.

## Decision

- bytes가 기관에 전달됐을 가능성이 있는 상태에서 확정 응답을 받지 못하면 금융거래를 `UNKNOWN`으로 기록한다.
- API는 `202 Accepted`, 결제·거래 ID, 조회 URL과 `Retry-After`를 반환한다.
- 동일 승인·취소·망취소 전문은 자동 재전송하지 않는다.
- 기관별 resolution policy에 따라 조회 전문을 전송한다.
- `NOT_FOUND` 한 번으로 실패를 확정하지 않는다.
- 필요한 경우 별도의 `REVERSAL` 금융거래를 만든다.
- resolution window를 초과하면 `MANUAL_REVIEW_REQUIRED`로 전환한다.
- 늦은 결과가 이미 시작된 보상 거래와 충돌하면 자동 확정하지 않고 기관 조회와 대사로 해결한다.

## Consequences

- 호출자는 확정 결과 외에 처리 중 상태를 다뤄야 한다.
- 상태 조회 API와 background resolver가 필요하다.
- 해결되지 않은 거래를 위한 운영 화면과 지표가 필요하다.
- 단순 retry보다 구현은 복잡하지만 이중 금융거래 위험을 줄인다.
