-- account 모듈 분리 — 옮긴 반복 작업의 남은 작업(ADR-0113 · ADR-0308).
--
-- 만료 세션 정리 · 실패 기록 정리 · 끝난 요청 제한 창 정리의 반복 작업 클래스가 user 에서 account 로 옮겨 갔다. 옛 클래스 이름으로
-- 직렬화된 예약 · 대기 작업을 지운다 — 반복 작업 ID 는 그대로이고, 기동 때 새 정의가 같은 ID 로 등록되어 다음 회차를 만든다. 옮긴
-- 리스너 · 이벤트 클래스는 없어 이벤트 발행 레지스트리는 고치지 않는다.

DELETE FROM jobrunr_jobs
 WHERE recurringJobId IN ('expire-sessions', 'forget-login-failures', 'forget-request-counts')
   AND state IN ('SCHEDULED', 'ENQUEUED', 'AWAITING');
