-- event 모듈 — 합쳐지지 않은 사건(열림 · 닫힘)의 모델 버전 · 마지막 기사 시각 인덱스(docs/specs/event.md §6 · event-R24).
--
-- 이웃 사건 · 스토리라인 채우기 대상 조회의 시각 조건을 거른다. 열린 사건만 덮는 events_open_idx 와 따로 둔다.
CREATE INDEX events_unmerged_idx ON events (model_version, last_article_at) WHERE status <> 'MERGED';
