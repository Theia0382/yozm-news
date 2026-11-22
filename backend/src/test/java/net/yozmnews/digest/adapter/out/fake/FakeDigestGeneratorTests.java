package net.yozmnews.digest.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GenerationOutcome;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.input.InputItem;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.digest.domain.output.CitationCheck;
import net.yozmnews.digest.domain.output.CitationDrops;
import net.yozmnews.digest.domain.output.CitationResult;
import net.yozmnews.digest.domain.output.CitedDigest;
import net.yozmnews.digest.domain.output.CitedHeadline;
import net.yozmnews.digest.domain.output.CitedStory;
import net.yozmnews.digest.domain.output.DigestOutputReader;
import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.digest.domain.output.OutputRead;
import net.yozmnews.digest.domain.prompt.DigestPrompt;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 다이제스트 생성기의 로컬 대체물 — 출력이 실제 생성기와 같은 읽기 · 항목 대조를 지나고 항목 별칭으로만 가리킨다. 주요 뉴스 항목마다 제목 ·
 * 요약, 헤드라인 항목마다 제목(코멘트 없음). 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R12
 * <p>
 * 대체물의 출력 원문을 읽기({@link DigestOutputReader})와 항목 대조({@link CitationCheck})에 넣고, 결과를 손으로 쓴 리터럴과 견준다.
 */
class FakeDigestGeneratorTests {

	private static final LlmModel MODEL = new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true);

	/** 대체물은 프롬프트를 보지 않는다 — 요청의 꼴을 채우는 값 */
	private static final DigestPrompt PROMPT = new DigestPrompt("지시", "{}", "{}");

	private final FakeDigestGenerator generator = new FakeDigestGenerator();

	/** 주요 뉴스 항목 {@code stories} 개 다음 헤드라인 항목 {@code headlines} 개 — 항목마다 사건 하나 · 기사 하나(ID 101 …) */
	private static CandidateSet items(int stories, int headlines) {
		List<InputItem> items = new ArrayList<>();
		for (int index = 0; index < stories + headlines; index++) {
			ItemKind kind = (index < stories) ? ItemKind.TOP_STORY : ItemKind.HEADLINE;
			items.add(DigestInputs.item(kind, DigestInputs.event(101 + index, SelectionReason.SUBSCRIBED, 101 + index)));
		}
		return CandidateSet.of(DigestInputs.items(items.toArray(InputItem[]::new)));
	}

	private CitationResult generateAndCheck(CandidateSet set) {
		GenerationOutcome outcome = this.generator.generate(new DigestRequest(new UserId(1), MODEL,
				ReasoningEffort.MEDIUM, set, PROMPT));
		assertThat(outcome).isInstanceOf(GenerationOutcome.Generated.class);
		GenerationOutcome.Generated output = (GenerationOutcome.Generated) outcome;
		assertThat(output.usage()).isEqualTo(new TokenUsage(0, 0));
		assertThat(output.stop()).isEqualTo(GenerationStop.COMPLETE);
		OutputRead read = DigestOutputReader.read(output.text(), DigestOutputSchema.root(set));
		assertThat(read).isInstanceOf(OutputRead.Read.class);
		OutputRead.Read result = (OutputRead.Read) read;
		assertThat(result.overLimit()).isZero();
		// 로컬 대체물은 모든 항목 칸을 쓰므로 대체할 제목이 필요 없다
		return CitationCheck.check(result.draft(), set, Map.of());
	}

	@Test
	void writesEveryItemInItsFieldForThreeStoriesAndTwoHeadlines() {
		CandidateSet set = items(3, 2);

		assertThat(generateAndCheck(set)).isEqualTo(new CitationResult.Cited(new CitedDigest(
				"로컬 대체물이 만든 다이제스트다. 항목 5개.",
				List.of(new CitedStory(item(set, "I1"), "로컬 주요 뉴스 1", "로컬 대체물이 만든 요약이다."),
						new CitedStory(item(set, "I2"), "로컬 주요 뉴스 2", "로컬 대체물이 만든 요약이다."),
						new CitedStory(item(set, "I3"), "로컬 주요 뉴스 3", "로컬 대체물이 만든 요약이다.")),
				List.of(new CitedHeadline(item(set, "I4"), "로컬 헤드라인 1", null),
						new CitedHeadline(item(set, "I5"), "로컬 헤드라인 2", null))),
				new CitationDrops(0)));
	}

	@Test
	void writesOnlyHeadlinesWhenEveryItemIsAHeadline() {
		CandidateSet set = items(0, 2);

		assertThat(generateAndCheck(set)).isEqualTo(new CitationResult.Cited(new CitedDigest(
				"로컬 대체물이 만든 다이제스트다. 항목 2개.", List.of(),
				List.of(new CitedHeadline(item(set, "I1"), "로컬 헤드라인 1", null),
						new CitedHeadline(item(set, "I2"), "로컬 헤드라인 2", null))),
				new CitationDrops(0)));
	}

	@Test
	void writesOneStoryForOneItem() {
		CandidateSet set = items(1, 0);

		assertThat(generateAndCheck(set)).isEqualTo(new CitationResult.Cited(new CitedDigest(
				"로컬 대체물이 만든 다이제스트다. 항목 1개.",
				List.of(new CitedStory(item(set, "I1"), "로컬 주요 뉴스 1", "로컬 대체물이 만든 요약이다.")), List.of()),
				new CitationDrops(0)));
	}

	@Test
	void isNeverAskedWithoutItems() {
		assertThatThrownBy(() -> new DigestRequest(new UserId(1), MODEL, ReasoningEffort.MEDIUM, items(0, 0), PROMPT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void writesWithinTheLimitsForTheLargestDigest() {
		// 주요 뉴스 20 · 헤드라인 150 — 모든 항목 칸을 쓰고 대체가 없다
		CitationResult result = generateAndCheck(items(20, 150));

		assertThat(result).isInstanceOf(CitationResult.Cited.class);
		CitedDigest digest = ((CitationResult.Cited) result).digest();
		assertThat(digest.stories()).hasSize(20);
		assertThat(digest.headlines()).hasSize(150);
		assertThat(result.drops()).isEqualTo(new CitationDrops(0));
	}

	private static AliasedItem item(CandidateSet set, String alias) {
		return set.item(alias).orElseThrow();
	}

}
