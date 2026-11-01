package net.yozmnews.article.domain;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.EntryCounts;
import net.yozmnews.source.FeedId;

/** 엔트리 집계 조회 — 읽기만 한다. docs/specs/article.md §4 · article-R13 */
public interface EntryStatisticsRepository {

	/** 기간 안(받은 시각이 시작 이상 끝 미만) 엔트리를 피드 · 칸마다 센다 — 엔트리가 없는 (피드, 칸)은 행이 없다 */
	List<EntryCountRow> countEntries(EntryWindow window);

	/**
	 * 피드 하나의 칸 하나에서 센 개수.
	 * @param bucket 칸 번호 — 0 이 기간의 첫 칸이다
	 */
	record EntryCountRow(FeedId feedId, int bucket, EntryCounts counts) {

		public EntryCountRow {
			Objects.requireNonNull(feedId, "feedId");
			Objects.requireNonNull(counts, "counts");
		}

	}

}
