package net.yozmnews.evaluation.domain.jev;

import static net.yozmnews.evaluation.domain.jev.JevFixtures.TAG_SET;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.evaluation.domain.evaluator.CandidateEvent;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import org.junit.jupiter.api.Test;

/**
 * Jev 요청 본문 — 질문 이름 · 질문 문장 · 선택지 · 수준 순서, state 형식. 사건 소속 판정의 질문 · state.
 * <p>
 * 지키는 규칙: evaluation-R14 · evaluation-R37
 */
class JevRequestTests {

	private static final EvaluationInput INPUT = new EvaluationInput("합당론 내놓은 대표", "지도부 안에서 불쾌 기류가 돈다",
			List.of(new FeedContext("경향신문", "정치", List.of("정치일반", "국회")), new FeedContext("동아일보", null, List.of())));

	@Test
	void asksEveryQuestionOfTheTagSetInOneRequest() {
		assertThat(JevRequest.body("jev-latest", INPUT, TAG_SET)).isEqualTo("{\"model\":\"jev-latest\","
				+ "\"state\":\"출처: 경향신문 / 피드 섹션: 정치 / 카테고리: 정치일반, 국회\\n출처: 동아일보\\n제목: 합당론 내놓은 대표\\n요약: 지도부 안에서 불쾌 기류가 돈다\","
				+ "\"questions\":{"
				+ "\"topic.politics.executive\":{\"type\":\"noul\",\"instructions\":\"주제가 대통령실과 행정부인가\"},"
				+ "\"topic.politics.legislature\":{\"type\":\"noul\",\"instructions\":\"주제가 국회와 정당인가\"},"
				+ "\"region.us\":{\"type\":\"noul\",\"instructions\":\"무대가 미국인가\"},"
				+ "\"region.domestic\":{\"type\":\"noul\",\"instructions\":\"무대가 대한민국인가\"},"
				+ "\"format\":{\"type\":\"choice\",\"instructions\":\"이 기사의 형식은 무엇인가\","
				+ "\"criteria\":{\"straight\":\"사실 보도\",\"opinion\":\"필자의 의견\"}},"
				+ "\"importance\":{\"type\":\"score\",\"instructions\":\"얼마나 중요한가\","
				+ "\"criteria\":[\"가벼운 화제\",\"업계 소식\",\"사회 전반\"]}}}");
	}

	@Test
	void probesWithTheSameQuestionsAndAnEmptyState() {
		assertThat(JevRequest.probeBody("jev-1.14.0", TAG_SET)).startsWith("{\"model\":\"jev-1.14.0\",\"state\":\"\","
				+ "\"questions\":{\"topic.politics.executive\":{\"type\":\"noul\",\"instructions\":\"주제가 대통령실과 행정부인가\"},")
			.endsWith("\"criteria\":[\"가벼운 화제\",\"업계 소식\",\"사회 전반\"]}}}");
	}

	@Test
	void leavesOutTheSummaryLineWithoutASummary() {
		EvaluationInput input = new EvaluationInput("오늘 날씨", null,
				List.of(new FeedContext("경향신문", "과학·환경", List.of("기후·날씨"))));

		assertThat(JevRequest.state(input)).isEqualTo("출처: 경향신문 / 피드 섹션: 과학·환경 / 카테고리: 기후·날씨\n제목: 오늘 날씨");
	}

	@Test
	void leavesOutMissingPiecesOfAFeedLine() {
		EvaluationInput input = new EvaluationInput("제목", "요약",
				List.of(new FeedContext(null, "경제", List.of()), new FeedContext(null, null, List.of("부동산"))));

		assertThat(JevRequest.state(input)).isEqualTo("피드 섹션: 경제\n카테고리: 부동산\n제목: 제목\n요약: 요약");
	}

	@Test
	void writesNoLineForAFeedWithoutAnyValue() {
		EvaluationInput input = new EvaluationInput("제목", null, List.of(new FeedContext(null, null, List.of())));

		assertThat(JevRequest.state(input)).isEqualTo("제목: 제목");
	}

	@Test
	void writesOnlyTheTitleWithoutFeeds() {
		assertThat(JevRequest.state(new EvaluationInput("제목", null, List.of()))).isEqualTo("제목: 제목");
	}

	@Test
	void asksOneYesNoQuestionPerCandidateEventInTheGivenOrder() {
		MembershipInput input = new MembershipInput("서울 시장 선거 결과", "개표가 끝났다",
				List.of(new CandidateEvent(15, List.of("부산 항만 파업", "부산항 물류 차질")),
						new CandidateEvent(12, List.of("서울 시장 선거 개표"))),
				new MembershipWording("w-test", "같은 사건인가"));

		assertThat(JevRequest.membershipBody("jev-latest", input)).isEqualTo("{\"model\":\"jev-latest\","
				+ "\"state\":\"제목: 서울 시장 선거 결과\\n요약: 개표가 끝났다\",\"questions\":{"
				+ "\"event.15\":{\"type\":\"noul\",\"instructions\":\"같은 사건인가\\n- 부산 항만 파업\\n- 부산항 물류 차질\"},"
				+ "\"event.12\":{\"type\":\"noul\",\"instructions\":\"같은 사건인가\\n- 서울 시장 선거 개표\"}}}");
	}

	@Test
	void leavesOutTheSummaryLineOfTheJudgedArticleWithoutASummary() {
		MembershipInput input = new MembershipInput("서울 시장 선거 결과", null,
				List.of(new CandidateEvent(12, List.of("서울 시장 선거 개표"))), new MembershipWording("w-test", "같은 사건인가"));

		assertThat(JevRequest.membershipBody("jev-latest", input)).isEqualTo("{\"model\":\"jev-latest\","
				+ "\"state\":\"제목: 서울 시장 선거 결과\",\"questions\":{"
				+ "\"event.12\":{\"type\":\"noul\",\"instructions\":\"같은 사건인가\\n- 서울 시장 선거 개표\"}}}");
	}

	@Test
	void probesTheMembershipWithTheSameQuestionsAndAnEmptyState() {
		MembershipInput input = new MembershipInput("서울 시장 선거 결과", "개표가 끝났다",
				List.of(new CandidateEvent(12, List.of("서울 시장 선거 개표"))), new MembershipWording("w-test", "같은 사건인가"));

		assertThat(JevRequest.membershipProbeBody("jev-1.14.0", input)).isEqualTo("{\"model\":\"jev-1.14.0\","
				+ "\"state\":\"\",\"questions\":{"
				+ "\"event.12\":{\"type\":\"noul\",\"instructions\":\"같은 사건인가\\n- 서울 시장 선거 개표\"}}}");
	}

}
