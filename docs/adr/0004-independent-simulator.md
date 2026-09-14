# ADR 0004: 기관 simulator 독립 구현

[ADR 목록](README.md) · [문서 홈](../README.md) · [프로젝트 홈](../../README.md)

- 상태: Accepted
- 날짜: 2026-09-13

## Context

PaySwitch와 기관 simulator가 동일한 codec implementation을 사용하면 encoding, padding 또는 필드 위치 버그가 양쪽에서 같은 방식으로 발생하여 contract test가 잘못 통과할 수 있다.

## Decision

- `payswitch-app`과 `bank-a-simulator`를 별도 Gradle 모듈과 프로세스로 구성한다.
- simulator는 PaySwitch의 protocol, domain 또는 connector module을 의존하지 않는다.
- 양쪽은 `docs/protocol-bank-a.md`와 수작업 검증 golden byte fixture만 계약으로 공유한다.
- 각 codec은 독립적으로 구현하고 상대편과 실제 socket을 통해 contract test를 수행한다.
- simulator가 timeout, 응답 유실, 지연, 중복, 순서 변경, 잘못된 MAC과 connection 종료를 주입할 수 있게 한다.

## Consequences

- codec 코드가 중복되지만 테스트의 독립성이 높아진다.
- 전문 변경 시 두 구현을 각각 수정해야 한다.
- 명세와 fixture의 version 관리가 중요해진다.
- 단위 테스트뿐 아니라 실제 TCP contract test가 필요하다.
