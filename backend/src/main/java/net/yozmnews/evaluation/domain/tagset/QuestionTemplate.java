package net.yozmnews.evaluation.domain.tagset;

import java.util.Objects;

/**
 * 질문 틀과 질문 문장 만들기의 정본 — docs/specs/evaluation.md §4 · evaluation-R8 · evaluation-R9.
 * <p>
 * 틀은 자리표시자 {@value #PLACEHOLDER} 를 정확히 하나 갖는다. 질문 문장은 그 자리를 태그 설명으로 바꾼 것이다.
 */
public record QuestionTemplate(String text) {

	/** 자리표시자 */
	public static final String PLACEHOLDER = "{description}";

	/**
	 * @throws IllegalArgumentException 자리표시자가 정확히 하나가 아닐 때
	 */
	public QuestionTemplate {
		Objects.requireNonNull(text, "text");
		if (placeholders(text) != 1) {
			throw new IllegalArgumentException("질문 틀은 자리표시자 " + PLACEHOLDER + " 를 정확히 하나 갖는다: " + text);
		}
	}

	/** 태그 설명을 끼운 질문 문장 */
	public String render(String description) {
		Objects.requireNonNull(description, "description");
		return this.text.replace(PLACEHOLDER, description);
	}

	/** 문구에 든 자리표시자 수 */
	public static int placeholders(String text) {
		int count = 0;
		for (int at = text.indexOf(PLACEHOLDER); at >= 0; at = text.indexOf(PLACEHOLDER, at + PLACEHOLDER.length())) {
			count++;
		}
		return count;
	}

}
