package net.yozmnews.storyline.domain.linking;

import java.util.Objects;

/**
 * 물은 기록 한 행 — 물은 결과와 물었을 때의 두 쪽 크기 · docs/specs/storyline.md §4 · storyline-R36.
 *
 * @param eventArticles 물었을 때 판정할 사건의 기사 수
 * @param otherSize 물었을 때 상대의 크기 — 후보 스토리라인이면 구성 사건 수, 짝 이웃이면 기사 수
 */
public record AskedRecord(Asked asked, int eventArticles, int otherSize) {

	/**
	 * @throws IllegalArgumentException 크기가 1 미만일 때
	 */
	public AskedRecord {
		Objects.requireNonNull(asked, "asked");
		if (eventArticles < 1 || otherSize < 1) {
			throw new IllegalArgumentException("크기는 1 이상이다: " + eventArticles + " · " + otherSize);
		}
	}

}
