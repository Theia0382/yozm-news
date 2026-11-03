-- evaluation 모듈 — 기간 집계의 평가 행 찾기(docs/specs/evaluation.md §6).
--
-- 보도량은 기간의 평가 행을 평가 시각으로, 평가 현황은 버전 · 평가 시각으로 찾는다. 기사별 더 이른 평가 · 최신 평가는 고유 제약
-- (article_id, tag_set_id) 의 앞 열로 찾는다.
CREATE INDEX article_evaluations_evaluated_idx ON article_evaluations (evaluated_at);
CREATE INDEX article_evaluations_version_evaluated_idx ON article_evaluations (tag_set_id, evaluated_at);
