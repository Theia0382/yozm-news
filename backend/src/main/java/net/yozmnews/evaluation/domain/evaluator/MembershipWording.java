package net.yozmnews.evaluation.domain.evaluator;

import java.util.List;
import java.util.Objects;

/**
 * 사건 소속 질문 문구와 그 판 — docs/specs/evaluation.md §4 · evaluation-R37.
 * <p>
 * 질문 문장은 문구 뒤에 서술 제목을 한 줄씩 {@code "- "} 로 붙인 것이다. 문구나 조립 모양을 바꾸면 판을 올린다 — 판은 판정 원본에 남아 바꾼
 * 전후의 판정을 가른다.
 *
 * @param version 문구 판
 * @param text 문구
 */
public record MembershipWording(String version, String text) {

	/** 지금 문구 — 정본 */
	public static final MembershipWording CURRENT = new MembershipWording("membership-2",
			"Do the following articles already report the happening this article reports, so that this article "
					+ "reports no new happening beyond them? A happening is one thing that occurred once: one act, "
					+ "occurrence, statement or decision. A response by someone else, another act in the same matter, "
					+ "or a similar occurrence elsewhere is a new happening. If neither this article nor the following "
					+ "articles report a particular happening, answer yes if they describe the same trend.");

	/** 문구와 서술 제목 사이 · 제목 사이 */
	private static final String LINE = "\n- ";

	/**
	 * @throws IllegalArgumentException 판 · 문구가 공백뿐일 때
	 */
	public MembershipWording {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(text, "text");
		if (version.isBlank() || text.isBlank()) {
			throw new IllegalArgumentException("문구 판 · 문구가 비었다");
		}
	}

	/**
	 * 후보 하나의 질문 문장 — 정본
	 * @throws IllegalArgumentException 제목이 없을 때
	 */
	public String question(List<String> titles) {
		if (titles.isEmpty()) {
			throw new IllegalArgumentException("서술 제목이 없다");
		}
		return this.text + LINE + String.join(LINE, titles);
	}

}
