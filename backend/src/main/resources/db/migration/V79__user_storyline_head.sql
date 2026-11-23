-- 사용자 스토리라인의 헤드 — 그 개체의 추적 대상 가운데 뒤 흐름(이어짐)이 없는 꼭대기. 판정은 user 의 묶음 규칙(UserStorylineGroups)이 하고,
-- 저장하는 맞추기가 잇기 결과로 쓴다. 이 열이 생기기 전에 저장된 개체는 비어 있다(다음 저장하는 맞추기가 채운다)
ALTER TABLE user_storylines ADD COLUMN head BIGINT CHECK (head >= 1);
