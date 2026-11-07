package net.yozmnews.evaluation.domain.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.JudgedEvent;
import net.yozmnews.evaluation.domain.evaluator.Membership;
import org.junit.jupiter.api.Test;

/**
 * 저장할 판정 원본 — 물은 후보의 순서 · 서술 기사 · 확률을 판정 결과에서 옮긴다.
 * <p>
 * 지키는 규칙: evaluation-R38
 */
class MembershipRecordTests {

	private static final Instant AT = Instant.parse("2026-11-05T06:30:00Z");

	private static final Membership MEMBERSHIP = new Membership(Map.of(12L, 0.83, 15L, 0.07), "jev", "jev-1.14.0", 812,
			"{}");

	@Test
	void takesTheCandidatesInTheAskedOrderWithTheirProbabilities() {
		Map<Long, List<ArticleId>> asked = new LinkedHashMap<>();
		asked.put(15L, List.of(new ArticleId(7)));
		asked.put(12L, List.of(new ArticleId(3), new ArticleId(4)));

		assertThat(MembershipRecord.of(new ArticleId(5), asked, MEMBERSHIP, "membership-1", AT))
			.isEqualTo(new MembershipRecord(new ArticleId(5),
					List.of(new JudgedEvent(15, List.of(new ArticleId(7)), 0.07),
							new JudgedEvent(12, List.of(new ArticleId(3), new ArticleId(4)), 0.83)),
					"jev", "jev-1.14.0", "membership-1", 812, "{}", AT));
	}

	@Test
	void refusesAnAskedCandidateWithoutAProbability() {
		assertThatIllegalArgumentException().isThrownBy(() -> MembershipRecord.of(new ArticleId(5),
				Map.of(99L, List.of(new ArticleId(7))), MEMBERSHIP, "membership-1", AT));
	}

	@Test
	void refusesNoCandidatesABlankWordingVersionOrACandidateWithoutDescriptions() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipRecord(new ArticleId(5), List.of(), "jev",
				"jev-1.14.0", "membership-1", 812, "{}", AT));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipRecord(new ArticleId(5),
					List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.83)), "jev", "jev-1.14.0", " ", 812,
					"{}", AT));
		assertThatIllegalArgumentException().isThrownBy(() -> new JudgedEvent(12, List.of(), 0.83));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new JudgedEvent(12, List.of(new ArticleId(3)), 1.01));
	}

}
