package net.yozmnews.digest.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GenerationOutcome;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.output.CitationCheck;
import net.yozmnews.digest.domain.output.CitationDrops;
import net.yozmnews.digest.domain.output.CitationResult;
import net.yozmnews.digest.domain.output.CitedDigest;
import net.yozmnews.digest.domain.output.CitedHeadline;
import net.yozmnews.digest.domain.output.CitedStory;
import net.yozmnews.digest.domain.output.DigestOutputReader;
import net.yozmnews.digest.domain.output.OutputRead;
import net.yozmnews.digest.domain.prompt.DigestPrompt;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 다이제스트 생성기의 로컬 대체물 — 출력이 실제 생성기와 같은 읽기 · 인용 검증을 지나고 후보 별칭으로만 인용한다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R12
 * <p>
 * 대체물의 출력 원문을 읽기({@link DigestOutputReader})와 인용 검증({@link CitationCheck})에 넣고, 결과를 손으로 쓴 리터럴과
 * 견준다.
 */
class FakeDigestGeneratorTests {

	private static final LlmModel MODEL = new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true);

	/** 대체물은 프롬프트를 보지 않는다 — 요청의 꼴을 채우는 값 */
	private static final DigestPrompt PROMPT = new DigestPrompt("지시", "{}", "{}");

	private final FakeDigestGenerator generator = new FakeDigestGenerator();

	private static List<DigestCandidate> candidates(int count) {
		List<DigestCandidate> candidates = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			candidates.add(new DigestCandidate(new ArticleId(100 + index), SelectionReason.SUBSCRIBED));
		}
		return candidates;
	}

	private static DigestCandidate candidate(long articleId) {
		return new DigestCandidate(new ArticleId(articleId), SelectionReason.SUBSCRIBED);
	}

	private CitationResult generateAndCheck(int count) {
		CandidateSet set = CandidateSet.of(candidates(count));
		GenerationOutcome outcome = this.generator.generate(new DigestRequest(new UserId(1), MODEL,
				ReasoningEffort.MEDIUM, set, PROMPT));
		assertThat(outcome).isInstanceOf(GenerationOutcome.Generated.class);
		GenerationOutcome.Generated output = (GenerationOutcome.Generated) outcome;
		assertThat(output.usage()).isEqualTo(new TokenUsage(0, 0));
		assertThat(output.stop()).isEqualTo(GenerationStop.COMPLETE);
		OutputRead read = DigestOutputReader.read(output.text());
		assertThat(read).isInstanceOf(OutputRead.Read.class);
		OutputRead.Read result = (OutputRead.Read) read;
		assertThat(result.overLimit()).isZero();
		return CitationCheck.check(result.draft(), set);
	}

	@Test
	void citesThreeStoriesAndTheRestAsHeadlinesForFiveCandidates() {
		assertThat(generateAndCheck(5)).isEqualTo(new CitationResult.Cited(new CitedDigest(
				"로컬 대체물이 만든 다이제스트다. 후보 5건.",
				List.of(new CitedStory("로컬 주요 뉴스 1", "로컬 대체물이 만든 요약이다.", List.of(candidate(101))),
						new CitedStory("로컬 주요 뉴스 2", "로컬 대체물이 만든 요약이다.", List.of(candidate(102))),
						new CitedStory("로컬 주요 뉴스 3", "로컬 대체물이 만든 요약이다.", List.of(candidate(103)))),
				List.of(new CitedHeadline(candidate(104), null), new CitedHeadline(candidate(105), null))),
				new CitationDrops(0, 0)));
	}

	@Test
	void citesThreeStoriesAndNoHeadlinesForThreeCandidates() {
		CitationResult result = generateAndCheck(3);

		assertThat(result).isInstanceOf(CitationResult.Cited.class);
		CitedDigest digest = ((CitationResult.Cited) result).digest();
		assertThat(digest.stories()).hasSize(3);
		assertThat(digest.headlines()).isEmpty();
		assertThat(result.drops()).isEqualTo(new CitationDrops(0, 0));
	}

	@Test
	void citesOneStoryForOneCandidate() {
		assertThat(generateAndCheck(1)).isEqualTo(new CitationResult.Cited(new CitedDigest(
				"로컬 대체물이 만든 다이제스트다. 후보 1건.",
				List.of(new CitedStory("로컬 주요 뉴스 1", "로컬 대체물이 만든 요약이다.", List.of(candidate(101)))),
				List.of()), new CitationDrops(0, 0)));
	}

	@Test
	void isNeverAskedWithoutCandidates() {
		assertThatThrownBy(() -> new DigestRequest(new UserId(1), MODEL, ReasoningEffort.MEDIUM,
				CandidateSet.of(List.of()), PROMPT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void citesOnlyWithinTheLimitsForManyCandidates() {
		CitationResult result = generateAndCheck(60);

		assertThat(result).isInstanceOf(CitationResult.Cited.class);
		CitedDigest digest = ((CitationResult.Cited) result).digest();
		assertThat(digest.stories()).hasSize(3);
		assertThat(digest.headlines()).hasSize(57);
		assertThat(result.drops()).isEqualTo(new CitationDrops(0, 0));
	}

}
