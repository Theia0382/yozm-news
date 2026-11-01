-- source 모듈 — 출처 이름 기록. source 가 프로필에서 마지막으로 확인한 소스 이름을 남긴다.
--
-- 운영자가 쓰는 운영 설정(sources)과 다른 행에 둔다 — 등록 · 원문 수정 · 기동 대조가 쓰고, 운영자의 소스 변경과 서로를 덮어쓰지
-- 않는다. 이미 있는 소스는 다음 기동 대조가 채운다.
CREATE TABLE source_attributions (
  source_id             BIGINT      PRIMARY KEY REFERENCES sources (id),
  -- 프로필의 source.name 을 마지막으로 확인한 값
  name                  TEXT        NOT NULL,
  -- 그 값을 처음 기록한 시각 — 같은 이름을 다시 확인하면 바꾸지 않는다
  recorded_at           TIMESTAMPTZ NOT NULL
);
