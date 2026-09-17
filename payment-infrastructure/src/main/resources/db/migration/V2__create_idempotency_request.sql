-- 승인 요청 멱등키와 request hash 기록.
-- merchant_id·operation_type·idempotency_key 조합의 PK가 동시 중복 요청의 최종 경쟁 판정이다.
-- request_hash는 canonical representation SHA-256의 소문자 16진수 64자다.
-- 같은 client reference의 같은 요청에 사용된 여러 멱등키가 하나의 금융거래를 참조할 수 있으므로 transaction_id는 유일하지 않다.
-- 만료 시각과 정리 정책은 TTL 운영 Issue에서 추가한다.

CREATE TABLE idempotency_request
(
    merchant_id     VARCHAR(64)  NOT NULL,
    operation_type  VARCHAR(16)  NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    transaction_id  VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_idempotency_request PRIMARY KEY (merchant_id, operation_type, idempotency_key),
    CONSTRAINT fk_idempotency_request_transaction
        FOREIGN KEY (transaction_id) REFERENCES financial_transaction (transaction_id),
    CONSTRAINT ck_idempotency_request_operation_type CHECK (operation_type IN ('AUTHORIZE')),
    CONSTRAINT ck_idempotency_request_request_hash CHECK (REGEXP_LIKE(request_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_bin;
