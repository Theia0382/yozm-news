-- user 모듈 — 다이제스트 분량 설정(docs/specs/user.md §6).
-- candidate_volume 은 최대 후보 수의 단계, candidate_count 는 직접 입력(CUSTOM)일 때만 있는 수, big_slots 는 큰 슬롯 수(0 이면 끔).
-- 단계의 값과 범위는 코드(DigestVolume · CandidateVolume)에만 둔다 — 측정 전에 바꿀 값이라 칸에 사본을 두지 않는다.
-- 기존 행은 기본값(보통 · 큰 슬롯 3)으로 채우고 기본값을 지운다 — 새 행의 기본값은 코드(InterestDefaults)가 넣는다.
ALTER TABLE user_settings
  ADD COLUMN candidate_volume  TEXT      NOT NULL DEFAULT 'NORMAL'
    CHECK (candidate_volume IN ('FEW', 'NORMAL', 'MANY', 'CUSTOM')),
  ADD COLUMN candidate_count   SMALLINT  CHECK (candidate_count >= 1),
  ADD COLUMN big_slots         SMALLINT  NOT NULL DEFAULT 3 CHECK (big_slots >= 0),
  ADD CONSTRAINT user_settings_candidate_count_custom
    CHECK ((candidate_volume = 'CUSTOM') = (candidate_count IS NOT NULL));

ALTER TABLE user_settings
  ALTER COLUMN candidate_volume DROP DEFAULT,
  ALTER COLUMN big_slots DROP DEFAULT;
