# Contributing Guide

이 문서는 PaySwitch의 Issue, branch, commit, Pull Request와 merge 규칙의 단일 기준이다.

## 역할 분담

| 역할 | 책임 |
|---|---|
| 사용자 | 목표 승인, 중요한 범위·설계 결정과 최종 merge 판단 |
| Codex | Issue와 작업 범위 작성, branch·commit·PR 관리, 코드 리뷰, Claude 관찰사항 기록과 최종 검증 |
| Claude Code | 승인된 Issue 범위의 코드·테스트·migration 구현과 범위 밖 관찰사항 보고 |

기본 흐름은 다음과 같다.

```text
사용자 목표
→ Codex Issue 작성
→ Codex branch 준비 및 Claude 지시
→ Claude 구현·테스트
→ Codex diff review·재검증
→ Codex commit·PR 작성
→ 사용자 확인·squash merge
```

## Issue 컨벤션

### 원칙

- Issue 하나는 PR 하나로 검증할 수 있는 결과 하나를 다룬다.
- 구현 전에 목표, 포함·제외 범위와 완료 조건을 합의한다.
- 완료 조건은 “구현한다”가 아니라 테스트나 관찰로 확인 가능한 문장으로 작성한다.
- 새로운 설계 결정은 Issue 본문에 숨기지 않고 ADR 필요 여부를 표시한다.
- 모든 Issue는 Risk tier와 Documentation impact를 명시한다.
- 후속 개선은 현재 Issue 범위에 계속 추가하지 않고 별도 Issue로 분리한다.

### 제목

```text
[Type] 간결한 결과 중심 제목
```

허용하는 Type은 다음과 같다.

| Type | 용도 |
|---|---|
| `Feature` | 사용자·운영자가 관찰할 수 있는 기능 |
| `Bug` | 잘못된 behavior 수정 |
| `Refactor` | behavior를 바꾸지 않는 구조 개선 |
| `Test` | 테스트 보강 |
| `Docs` | 문서만 변경 |
| `Chore` | 설정, dependency와 저장소 관리 |
| `Spike` | 구현 전 불확실성 검증 |

예시:

```text
[Chore] Kotlin Gradle 멀티모듈 프로젝트 구성
[Feature] 원화 승인 거래의 멱등성 보장
[Bug] 지연 승인 응답이 망취소 상태를 덮어쓰는 문제 수정
```

### 필수 본문

```markdown
## 배경
왜 필요한가?

## 목표
완료 후 무엇이 달라지는가?

## 포함 범위
- 이번 Issue에서 수행할 내용

## 제외 범위
- 의도적으로 수행하지 않을 내용

## 완료 조건
- [ ] 검증 가능한 조건

## 테스트 계획
- 실행할 테스트와 장애 시나리오

## 기술 메모
- 관련 문서, ADR, 제약과 위험

## Libraries / build plugins
- 이름, 용도와 버전 관리 방식; 없으면 None

## Risk tier
- Low / Medium / High와 판단 근거

## Documentation impact
- 수정할 가장 가까운 README와 상세 문서; 변경이 없으면 None과 이유
```

### Label

가능하면 type, area, priority를 각각 하나씩 사용한다.

```text
type:feature, type:bug, type:refactor, type:test, type:docs, type:chore, type:spike
area:domain, area:api, area:persistence, area:protocol, area:simulator,
area:connector, area:batch, area:event, area:admin, area:infra,
area:build, area:docs, area:ci
priority:p0, priority:p1, priority:p2, priority:p3
```

## Branch 컨벤션

```text
<type>/<issue-number>-<kebab-case-summary>
```

| Issue Type | Branch prefix |
|---|---|
| Feature | `feat/` |
| Bug | `fix/` |
| Refactor | `refactor/` |
| Test | `test/` |
| Docs | `docs/` |
| Chore | `chore/` |
| Spike | `spike/` |

예시:

```text
chore/1-gradle-multi-module
feat/12-authorization-idempotency
fix/31-late-authorization-response
```

규칙:

- 최신 `main`에서 branch를 만든다.
- `main`에 직접 작업 commit을 만들지 않는다.
- 영문 소문자, 숫자와 hyphen만 사용한다.
- 한 branch에서 여러 Issue를 처리하지 않는다.
- branch를 재사용하지 않는다.

## Commit 컨벤션

Conventional Commits 형식을 사용한다.

```text
<type>(<scope>): <imperative English summary>
```

### Type

```text
feat, fix, refactor, test, docs, chore, perf, build, ci
```

### Scope

```text
domain, api, persistence, protocol, connector, simulator,
batch, event, admin, infra, build, docs, adr, ci
```

예시:

```text
chore(build): scaffold Kotlin multi-module project
feat(domain): add authorization transaction state model
feat(persistence): enforce request idempotency
test(protocol): cover delayed and out-of-order responses
docs(adr): define unknown transaction resolution policy
```

규칙:

- 제목은 영문 명령형으로 쓰고 마침표를 붙이지 않는다.
- 제목은 72자를 넘기지 않는다.
- commit 하나에는 되돌릴 수 있는 논리적 변경 하나만 담는다.
- 테스트가 필요한 production code와 해당 테스트는 같은 commit에 포함한다.
- `WIP`, `fix`, `update`처럼 의도가 불분명한 제목을 사용하지 않는다.
- 이유나 trade-off가 제목에 드러나지 않으면 본문에 `what`보다 `why`를 설명한다.
- Issue 종료는 개별 commit보다 PR의 `Closes #번호`로 처리한다.

## Pull Request 컨벤션

### 제목

Squash commit으로 사용할 수 있도록 Commit 컨벤션과 같은 형식을 사용한다.

```text
feat(domain): add authorization transaction state model
```

### 본문

PR template의 모든 필수 항목을 작성한다.

- `Closes #번호`로 Issue를 연결한다.
- 변경 결과와 주요 설계 결정을 설명한다.
- 포함하지 않은 내용을 명시한다.
- 실제 실행한 검증 명령과 결과를 기록한다.
- 리뷰어가 집중해서 볼 위험과 파일을 표시한다.
- Claude가 구현 중 발견한 문제와 Codex의 확인·처리 결과를 `Claude observations`에 기록한다.
- API, schema, protocol, 디렉터리 책임 또는 운영 방식이 바뀌면 가장 가까운 README와 관련 문서·ADR을 연결한다.

### 크기와 상태

- 한 PR은 하나의 Issue와 하나의 주된 목적을 갖는다.
- 완료 조건을 만족하지 못하면 Draft PR로 유지한다.
- 생성 파일과 migration을 제외한 변경이 리뷰하기 어려울 정도로 커지면 Issue와 PR을 분리한다.
- 관계없는 formatting과 리팩터링을 섞지 않는다.

## Review와 Merge

Codex는 다음 순서로 검증한다.

1. Issue 범위와 diff 일치 여부
2. 설계 문서와 ADR 준수 여부
3. 실패·중복·동시성 경로
4. DB constraint와 transaction 경계
5. 테스트 실행 결과
6. 문서, migration과 운영 영향

리뷰는 Risk tier에 비례해 수행한다. 의미 리뷰는 기본 1회, Claude 수정과 집중 재리뷰는 1회로 제한한다. finding은 최대 5개로 통합하며 P0/P1은 현재 PR에서 해결하고, 범위 밖 P2는 후속 Issue로 분리한다. 사람의 PR 리뷰가 들어온 라운드에는 동일 범위의 독립 모델 리뷰를 기본적으로 반복하지 않는다.

사용자가 “PR에 리뷰 달았어”라고 알리면 Codex는 review와 미해결 inline comment를 수집해 범위 안의 변경을 같은 branch에 반영하고 push한다. 댓글 답변, conversation resolve와 merge는 별도 요청이 있을 때만 수행한다.

Claude는 승인된 Issue 밖의 문제를 직접 수정하지 않는다. 발견한 문제는 Codex에 보고하고, Codex는 근거를 확인해 PR의 `Claude observations`에 `Current fix`, `Follow-up candidate`, `Decision needed`, `Not reproduced` 또는 `Duplicate` 상태로 남긴다.

모든 필수 check가 통과하고 미해결 리뷰가 없을 때 사용자가 최종 merge를 승인한다. 사용자가 실행을 요청하면 Codex가 squash merge하며, squash commit 제목은 PR 제목을 그대로 사용한다.

## Definition of Done

- [ ] Issue 완료 조건이 모두 충족됐다.
- [ ] 범위 밖 변경이 없다.
- [ ] 필요한 테스트가 추가되고 로컬에서 통과했다.
- [ ] CI workflow가 구성된 경우 모든 필수 check가 통과했다.
- [ ] 실제 금융정보와 secret이 포함되지 않았다.
- [ ] Documentation impact를 기록하고 가장 가까운 README와 상세 문서를 갱신했거나, 변경이 없는 이유를 기록했다.
- [ ] README와 문서 인덱스의 상대 링크가 유효하다.
- [ ] API, DB, 전문 또는 이벤트 변경이 문서화됐다.
- [ ] 새 설계 결정이 ADR에 반영됐다.
- [ ] 실패·복구·운영 영향을 PR에 기록했다.
- [ ] Claude observations를 기록했거나 발견된 항목이 없음을 표시했다.

## 권장 GitHub 설정

- 기본 branch: `main`
- Pull Request 필수
- CI status check 필수
- conversation resolution 필수
- `main` force push와 deletion 금지
- merge 방식은 squash merge만 허용
- merge 후 작업 branch 자동 삭제
