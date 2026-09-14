# ADR 0003: 동기·비동기 처리 경계

[ADR 목록](README.md) · [문서 홈](../README.md) · [프로젝트 홈](../../README.md)

- 상태: Accepted
- 날짜: 2026-09-13

## Context

온라인 승인과 취소는 가능한 즉시 기관의 확정 결과를 반환해야 한다. 반면 정산 대상 생성, 통계와 운영 이벤트는 API latency와 분리할 수 있다. 모든 흐름을 Kafka로 처리하면 초기 복잡도가 커지고 온라인 결과 계약도 불명확해진다.

## Decision

- API부터 기관 요청·응답까지의 온라인 거래는 동기식으로 처리한다.
- 거래 상태와 Outbox event를 같은 MySQL 트랜잭션에 저장한다.
- 초기 Outbox transport는 DB poller를 사용한다.
- 정산 대상 생성, 통계와 알림은 멱등 consumer로 처리한다.
- Spring Batch는 event로 생성된 `settlement_target` snapshot을 읽는다.
- 이벤트 계약과 장애 처리가 안정된 후 transport를 Kafka로 확장한다.

## Consequences

- MVP는 Kafka 없이 실행할 수 있다.
- Kafka 도입 전에도 event 중복과 Outbox 장애를 먼저 검증할 수 있다.
- 온라인 거래는 외부 기관 latency의 영향을 받으므로 timeout과 기관별 bulkhead가 필요하다.
- Kafka로 변경해도 consumer inbox와 batch 입력 모델은 유지된다.
