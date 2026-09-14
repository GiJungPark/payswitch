# Architecture Decision Records

[문서 홈](../README.md) · [프로젝트 홈](../../README.md)

| ADR | 상태 | 결정 |
|---|---|---|
| [0001](0001-unknown-response-policy.md) | Accepted | 불확실한 기관 응답을 `UNKNOWN`과 `202 Accepted`로 처리한다. |
| [0002](0002-financial-transaction-model.md) | Accepted | 결제 요약과 승인·취소·망취소 금융거래를 분리한다. |
| [0003](0003-sync-async-boundary.md) | Accepted | 온라인 기관 통신은 동기, 후속 처리는 Outbox 기반 비동기로 구성한다. |
| [0004](0004-independent-simulator.md) | Accepted | 기관 simulator의 codec을 결제 스위치와 독립적으로 구현한다. |

ADR은 이미 내린 결정을 설명한다. 결정이 변경되면 기존 문서를 지우지 않고 대체 ADR을 추가하여 변경 이유를 남긴다.
