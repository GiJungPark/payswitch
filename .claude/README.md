[PaySwitch 홈](../README.md) · [Claude 구현 지침](../CLAUDE.md) · [Codex 운영 지침](../AGENTS.md)

# Claude Code 설정

이 디렉터리는 Claude Code의 저장소 단위 실행 제한을 관리한다.

| 경로 | 용도 |
|---|---|
| [settings.json](settings.json) | Git·GitHub·정책 문서 쓰기와 외부 도구 사용 제한 |

Claude Code는 이 디렉터리를 직접 수정하지 않는다. 정책 변경은 Claude 세션 밖에서 사용자가 승인하고 Codex가 적용한다.

사용자는 Codex와 Claude Code를 각각 직접 호출한다. Codex가 제공한 `Claude Code 구현 프롬프트` 또는 `Claude Code 수정 프롬프트`를 Claude 세션에 붙여 넣어 작업을 시작하고, 완료 후 Codex에 리뷰를 별도로 요청한다. GitHub 조회와 Git 쓰기 제한은 이 수동 handoff에서도 유지되므로 prompt에는 Issue 본문과 branch·baseline 정보가 모두 포함되어야 한다.
