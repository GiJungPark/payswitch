# Bank A TCP 전문 v1

[문서 홈](README.md) · [프로젝트 홈](../README.md)

## 목적과 범위

Bank A 전문은 TCP 금융기관 연동에서 framing, 고정 byte 필드, multiplexing, MAC 검증과 timeout 처리를 학습하기 위한 가상 규격이다. 실제 금융기관 또는 ISO 8583 규격을 복제하지 않는다.

## 전송 규칙

- 전송 단위는 `4-byte length header + 293-byte body`다.
- length header는 body의 byte 길이를 나타내는 4자리 ASCII 숫자 `0293`이다.
- body의 문자열 encoding은 `EUC-KR`이다.
- 모든 필드 길이는 문자 수가 아니라 encoding 이후 byte 수를 기준으로 한다.
- 숫자 필드는 왼쪽을 `0`으로 채운다.
- 문자 필드는 오른쪽을 ASCII space(`0x20`)로 채운다.
- 필드가 정의된 byte 길이를 넘으면 자르지 않고 encoding 오류로 거절한다.
- 금액은 소수점 기호 없이 통화의 최소 단위 정수로 전송한다.

금액 예시는 다음과 같다.

```text
KRW 10,000  -> 000000000000010000
USD 12.34   -> 000000000000001234
```

## 메시지 종류

| Message Type | 방향 | 용도 |
|---|---|---|
| `0100` | PaySwitch → Bank A | 승인 요청 |
| `0110` | Bank A → PaySwitch | 승인 응답 |
| `0200` | PaySwitch → Bank A | 취소 요청 |
| `0210` | Bank A → PaySwitch | 취소 응답 |
| `0300` | PaySwitch → Bank A | 거래 조회 요청 |
| `0310` | Bank A → PaySwitch | 거래 조회 응답 |
| `0400` | PaySwitch → Bank A | 망취소 요청 |
| `0410` | Bank A → PaySwitch | 망취소 응답 |
| `0800` | 양방향 | heartbeat 요청 |
| `0810` | 양방향 | heartbeat 응답 |

## Body layout

| 순서 | 필드 | Byte | 형식 | 설명 |
|---:|---|---:|---|---|
| 1 | protocolVersion | 2 | Numeric | 최초 버전은 `01` |
| 2 | messageType | 4 | Numeric | 요청·응답 종류 |
| 3 | institutionCode | 8 | AlphaNumeric | Bank A 기관 코드 |
| 4 | businessDate | 8 | Numeric | `yyyyMMdd` 형식의 기관 영업일 |
| 5 | traceNumber | 12 | Numeric | 영업일 내 기관 전문 추적번호 |
| 6 | transactionId | 26 | AlphaNumeric | PaySwitch 금융거래 ID |
| 7 | originalTransactionId | 26 | AlphaNumeric | 취소·망취소의 원거래 ID, 없으면 space |
| 8 | merchantId | 20 | AlphaNumeric | 가맹점 ID |
| 9 | merchantName | 40 | Text | EUC-KR byte 기준, 오른쪽 space padding |
| 10 | currency | 3 | Alpha | `KRW`, `USD` 등 거래 통화 |
| 11 | amount | 18 | Numeric | 통화 최소 단위 정수 |
| 12 | transmissionAt | 14 | Numeric | `yyyyMMddHHmmss` |
| 13 | responseCode | 4 | AlphaNumeric | 요청은 space, 응답은 결과 코드 |
| 14 | approvalNumber | 12 | AlphaNumeric | 승인 성공 응답에서 발급, 그 외 space |
| 15 | reserved | 32 | AlphaNumeric | 향후 확장용 space padding |
| 16 | mac | 64 | Hex | body 1~15 필드에 대한 HMAC-SHA-256 |
| | 합계 | **293** | | length header 제외 |

## MAC 정책

- MAC은 TCP checksum을 대체하는 전송 오류 검사가 아니라 애플리케이션 메시지의 무결성과 송신자 확인을 위한 값이다.
- 필드 1부터 15까지 padding과 EUC-KR encoding을 완료한 raw byte 배열을 입력으로 사용한다.
- 학습 환경에서는 HMAC-SHA-256을 사용한다.
- runtime용 MAC key는 source code나 전문에 포함하지 않고 실행 환경에서 주입하며, 값이 없으면 애플리케이션 시작을 실패시킨다.
- golden fixture는 재현 가능한 공개 dummy key를 사용한다. 이 키는 `spec/bank-a/v1/fixtures/` 아래에 테스트 전용임을 명시해 저장하고 production 설정에서 기본값으로 참조하지 않는다.
- MAC 불일치 응답은 업무 결과로 반영하지 않고 `MALFORMED_RESPONSE`로 기록한다.
- 실제 금융 보안키 관리와 암호 장비 연동은 프로젝트 범위에 포함하지 않는다.

## 응답 코드

| 코드 | 내부 결과 | 설명 |
|---|---|---|
| `0000` | `SUCCEEDED` | 정상 처리 |
| `1001` | `DECLINED` | 잔액 또는 한도 부족을 가정한 업무 거절 |
| `1002` | `DECLINED` | 원거래 상태상 취소 불가 |
| `2001` | `NOT_FOUND` | 조회 대상 거래 없음 |
| `3001` | `INVALID_MESSAGE` | 전문 길이 또는 필드 형식 오류 |
| `3002` | `INVALID_MAC` | MAC 검증 실패 |
| `9001` | `INSTITUTION_ERROR` | 기관 내부 오류 |

`NOT_FOUND`는 곧바로 원거래 거절을 의미하지 않는다. 거래가 아직 조회계에 반영되지 않았을 수 있으므로 기관별 resolution window 정책을 적용한다.

## 연결과 multiplexing

- PaySwitch는 Bank A와 long-lived TCP connection을 유지한다.
- 하나의 connection은 동시에 최대 32개의 in-flight 요청을 허용한다.
- 응답 순서는 요청 순서와 같다고 가정하지 않는다.
- 응답은 `(institutionCode, businessDate, traceNumber)`로 원요청과 연결한다.
- trace number는 기관·영업일 단위의 원자적 allocator로 생성한다.
- 이미 완료된 trace number 응답은 중복으로 기록하고 상태를 다시 변경하지 않는다.
- 매칭할 요청이 없거나 message type이 맞지 않는 응답은 격리하고 운영 지표를 증가시킨다.

초기 기본값은 다음과 같으며 기관 설정으로 변경할 수 있다.

| 설정 | 기본값 |
|---|---:|
| connect timeout | 3초 |
| response timeout | 5초 |
| heartbeat idle interval | 20초 |
| max in-flight per connection | 32 |
| reconnect backoff | 1초부터 최대 30초 |

기관마다 독립된 connection pool과 in-flight semaphore를 사용한다. Bank A의 응답 지연이 다른 기관 connector의 자원을 점유하지 않게 한다.

## 전송 결과 판정

- connection을 얻지 못했거나 write 전에 실패했음을 확정할 수 있으면 금융거래를 `FAILED`로 처리할 수 있다.
- write 성공 이후 응답 timeout 또는 connection 종료가 발생하면 기관 수신 가능성이 있으므로 `UNKNOWN`으로 처리한다.
- `UNKNOWN`인 승인·취소·망취소 전문 자체를 자동 재전송하지 않는다.
- 상태 해결은 `0300` 조회 전문과 기관별 망취소 정책으로 수행한다.

## Simulator 독립성

`bank-a-simulator`는 PaySwitch의 encoder, decoder 또는 domain module을 의존하지 않는다. 양쪽 구현은 이 문서와 수작업으로 검증한 golden byte fixture만 계약으로 사용한다.

검증 항목은 다음과 같다.

- 한글 가맹점명의 EUC-KR byte 길이
- 모든 숫자·문자 padding
- body와 length header 길이
- minor unit 금액 변환
- MAC 대상 byte 범위
- 서로 다른 순서의 응답 correlation
