-- tag 모듈 분리 — 옮긴 리스너의 미완료 발행과 옮긴 반복 작업의 남은 작업(ADR-0113).
--
-- 이벤트 발행 레지스트리는 발행을 리스너 ID(클래스 전체 이름 · 메서드 · 인자 타입)와 함께 저장하고, 다시 처리할 때 같은 ID 의
-- 리스너를 찾는다. evaluation 의 ArticleEventsListener 는 tag 로 옮겨 가고, 그 ArticlesRemoved 몫 가운데 판정 원본 지우기는
-- evaluation 의 JudgementRemovalListener 로 나뉜다 — 미완료 발행의 리스너 ID 를 새 리스너로 옮기고, 나뉜 몫은 새 리스너마다 한
-- 행이 되도록 복제한다. 완료된 발행은 지워지므로(완료 방식 delete) 미완료 조건은 방어다.
--
-- 평가 회차 · 빠진 태그 채움의 반복 작업 클래스도 tag 로 옮긴다. 옛 클래스 이름으로 직렬화된 예약 · 대기 작업을 지운다 — 반복 작업
-- ID 는 그대로이고, 기동 때 새 정의가 같은 ID 로 등록되어 다음 회차를 만든다.

INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date, status,
                               completion_attempts, last_resubmission_date)
SELECT gen_random_uuid(),
       'net.yozmnews.evaluation.application.JudgementRemovalListener.on(net.yozmnews.article.ArticlesRemoved)',
       event_type, serialized_event, publication_date, NULL, status, completion_attempts, last_resubmission_date
  FROM event_publication
 WHERE listener_id = 'net.yozmnews.evaluation.application.ArticleEventsListener.on(net.yozmnews.article.ArticlesRemoved)'
   AND completion_date IS NULL;

UPDATE event_publication
   SET listener_id = 'net.yozmnews.tag.application.ArticleEventsListener.on(net.yozmnews.article.ArticlesRemoved)'
 WHERE listener_id = 'net.yozmnews.evaluation.application.ArticleEventsListener.on(net.yozmnews.article.ArticlesRemoved)'
   AND completion_date IS NULL;

UPDATE event_publication
   SET listener_id = 'net.yozmnews.tag.application.ArticleEventsListener.on(net.yozmnews.article.ArticleStored)'
 WHERE listener_id = 'net.yozmnews.evaluation.application.ArticleEventsListener.on(net.yozmnews.article.ArticleStored)'
   AND completion_date IS NULL;

DELETE FROM jobrunr_jobs
 WHERE recurringJobId IN ('evaluate-pending-articles', 'backfill-missing-tags')
   AND state IN ('SCHEDULED', 'ENQUEUED', 'AWAITING');
