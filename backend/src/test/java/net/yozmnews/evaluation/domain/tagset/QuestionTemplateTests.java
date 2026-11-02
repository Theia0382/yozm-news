package net.yozmnews.evaluation.domain.tagset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 질문 틀 — 자리표시자 하나에 태그 설명을 끼워 질문 문장을 만든다.
 * <p>
 * 지키는 규칙: evaluation-R8 · evaluation-R9
 */
class QuestionTemplateTests {

	@Test
	void rendersTheDescriptionIntoThePlaceholder() {
		assertThat(new QuestionTemplate("앞: {description}").render("설명")).isEqualTo("앞: 설명");
		assertThat(new QuestionTemplate("{description} 인가").render("주식 · 채권")).isEqualTo("주식 · 채권 인가");
	}

	@Test
	void keepsAPlaceholderLikeTextInTheDescription() {
		assertThat(new QuestionTemplate("앞: {description}").render("{description}")).isEqualTo("앞: {description}");
	}

	@Test
	void requiresExactlyOnePlaceholder() {
		assertThatIllegalArgumentException().isThrownBy(() -> new QuestionTemplate("자리표시자 없음"));
		assertThatIllegalArgumentException().isThrownBy(() -> new QuestionTemplate("{description} {description}"));
		assertThat(QuestionTemplate.placeholders("{description}{description}{description}")).isEqualTo(3);
		assertThat(QuestionTemplate.placeholders("{descriptio}")).isZero();
	}

}
