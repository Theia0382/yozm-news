-- credential 모듈 — 사용자 LLM 키(docs/specs/credential.md §6).

-- 사용자 LLM 키 — 주인(사용자 · 공급자)마다 하나. 키는 키마다의 DEK 로, DEK 는 KEK 로 암호화해 둔다. 평문은 저장하지 않는다.
-- user_id 는 user 모듈의 사용자 ID 다 — 모듈 사이라 FK 를 두지 않는다
CREATE TABLE llm_credentials (
  id             BIGSERIAL    PRIMARY KEY,
  user_id        BIGINT       NOT NULL,
  provider       TEXT         NOT NULL CHECK (provider IN ('openai', 'anthropic')),
  ciphertext     BYTEA        NOT NULL,
  encrypted_dek  BYTEA        NOT NULL,
  kek_version    INTEGER      NOT NULL CHECK (kek_version >= 1),
  key_hint       TEXT         NOT NULL,
  status         TEXT         NOT NULL CHECK (status IN ('VALID', 'INVALID')),
  validated_at   TIMESTAMPTZ  NOT NULL,
  created_at     TIMESTAMPTZ  NOT NULL,
  CONSTRAINT llm_credentials_owner_key UNIQUE (user_id, provider)
);
-- 재암호화와 기동 검사가 KEK 버전으로 찾는다
CREATE INDEX llm_credentials_kek_version_idx ON llm_credentials (kek_version);
