-- llm 공급자에 openrouter 를 더한다 — credential · user · digest 의 공급자 칸(docs/specs/llm.md llm-R1).
-- 제약을 넓히기만 하고 기존 행은 바꾸지 않는다. user_settings 의 이름 없는 표 제약(공급자 · 모델이 함께 있거나 함께 없다)은 그대로 둔다.
ALTER TABLE llm_credentials DROP CONSTRAINT llm_credentials_provider_check;
ALTER TABLE llm_credentials ADD CONSTRAINT llm_credentials_provider_check
  CHECK (provider IN ('openai', 'anthropic', 'openrouter'));

ALTER TABLE user_settings DROP CONSTRAINT user_settings_llm_provider_check;
ALTER TABLE user_settings ADD CONSTRAINT user_settings_llm_provider_check
  CHECK (llm_provider IN ('openai', 'anthropic', 'openrouter'));

ALTER TABLE digests DROP CONSTRAINT digests_provider_check;
ALTER TABLE digests ADD CONSTRAINT digests_provider_check
  CHECK (provider IN ('openai', 'anthropic', 'openrouter'));
