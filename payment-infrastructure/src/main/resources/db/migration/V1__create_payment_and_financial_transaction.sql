-- 결제 요약과 개별 금융거래 기준 스키마.
-- 현재 domain은 KRW 승인만 지원하므로 통화 CHECK는 KRW만 허용한다. 외화 지원 시 새 migration으로 확장한다.
-- 식별자와 통화·enum 문자열은 domain String 비교와 같도록 대소문자와 trailing space를 구분하는 utf8mb4_0900_bin을 사용한다.

CREATE TABLE payment
(
    payment_id                   VARCHAR(64)  NOT NULL,
    merchant_id                  VARCHAR(64)  NOT NULL,
    client_reference             VARCHAR(128) NOT NULL,
    approved_amount_minor        BIGINT       NOT NULL,
    cancelled_amount_minor       BIGINT       NOT NULL,
    reserved_cancel_amount_minor BIGINT       NOT NULL,
    transaction_currency         CHAR(3)      NOT NULL,
    status                       VARCHAR(32)  NOT NULL,
    version                      BIGINT       NOT NULL,
    CONSTRAINT pk_payment PRIMARY KEY (payment_id),
    CONSTRAINT uk_payment_merchant_client_reference UNIQUE (merchant_id, client_reference),
    CONSTRAINT ck_payment_approved_amount_non_negative CHECK (approved_amount_minor >= 0),
    CONSTRAINT ck_payment_cancelled_amount_non_negative CHECK (cancelled_amount_minor >= 0),
    CONSTRAINT ck_payment_reserved_cancel_amount_non_negative CHECK (reserved_cancel_amount_minor >= 0),
    CONSTRAINT ck_payment_cancel_within_approved
        CHECK (cancelled_amount_minor + reserved_cancel_amount_minor <= approved_amount_minor),
    CONSTRAINT ck_payment_transaction_currency CHECK (transaction_currency = 'KRW'),
    CONSTRAINT ck_payment_status CHECK (status IN
                                        ('CREATED', 'APPROVED', 'DECLINED', 'FAILED', 'RESOLUTION_REQUIRED',
                                         'MANUAL_REVIEW_REQUIRED')),
    CONSTRAINT ck_payment_version_non_negative CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_bin;

CREATE TABLE financial_transaction
(
    transaction_id          VARCHAR(64) NOT NULL,
    payment_id              VARCHAR(64) NOT NULL,
    transaction_type        VARCHAR(16) NOT NULL,
    original_transaction_id VARCHAR(64) NULL,
    amount_minor            BIGINT      NOT NULL,
    currency                CHAR(3)     NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    version                 BIGINT      NOT NULL,
    CONSTRAINT pk_financial_transaction PRIMARY KEY (transaction_id),
    CONSTRAINT fk_financial_transaction_payment
        FOREIGN KEY (payment_id) REFERENCES payment (payment_id),
    CONSTRAINT fk_financial_transaction_original
        FOREIGN KEY (original_transaction_id) REFERENCES financial_transaction (transaction_id),
    CONSTRAINT ck_financial_transaction_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_financial_transaction_currency CHECK (currency = 'KRW'),
    CONSTRAINT ck_financial_transaction_type CHECK (transaction_type IN ('AUTHORIZE')),
    CONSTRAINT ck_financial_transaction_authorize_without_original
        CHECK (transaction_type <> 'AUTHORIZE' OR original_transaction_id IS NULL),
    CONSTRAINT ck_financial_transaction_status CHECK (status IN
                                                      ('RECEIVED', 'PROCESSING', 'SUCCEEDED', 'DECLINED', 'FAILED',
                                                       'UNKNOWN', 'MANUAL_REVIEW_REQUIRED')),
    CONSTRAINT ck_financial_transaction_version_non_negative CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_bin;
