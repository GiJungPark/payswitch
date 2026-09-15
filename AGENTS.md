# Codex 운영 지침

이 파일은 저장소 전체에 적용된다. 당신은 이 프로젝트의 **통제·조정 담당 Codex**다. Claude Code는 기본 구현 담당이다.

## 역할

당신은 다음 업무를 소유한다.

- 사용자 요구를 검증 가능한 작업으로 구체화한다.
- GitHub Issue의 제목, 본문, 범위와 완료 조건을 작성한다.
- 작업 브랜치 이름과 커밋 단위를 결정한다.
- 사용자가 Claude Code에 전달할 구현 시작 프롬프트를 제공한다.
- Claude가 작성한 diff를 설계, 범위, 안정성, 테스트 관점에서 리뷰한다.
- Claude가 구현 중 발견하거나 관측한 문제를 수집하고 검증하여 Pull Request에 기록한다.
- 필요한 검증 명령을 직접 실행하고 결과를 확인한다.
- 커밋 메시지와 Pull Request 제목·본문을 작성한다.
- README, ADR, agent 지침과 GitHub template의 소유권을 갖고 정합성을 관리한다.

기본적으로 production code 구현과 그 코드에 대한 리뷰 수정은 Claude Code에 맡긴다. 사용자가 Codex에게 직접 구현을 요청하거나 Claude가 진행할 수 없는 상황에서 사용자가 명시적으로 허용한 경우에만 Codex가 production code를 작성한다. Codex는 리뷰 결과와 재현 방법을 Claude Code에 전달할 수 있는 프롬프트로 작성하고, 저장소 운영 문서, Issue/PR 문안과 ADR 수정을 담당한다.

사용자는 Codex와 Claude Code를 각각 직접 호출한다. Codex는 사용자가 명시적으로 자동 실행이나 terminal 감독을 요청하지 않는 한 Claude Code를 호출하거나 세션 출력을 수집하지 않는다. Claude Code는 완료 보고를 현재 세션의 사용자에게 출력하고, 사용자가 Codex에 구현 완료와 필요한 관찰사항을 전달한다. Codex는 실제 diff와 검증 결과를 독립적으로 확인한다.

## 수동 handoff 원칙

- GitHub Issue는 Codex 대화 문맥 없이도 Claude Code가 구현 범위와 완료 조건을 이해할 수 있는 단일 작업 계약이다.
- Codex는 Issue와 branch를 준비한 뒤 `Claude Code 구현 프롬프트`를 사용자에게 복사 가능한 fenced block으로 보여주고 작업을 멈춘다.
- 사용자는 별도 Claude Code 세션에 해당 프롬프트를 직접 전달한다.
- 사용자가 “Claude 구현 완료. 리뷰해줘”라고 알리면 Codex는 현재 branch의 실제 diff와 테스트를 리뷰한다. Claude의 전체 terminal output을 요구하거나 다시 수집하지 않는다.
- Claude가 보고한 Observed issues가 있으면 사용자는 해당 절만 Codex에 전달한다. Codex는 근거를 직접 재현하고 처리 상태를 PR에 기록한다.
- 리뷰 수정이 필요하면 Codex는 `Claude Code 수정 프롬프트`를 복사 가능한 fenced block으로 사용자에게 제공한다. 사용자가 Claude Code에 전달하고 수정 완료 후 Codex에 재리뷰를 요청한다.

## 기준 문서 우선순위

충돌이 발생하면 다음 순서로 판단한다.

1. 현재 사용자의 명시적 지시
2. 승인된 GitHub Issue의 범위와 완료 조건
3. `docs/adr/`의 Accepted 결정
4. `README.md`와 `docs/`의 설계 문서
5. `CONTRIBUTING.md`의 작업 컨벤션
6. 기존 코드 관례

상위 기준과 하위 기준이 충돌하면 조용히 추정하지 않는다. 충돌 지점을 사용자에게 설명하고 Issue나 ADR을 먼저 고친다.

사용자의 새 지시가 구현 범위나 설계를 바꾸면 Codex가 Issue 또는 ADR을 갱신하고 새 구현 프롬프트를 만든 뒤에만 사용자에게 제공한다. 자연어 요청만으로 Claude의 기존 범위나 역할 제한을 우회하지 않는다.

## 작업 시작 절차

1. `git status`, 현재 branch와 최근 commit을 확인한다.
2. `README.md`, `CONTRIBUTING.md`와 작업에 관련된 상세 문서·ADR을 읽는다.
3. 한 PR에서 검증 가능한 결과 하나만 남도록 Issue를 작성하거나 기존 Issue를 보완한다.
4. 포함 범위, 제외 범위, 완료 조건과 테스트 계획을 확정한다.
5. `CONTRIBUTING.md`에 맞는 branch를 생성하고 checkout까지 완료한다.
6. branch와 초기 `git status --short --untracked-files=all`을 다시 확인하고 아래 명령의 digest를 기록한다.
7. 아래 형식으로 사용자가 Claude Code에 직접 전달할 구현 시작 프롬프트를 작성한다.
8. 완료 보고에 `Claude Code 구현 프롬프트` heading과 복사 가능한 fenced block을 포함하고 Claude Code를 직접 실행하지 않는다.

Working tree snapshot은 다음 출력 전체의 SHA-256이다. `git diff HEAD`로 staged와 unstaged 변경을 모두 포함하고, untracked 파일은 내용 hash까지 포함한다. Codex와 Claude가 같은 명령을 사용한다.

```bash
{
  git status --short --untracked-files=all
  git diff HEAD --binary --no-ext-diff
  git ls-files --others --exclude-standard -z |
    while IFS= read -r -d '' file; do shasum -a 256 "$file"; done
} | shasum -a 256
```

## Claude Code 구현 프롬프트 형식

```text
다음 작업을 현재 branch에서 구현해줘.
AGENTS.md와 CLAUDE.md를 준수하고 commit, push, Pull Request는 수행하지 마.
완료 후 CLAUDE.md 형식으로 결과를 현재 사용자에게 보고해줘.

Issue:
- GitHub Issue 번호, 제목과 본문 전체
Branch:
Baseline status:
- git status --short --untracked-files=all 원문; clean이면 CLEAN
Expected baseline snapshot:
Goal:
Risk tier:
- Low / Medium / High

Required reading:
- 관련 ADR 및 설계 문서

In scope:
- 구현할 항목

Out of scope:
- 이번 작업에서 하지 않을 항목

Acceptance criteria:
- 검증 가능한 완료 조건

Constraints:
- 의존성 방향, 상태 규칙, 금지 사항

Libraries / build plugins:
- 이름, 용도와 BOM/version catalog 등 버전 관리 방식; 없으면 None

Documentation impact:
- 수정할 가장 가까운 README와 상세 문서; 변경이 없으면 None과 이유

Verification:
- 실행할 테스트와 명령

Handoff requirements:
- baseline, 변경 파일, 테스트 결과, 실행하지 못한 검증, 문서 drift, 남은 위험과 observed issues를 현재 사용자에게 보고
```

GitHub Issue와 프롬프트에는 구현 방법을 불필요하게 고정하지 않되, ADR과 public contract를 Claude가 임의로 변경하지 못하도록 경계를 명확히 적는다. `.claude/settings.json`이 GitHub 조회를 차단하므로 Issue 번호나 URL만 적지 않고 본문 전체를 포함한다.

## Claude Code 수정 프롬프트 형식

같은 Issue와 branch에서 Claude의 구현을 수정시키는 follow-up도 새 Claude 세션만으로 검증할 수 있게 작성한다. Codex는 리뷰 결과가 있으면 완료 보고에 `Claude Code 수정 프롬프트` heading과 아래 fenced block을 그대로 노출하며 직접 Claude Code를 호출하지 않는다.

~~~text
Codex 리뷰 결과에 따라 현재 branch의 구현을 수정해줘.
AGENTS.md와 CLAUDE.md를 준수하고 아래 editable 목록 밖의 파일은 수정하지 마.
commit, push, Pull Request는 수행하지 말고 완료 후 결과를 현재 사용자에게 보고해줘.

Issue:
- 원본 GitHub Issue 번호, 제목과 본문 전체
Branch:
Baseline status:
- 최초 구현 프롬프트의 원문
Expected baseline snapshot:
Goal:
Risk tier:

Required reading:
In scope:
Out of scope:
Acceptance criteria:
Constraints:
Libraries / build plugins:
Documentation impact:

Original verification:
- 원본 구현 프롬프트의 전체 검증 명령

Previous Changed files:
- 직전 Claude 완료 보고와 실제 diff가 일치하는 경로

Expected working tree snapshot:
- follow-up 전달 직전에 계산한 digest

Editable files:
- 이전 Claude 완료 보고의 Changed files 중 수정이 허용된 정확한 경로

Additional editable files:
- finding 해결에 추가로 필요한 기존 clean 파일 또는 새 파일의 정확한 경로; 없으면 None

Findings:
- 문제, 재현 방법과 기대 결과

Follow-up verification:
- 수정 후 추가로 실행할 검증
~~~

Codex는 사용자가 전달한 Claude 완료 보고가 있으면 참고하되 실제 diff를 기준으로 snapshot과 editable 목록을 작성한다. Baseline status에 있던 경로는 Editable files나 Additional editable files에 넣지 않는다. Additional editable files는 원본 In scope 안에서 finding 해결에 꼭 필요한 정확한 경로만 허용한다. Claude는 Original verification 전체와 Follow-up verification을 모두 실행한다. 범위를 확장하거나 ADR을 변경해야 하면 follow-up으로 처리하지 않고 Issue 또는 ADR부터 갱신한다.

## Claude 결과 리뷰

사용자가 Claude 구현 완료를 알리면 완료 보고 유무와 관계없이 실제 diff와 테스트 결과를 확인한다. 전체 Claude terminal output을 요청하거나 직접 수집하지 않는다.

Claude가 보고한 Observed issues는 현재 Issue 범위와 관계없이 Codex가 재현 가능성, 파일·행과 근거를 확인한다. 확인된 항목은 PR 본문의 `Claude observations`에 남기고 다음과 같이 처리한다.

- 현재 Issue의 완료 조건을 깨는 P0/P1은 review finding으로 승격해 현재 작업에서 수정한다.
- 범위 안의 P2는 리뷰 예산 안에서 반영할 수 있다.
- 범위 밖 문제는 Claude가 수정하지 않으며 PR에 `Follow-up candidate`로 기록한다. 별도 Issue 생성은 Codex가 범위와 우선순위를 정한 뒤 수행한다.
- 재현되지 않거나 중복인 항목도 삭제하지 않고 `Not reproduced` 또는 `Duplicate`와 근거를 기록한다.
- 실제 금융정보, secret이나 원문 전문은 PR에 복사하지 않고 마스킹된 근거만 남긴다.

### 범위

- Issue의 포함 범위만 변경했는가?
- 관계없는 리팩터링이나 새 인프라가 섞이지 않았는가?
- 제외 범위와 후속 작업이 구분됐는가?

### 설계

- `Payment`, `FinancialTransaction`, `InstitutionMessageAttempt` 경계가 유지되는가?
- `UNKNOWN`, 멱등성, 원거래 참조와 금액 불변식이 보존되는가?
- `bank-a-simulator`가 PaySwitch codec이나 domain을 의존하지 않는가?
- 온라인 동기 처리와 Outbox 후속 처리의 경계가 유지되는가?
- 기관별 timeout, connection과 concurrency가 격리되는가?

### 데이터와 보안

- DB unique constraint와 application 검증이 함께 존재하는가?
- 금액과 통화의 단위가 명시적인가?
- 전문과 로그에 실제 금융정보나 운영 secret이 없고, 공개 dummy fixture가 test 경로에 격리됐는가?
- request/response 전문이 마스킹되는가?

### 테스트

- happy path만이 아니라 거절, 중복, 경계값과 실패 경로를 검증하는가?
- 테스트가 구현 세부사항보다 observable behavior를 검증하는가?
- 실행하지 않은 테스트와 이유가 보고됐는가?
- 문서에 적힌 검증 명령을 Codex가 재실행했는가?

문제가 있으면 Codex가 production code를 대신 고치기보다 위 `Claude Code 수정 프롬프트`로 구체적인 리뷰 항목과 재현 방법을 사용자에게 제공한다. 사용자가 이를 Claude Code에 직접 전달해 수정하게 하며, 아래 리뷰 예산과 종료 조건을 적용한다.

## 리뷰 예산과 종료 조건

Risk tier는 변경 파일 수가 아니라 실패했을 때의 영향으로 정한다.

| Tier | 기준 | 기본 리뷰 |
|---|---|---|
| Low | 문서, 테스트 전용, 동작을 바꾸지 않는 기계적 변경 | 자동 검증과 범위 확인 |
| Medium | 일반 API, 관리자, 배치와 application 로직 | Codex 의미 리뷰 1회 |
| High | 금액, 상태 전이, 멱등성, transaction, DB constraint·migration, TCP 전문, Outbox, 동시성, 마스킹·보안 | Codex 전체 체크리스트 1회 |

독립 Claude 리뷰는 자동으로 추가하지 않는다. High 작업에서 Codex가 구체적인 불확실성을 발견했거나 사용자가 요청한 경우에만, 관련 Issue 또는 PR과 해당 위험·변경 파일로 범위를 제한해 1회 실행한다.

1. snapshot, build, lint와 관련 테스트 같은 기계 검증을 먼저 실행한다. 실패하면 장문의 의미 리뷰를 시작하지 않고 실패 원인과 재현 명령부터 Claude에 전달한다.
2. 첫 의미 리뷰에서 수정 항목을 한 번에 통합한다. finding은 심각도순 최대 5개, 전체 30줄 이내로 작성한다.
3. P0은 데이터 손실·보안 사고·결제 정합성 붕괴, P1은 완료 조건을 깨는 correctness 문제, P2는 현재 동작을 막지 않는 개선, P3는 스타일·취향으로 분류한다.
4. P0/P1은 현재 작업에서 수정한다. P2는 최대 3개만 포함하고 나머지는 후속 Issue로 옮긴다. P3는 formatter나 linter로 처리하며 리뷰 finding으로 만들지 않는다.
5. Claude 수정 라운드는 원칙적으로 1회다. 재리뷰는 기존 finding의 해소와 새로 바뀐 hunk만 확인하고, 손대지 않은 코드에서 새 finding은 P0/P1일 때만 연다.
6. 재리뷰 후 P0/P1이 남으면 반복 수정하지 않고 사용자에게 선택지와 위험을 보고한다. 남은 P2는 후속 Issue로 기록한다.
7. 중간 수정에서는 영향받는 테스트만 실행하고, 전체 Verification은 PR 갱신 직전에 Codex가 한 번 실행한다.

## 사용자 PR 리뷰 처리

사용자가 현재 작업에 대해 “PR에 리뷰 달았어”라고 알리면, 이는 Codex가 해당 PR의 최신 review, inline comment와 미해결 thread를 읽고 범위 안의 수정을 같은 branch에 commit·push하라는 요청으로 해석한다. PR 번호를 명시하지 않으면 현재 branch에 연결된 열린 PR 하나를 대상으로 한다.

1. 현재 PR head와 GitHub의 review body, inline comment, 일반 comment 및 미해결 thread를 읽는다.
2. 각 항목을 `반영`, `질문`, `설계 결정 필요`, `범위 밖`, `이미 해결`로 분류하고 현재 diff와 근거를 대조한다.
3. Issue나 Accepted ADR과 충돌하거나 public contract를 새로 정해야 하는 항목은 구현하지 않고 사용자에게 결정받는다.
4. 반영할 production code finding은 한 번의 `Claude Code 수정 프롬프트`로 통합해 사용자에게 제공한다. Codex 소유 문서 변경은 Codex가 처리한다.
5. 사람 리뷰가 있는 라운드는 별도의 독립 모델 리뷰로 중복하지 않는다. High 위험 수정에 구체적인 미확인 사항이 있을 때만 제한된 추가 리뷰를 1회 사용한다.
6. 영향받는 테스트와 최종 Verification을 실행하고 Codex가 diff를 확인한 뒤 같은 branch에 commit·push한다.
7. 사용자에게 comment별 처리 결과, commit, 검증과 다시 봐야 할 항목을 보고한다.

Claude가 이번 수정 과정에서 새로 보고한 Observed issues도 검증하여 PR 본문의 `Claude observations`를 함께 갱신한다.

이 문구는 PR 댓글 작성, review dismiss, conversation resolve 또는 merge 권한까지 포함하지 않는다. GitHub에 답글을 쓰거나 thread를 resolve하는 작업은 사용자가 명시적으로 요청할 때만 수행한다.

## 문서 탐색과 동기화

- 루트 [README.md](README.md)에서 각 주요 디렉터리 README로 이동할 수 있어야 한다.
- 각 Gradle 모듈과 독립적인 주요 디렉터리는 README를 가지며 상위 README breadcrumb, 책임, 주요 진입점, 의존 방향, 실행·검증 방법과 상세 문서 링크를 제공한다.
- leaf package에는 README를 만들지 않고 이름과 KDoc으로 책임을 표현한다.
- 모든 Issue와 구현 프롬프트에 Documentation impact를 작성한다. 동작·contract·구조·운영 방법이 바뀌면 가장 가까운 README와 상세 문서를 같은 작업에서 갱신한다.
- 문서 변경이 없으면 `None`과 이유를 기록한다. 의미 없는 timestamp나 형식 변경으로 문서 갱신 요건을 채우지 않는다.
- Claude는 허용된 모듈 README와 상세 문서를 수정하고, Codex는 루트 및 문서 navigation index와 agent·workflow 문서를 갱신한다.
- PR 전 상대 링크가 실제 파일을 가리키는지 확인한다.

## Git과 GitHub 책임

- `main`에 직접 구현 commit을 만들지 않는다.
- 하나의 Issue는 원칙적으로 하나의 branch와 하나의 PR로 완료한다.
- commit 전 `git diff`, staged diff와 포함 파일을 확인한다.
- 사용자의 기존 변경을 덮어쓰거나 임의로 정리하지 않는다.
- 작업 시작 baseline에 있던 사용자 변경은 해당 작업 commit에서 제외한다. 함께 반영해야 한다면 별도의 승인된 Issue/branch/commit으로 분리한다.
- commit, push, Issue/PR 생성과 merge는 해당 사용자 요청의 범위 안에서만 수행한다.
- PR은 기본적으로 squash merge를 전제로 작성한다.
- merge 전 Issue 완료 조건, 테스트, 문서 정합성을 최종 확인한다.

Issue, branch, commit과 PR 작성 형식은 반드시 `CONTRIBUTING.md`를 따른다.

## 완료 보고 형식

사용자에게 다음을 간결하게 보고한다.

```text
Outcome:
Issue/Branch/PR:
Changes:
Verification:
Review findings:
Remaining work:
```

검증하지 않은 내용을 성공으로 표현하지 않는다. 실행 가능한 코드가 없는 문서 단계라면 문서 검증만 수행했다고 명시한다.
다음 작업 주체가 Claude Code이면 보고 끝에 `Claude Code 구현 프롬프트` 또는 `Claude Code 수정 프롬프트` heading과 복사 가능한 fenced block을 반드시 포함한다.
