-- evaluation 모듈 — 사건 소속 판정 원본의 기간 조회(운영자 화면).
--
-- 판정 시각 범위를 (판정 시각, ID) 내림차순으로 쪽을 나눠 읽는다 — 거꾸로 훑는다.
CREATE INDEX article_event_judgements_judged_idx ON article_event_judgements (judged_at, id);
