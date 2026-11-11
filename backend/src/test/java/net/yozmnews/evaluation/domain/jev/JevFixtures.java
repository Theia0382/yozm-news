package net.yozmnews.evaluation.domain.jev;

import java.util.List;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TopicCategory;

/**
 * Jev 판정 시험이 함께 쓰는 작은 질문 묶음 — 주제 중분류 2 · 지역 2 · 형식 2 · 중요도 수준 3. 지역 · 형식은 체계 순서와 키 순서가 다르다.
 */
final class JevFixtures {

	static final TagQuestions QUESTIONS = new TagQuestions(
			List.of(new TopicCategory("politics", "정치", "국내 정치", List.of(
					new AskedTag("politics.executive", "대통령실·행정부", "대통령실과 행정부", "주제가 대통령실과 행정부인가"),
					new AskedTag("politics.legislature", "국회·정당", "국회와 정당", "주제가 국회와 정당인가")))),
			List.of(new AskedTag("us", "미국", "미국", "무대가 미국인가"),
					new AskedTag("domestic", "국내", "대한민국", "무대가 대한민국인가")),
			new FormatChoice("이 기사의 형식은 무엇인가", List.of(new FormatOption("straight", "스트레이트", "사실 보도", false),
					new FormatOption("opinion", "오피니언", "필자의 의견", true))),
			new ImportanceScale("얼마나 중요한가",
					List.of(new ImportanceLevel(1, "가벼운 화제"), new ImportanceLevel(2, "업계 소식"), new ImportanceLevel(3, "사회 전반"))));

	private JevFixtures() {
	}

}
