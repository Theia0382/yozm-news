-- source 모듈 — 소스 · 피드의 숨김. 운영자가 숨긴 소스 · 피드는 행 · 원문 · 받은 기사가 남고, 운영 목록에서 빠진다.
--
-- 비어 있으면(NULL) 숨기지 않았다. 숨김은 끄기를 포함하므로 수집 · 피드 건강 검사는 켜짐 조건으로 빠진다.
ALTER TABLE sources ADD COLUMN hidden_at TIMESTAMPTZ;
ALTER TABLE feeds ADD COLUMN hidden_at TIMESTAMPTZ;
