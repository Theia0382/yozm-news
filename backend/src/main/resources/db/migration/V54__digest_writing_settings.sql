-- user 모듈 — 다이제스트 요약 길이와 생성의 추론 강도(docs/specs/user.md §6).
-- summary_length 는 요약 길이의 단계(뜻은 다이제스트의 지시가 정한다), reasoning_effort 는 llm 의 추론 강도 값이다.
-- 칸을 더하기만 한다 — 기존 행은 기본값(보통 · 보통)으로 채우고 기본값을 지운다. 새 행의 기본값은 코드(InterestDefaults)가 넣는다.
ALTER TABLE user_settings
  ADD COLUMN summary_length    TEXT  NOT NULL DEFAULT 'NORMAL'
    CHECK (summary_length IN ('SHORT', 'NORMAL', 'DETAILED')),
  ADD COLUMN reasoning_effort  TEXT  NOT NULL DEFAULT 'MEDIUM'
    CHECK (reasoning_effort IN ('LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE user_settings
  ALTER COLUMN summary_length DROP DEFAULT,
  ALTER COLUMN reasoning_effort DROP DEFAULT;
