package net.yozmnews.digest.domain.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;
import org.junit.jupiter.api.Test;

/**
 * 생성의 입력 — 후보 순서를 그대로 내고, 같은 기사 두 번 · 후보 밖의 묶음 기사 · 두 묶음에 든 기사 · 빈 묶음을 거부한다
 */
class DigestInputTests {

	private static final DigestProfile PROFILE = new DigestProfile(List.of("정치"), List.of(), "", ZoneId.of("Asia/Seoul"),
			SummaryLength.NORMAL, ReasoningEffort.MEDIUM);

	@Test
	void theCandidatesKeepTheirOrder() {
		DigestInput input = new DigestInput(PROFILE, List.of(article(7, SelectionReason.MAJOR_STORYLINE),
				article(1, SelectionReason.SUBSCRIBED)), List.of(new StorylineGroup("분쟁", List.of(new ArticleId(7)))));

		assertThat(input.digestCandidates()).containsExactly(
				new DigestCandidate(new ArticleId(7), SelectionReason.MAJOR_STORYLINE),
				new DigestCandidate(new ArticleId(1), SelectionReason.SUBSCRIBED));
	}

	@Test
	void inconsistentInputsAreRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestInput(PROFILE,
				List.of(article(1, SelectionReason.SUBSCRIBED), article(1, SelectionReason.SUBSCRIBED)), List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestInput(PROFILE,
				List.of(article(1, SelectionReason.SUBSCRIBED)),
				List.of(new StorylineGroup("분쟁", List.of(new ArticleId(9))))));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestInput(PROFILE,
				List.of(article(7, SelectionReason.MAJOR_STORYLINE)),
				List.of(new StorylineGroup("분쟁", List.of(new ArticleId(7))),
						new StorylineGroup("분쟁 둘", List.of(new ArticleId(7))))));
		assertThatIllegalArgumentException().isThrownBy(() -> new StorylineGroup("분쟁", List.of()));
	}

	@Test
	void noCandidatesIsAnInputToo() {
		assertThat(new DigestInput(PROFILE, List.of(), List.of()).digestCandidates()).isEmpty();
	}

	private static CandidateArticle article(long id, SelectionReason reason) {
		return new CandidateArticle(new DigestCandidate(new ArticleId(id), reason), "제목 " + id, null, List.of("출처"),
				Instant.parse("2026-11-17T00:00:00Z"), List.of("정치"), List.of());
	}

}
