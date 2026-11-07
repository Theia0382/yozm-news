-- event 모듈 — 배정 · 병합 판정 기록이 반영한 사건 소속 판정 원본(evaluation)을 가리킨다.
--
-- 판정 원본은 evaluation 모듈의 article_event_judgements 를 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다.
-- 원본을 가리키는 기록이 없으면 그 판정은 반영되지 않았다(쓰기를 되돌렸거나, 쌍의 기록이 이미 있었거나, 기록이 함께 지워졌거나, 이
-- 마이그레이션 전의 원본).

-- 배정을 정한 판정 원본 — 이 배정을 쓸 때 판정을 반영했으면. 재판정 · 병합으로 다시 적거나 옮겨도 그대로다
ALTER TABLE event_articles ADD COLUMN judgement_id BIGINT;
CREATE INDEX event_articles_judgement_idx ON event_articles (judgement_id) WHERE judgement_id IS NOT NULL;

-- 이 쌍의 확률을 낸 판정 원본 — 이 마이그레이션 전의 행은 null
ALTER TABLE event_merge_judgements ADD COLUMN judgement_id BIGINT;
CREATE INDEX event_merge_judgements_judgement_idx ON event_merge_judgements (judgement_id) WHERE judgement_id IS NOT NULL;
