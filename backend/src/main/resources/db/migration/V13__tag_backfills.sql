-- evaluation 모듈 — 빠진 태그 채움(docs/specs/evaluation.md §6).
--
-- 운영자 요청 하나가 tag_backfills 한 행이고, 요청 때 모든 버전의 채울 저장 기준을 tag_backfill_versions 에 넣는다. 끝난 채움은
-- 저장 바닥 계산의 입력이라 지우지 않는다.

-- 빠진 태그 채움 — 요청 하나가 한 행. 끝나지 않은 행은 하나뿐이다
CREATE TABLE tag_backfills (
  id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  days              INTEGER      NOT NULL CHECK (days >= 1),
  -- 덮는 평가 시각 [covered_from, covered_to) — covered_to 는 요청 시각이다
  covered_from      TIMESTAMPTZ  NOT NULL,
  covered_to        TIMESTAMPTZ  NOT NULL,
  -- 이 시각부터 시작할 수 있다
  eligible_at       TIMESTAMPTZ  NOT NULL,
  -- 처리한 마지막 평가 ID
  cursor_id         BIGINT       NOT NULL DEFAULT 0,
  lease_token       UUID,
  lease_expires_at  TIMESTAMPTZ,
  started_at        TIMESTAMPTZ,
  finished_at       TIMESTAMPTZ,
  CHECK (covered_from < covered_to),
  CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL)),
  CHECK (finished_at IS NULL OR lease_token IS NULL)
);
CREATE UNIQUE INDEX tag_backfills_one_open ON tag_backfills ((true)) WHERE finished_at IS NULL;

-- 채움의 버전별 기록 — 채울 저장 기준과 버전별 결과
CREATE TABLE tag_backfill_versions (
  backfill_id             BIGINT        NOT NULL REFERENCES tag_backfills (id),
  tag_set_id              BIGINT        NOT NULL REFERENCES tag_sets (id),
  topic_store_threshold   NUMERIC(4,3)  NOT NULL CHECK (topic_store_threshold BETWEEN 0 AND 1),
  region_store_threshold  NUMERIC(4,3)  NOT NULL CHECK (region_store_threshold BETWEEN 0 AND 1),
  -- 요청 때 센 대상 평가 수
  evaluations             BIGINT        NOT NULL CHECK (evaluations >= 0),
  processed               BIGINT        NOT NULL DEFAULT 0,
  tags_added              BIGINT        NOT NULL DEFAULT 0,
  -- 평가기가 다른 평가
  skipped_other           BIGINT        NOT NULL DEFAULT 0,
  -- 원본 응답을 읽을 수 없거나 체계와 어긋난 평가
  skipped_unreadable      BIGINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (backfill_id, tag_set_id)
);
