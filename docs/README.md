[PaySwitch 홈](../README.md)

# 문서 허브

이 디렉터리는 PaySwitch의 도메인, 외부기관 전문, 데이터, 이벤트와 운영 설계를 설명한다. 처음 읽는 사람과 AI agent는 아래 순서로 필요한 문서까지 이동한다.

## 권장 읽기 순서

| 순서 | 문서 | 설명 |
|---|---|---|
| 1 | [용어집](glossary.md) | PG, VAN, 전문, 망취소와 대사 용어 |
| 2 | [도메인 모델과 상태 정책](domain-model.md) | Payment와 금융거래 경계, 상태 전이 |
| 3 | [Bank A TCP 전문 v1](protocol-bank-a.md) | 고정 길이 전문과 correlation 규칙 |
| 4 | [데이터 모델](database-schema.md) | 테이블, 제약과 멱등성 |
| 5 | [이벤트 계약과 전달 정책](events.md) | Outbox, Inbox와 이벤트 계약 |
| 6 | [배치·외화·정산·대사](batch-and-settlement.md) | 후속 처리와 운영 배치 |
| 7 | [장애 시나리오](failure-scenarios.md) | timeout, 중복과 복구 검증 |
| 8 | [ADR 목록](adr/README.md) | 확정된 설계 결정과 변경 이력 |

## 디렉터리 탐색 규칙

- 저장소 루트, 각 Gradle 모듈과 독립적으로 이해해야 하는 주요 디렉터리에는 `README.md`를 둔다.
- 디렉터리 README에는 책임, 주요 진입점, 의존 방향, 실행·검증 방법과 관련 상세 문서 링크를 적는다.
- 하위 README는 상위 README로 돌아가는 breadcrumb를 제공한다.
- 단순 leaf package에는 README를 만들지 않는다. package와 타입의 역할은 이름과 KDoc으로 설명한다.
- 디렉터리 구조를 추가·이동·삭제하면 가장 가까운 README와 상위 문서 인덱스를 함께 갱신한다.

## 문서 갱신 규칙

모든 Issue와 PR은 `Documentation impact`를 작성한다. 동작, public contract, DB, 전문, 이벤트, 운영 방법 또는 디렉터리 책임이 바뀌면 같은 작업에서 가장 가까운 README와 상세 문서를 수정한다.

문서 내용이 달라지지 않는 순수 내부 변경은 문서를 억지로 수정하지 않고 `None — 문서화된 동작과 구조가 변하지 않음`처럼 이유를 남긴다. 문서 인덱스와 실제 파일 링크가 어긋난 상태로 작업을 완료하지 않는다.

다음 문서: [용어집](glossary.md)
