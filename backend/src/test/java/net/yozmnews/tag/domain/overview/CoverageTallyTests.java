package net.yozmnews.tag.domain.overview;

import static net.yozmnews.tag.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.tag.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.tag.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.tag.domain.result.ResultTagSet.TAG_SET;
import static net.yozmnews.tag.domain.result.ResultTagSet.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.tag.CategoryCoverage;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.SubtopicCoverage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagProbability;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicCoverageView;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.tag.domain.result.TagJudgement.Judged;
import org.junit.jupiter.api.Test;

/**
 * 주제별 보도량 집계 — 활성 버전(고정 체계 버전 1: 정치(행정부 · 국회) · 경제(금융))에 옛 버전 9 의 판정을 계보 표 리터럴로 이어 센다.
 * <p>
 * 버전 9 → 1 계보: 대통령 → 행정부(이름 변경), 정부 → 행정부 · 국회(분할), 은행 · 화폐 → 금융(병합), 퇴역 → 없음(계보 없이 지움). 기사 일곱:
 * ① 정부(분할) ② 은행 · 화폐(병합) ③ 퇴역뿐 ④ 버전 1 행정부 · 국회 ⑤ 버전 1 금융(low_confidence) ⑥ 버전 1 미분류 ⑦ 대통령(이름 변경).
 * <p>
 * 지키는 규칙: tag-R30
 */
class CoverageTallyTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-10-27T15:00:00Z"),
			Instant.parse("2026-11-03T15:00:00Z"));

	private static final TagSetId OLD = new TagSetId(9);

	private static final TagKeyMapping OLD_TO_ACTIVE = new TagKeyMapping(OLD, VERSION,
			Map.of(TagKind.TOPIC,
					Map.of("politics.president", Set.of(EXECUTIVE), "politics.government", Set.of(EXECUTIVE, ASSEMBLY),
							"economy.banking", Set.of(FINANCE), "economy.money", Set.of(FINANCE), "economy.retired",
							Set.of())));

	private static final TagKeyMapping IDENTITY = new TagKeyMapping(VERSION, VERSION, Map.of(TagKind.TOPIC,
			Map.of(EXECUTIVE, Set.of(EXECUTIVE), ASSEMBLY, Set.of(ASSEMBLY), FINANCE, Set.of(FINANCE))));

	@Test
	void articlesFollowTheLineageToActiveTopicsAndCountOncePerTopicAndCategory() {
		TopicCoverageView view = sevenArticles(Set.of());

		assertThat(view.activeVersion()).isEqualTo(VERSION);
		assertThat(view.from()).isEqualTo(Instant.parse("2026-10-27T15:00:00Z"));
		assertThat(view.to()).isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
		assertThat(view.articles()).isEqualTo(7);
		// ③ 퇴역뿐 · ⑥ 미분류
		assertThat(view.withoutActiveTopic()).isEqualTo(2);
		// 정치 3(①④⑦) — 하위의 합 5 가 아니다. 행정부 3(①④⑦) · 국회 2(①④), 경제 2 · 금융 2(②⑤ — ② 의 병합 둘은 한 번)
		assertThat(view.categories()).containsExactly(
				new CategoryCoverage("politics", "정치", 3,
						List.of(new SubtopicCoverage(EXECUTIVE, "대통령실·행정부", 3),
								new SubtopicCoverage(ASSEMBLY, "국회·정당·선거", 2))),
				new CategoryCoverage("economy", "경제", 2, List.of(new SubtopicCoverage(FINANCE, "금융", 2))));
	}

	@Test
	void aChosenCategoryExpandsToAllItsSubtopics() {
		assertThat(sevenArticles(Set.of("politics")).categories()).containsExactly(new CategoryCoverage("politics",
				"정치", 3, List.of(new SubtopicCoverage(EXECUTIVE, "대통령실·행정부", 3),
						new SubtopicCoverage(ASSEMBLY, "국회·정당·선거", 2))));
	}

	@Test
	void aChosenSubtopicStaysInItsCategoryWhoseCountIsStillTheWholeCategory() {
		assertThat(sevenArticles(Set.of(ASSEMBLY)).categories()).containsExactly(
				new CategoryCoverage("politics", "정치", 3, List.of(new SubtopicCoverage(ASSEMBLY, "국회·정당·선거", 2))));
	}

	@Test
	void aChosenCategoryWinsOverItsOwnSubtopic() {
		assertThat(sevenArticles(Set.of("politics", ASSEMBLY)).categories().getFirst().subtopics()).hasSize(2);
	}

	@Test
	void topicsMissingFromTheActiveVersionAreReportedAndRefused() {
		// 대조: 있는 키와 섞여도 없는 키만 낸다
		assertThat(CoverageTally.unknownTopics(TAG_SET, Set.of("politics", EXECUTIVE, "sports", "politics.president")))
			.containsExactly("politics.president", "sports");
		assertThat(CoverageTally.unknownTopics(TAG_SET, Set.of())).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> new CoverageTally(TAG_SET, Set.of("sports"), PERIOD));
	}

	@Test
	void noArticlesListEveryChosenTopicWithZero() {
		TopicCoverageView view = new CoverageTally(TAG_SET, Set.of(), PERIOD).view();

		assertThat(view.articles()).isZero();
		assertThat(view.withoutActiveTopic()).isZero();
		assertThat(view.categories()).containsExactly(
				new CategoryCoverage("politics", "정치", 0,
						List.of(new SubtopicCoverage(EXECUTIVE, "대통령실·행정부", 0),
								new SubtopicCoverage(ASSEMBLY, "국회·정당·선거", 0))),
				new CategoryCoverage("economy", "경제", 0, List.of(new SubtopicCoverage(FINANCE, "금융", 0))));
	}

	@Test
	void aLineageTableTowardsAnotherVersionBreaksTheSnapshotInvariant() {
		CoverageTally tally = new CoverageTally(TAG_SET, Set.of(), PERIOD);
		TagKeyMapping towardsTwo = new TagKeyMapping(VERSION, new TagSetId(2),
				Map.of(TagKind.TOPIC, Map.of(EXECUTIVE, Set.of(EXECUTIVE))));

		assertThatIllegalStateException().isThrownBy(() -> tally.add(judged(TopicStatus.TAGGED, EXECUTIVE), towardsTwo));
		// 대조: 활성 버전으로 가는 표면 센다
		tally.add(judged(TopicStatus.TAGGED, EXECUTIVE), IDENTITY);
		assertThat(tally.view().articles()).isEqualTo(1);
	}

	@Test
	void aLineageTargetThatIsNotAnActiveSubtopicBreaksTheInvariant() {
		TagKeyMapping toCategory = new TagKeyMapping(OLD, VERSION,
				Map.of(TagKind.TOPIC, Map.of("politics.president", Set.of("politics"))));

		assertThatIllegalStateException().isThrownBy(() -> new CoverageTally(TAG_SET, Set.of(), PERIOD)
			.add(judged(TopicStatus.TAGGED, "politics.president"), toCategory));
	}

	private static TopicCoverageView sevenArticles(Set<String> topics) {
		CoverageTally tally = new CoverageTally(TAG_SET, topics, PERIOD);
		tally.add(judged(TopicStatus.TAGGED, "politics.government"), OLD_TO_ACTIVE);
		tally.add(judged(TopicStatus.TAGGED, "economy.banking", "economy.money"), OLD_TO_ACTIVE);
		tally.add(judged(TopicStatus.TAGGED, "economy.retired"), OLD_TO_ACTIVE);
		tally.add(judged(TopicStatus.TAGGED, EXECUTIVE, ASSEMBLY), IDENTITY);
		tally.add(judged(TopicStatus.LOW_CONFIDENCE, FINANCE), IDENTITY);
		tally.add(judged(TopicStatus.UNCLASSIFIED), IDENTITY);
		tally.add(judged(TopicStatus.TAGGED, "politics.president"), OLD_TO_ACTIVE);
		return tally.view();
	}

	/** 판정 결과 — 대분류 · 지역은 집계가 쓰지 않아 비운다 */
	private static Judged judged(TopicStatus status, String... topics) {
		return new Judged(status, Arrays.stream(topics).map(key -> new TagProbability(key, 0.9)).toList(), List.of(),
				List.of());
	}

}
