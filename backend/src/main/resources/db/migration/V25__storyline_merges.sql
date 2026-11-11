-- storyline 모듈 — 합쳐진 스토리라인의 가리킴(docs/specs/storyline.md §6 · storyline-R18).
--
-- 합쳐진 스토리라인은 행을 남겨 남는 스토리라인을 가리킨다. 가리킴은 평평하고, 남는 스토리라인을 지우면 가리키는 행도 함께 지워진다.
-- 합쳐진 행의 표현 칸은 합칠 때의 값이다.

ALTER TABLE storylines
  ADD COLUMN merged_into BIGINT REFERENCES storylines ON DELETE CASCADE,
  ADD COLUMN merged_at   TIMESTAMPTZ,
  ADD CONSTRAINT storylines_merge_pair_check CHECK ((merged_into IS NULL) = (merged_at IS NULL)),
  ADD CONSTRAINT storylines_merge_self_check CHECK (merged_into <> id);

-- 후보는 합쳐지지 않은 행만 본다(storyline-R9)
DROP INDEX storylines_model_last_idx;
CREATE INDEX storylines_model_last_idx ON storylines (model_version, last_article_at) WHERE merged_into IS NULL;

-- 가리킴을 평평하게 고치기 · 남는 스토리라인을 지울 때의 연쇄 삭제가 쓴다
CREATE INDEX storylines_merged_into_idx ON storylines (merged_into) WHERE merged_into IS NOT NULL;
