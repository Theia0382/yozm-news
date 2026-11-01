-- article 모듈 — 운영자 수집 지표가 기간 안 엔트리를 센다(article-R13). 모든 피드의 seen_at 범위 조건이다.
CREATE INDEX article_feed_entries_seen_at_idx ON article_feed_entries (seen_at);
