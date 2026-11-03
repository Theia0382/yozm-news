package net.yozmnews.evaluation.domain.result;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.TopicCategory;

/**
 * 결과 해석 · 판정 시험의 고정 체계 — 버전 1, 대분류 정치(행정부 · 국회) · 경제(금융), 지역 국외 · 국내, 형식 셋, 중요도 1 ~ 5.
 * <p>
 * 체계 순서와 키의 사전 순이 어긋난다(중분류 executive → assembly → finance, 지역 world → domestic) — 순서 단언이 정렬 우연으로 통과하지
 * 않는다.
 */
public final class ResultTagSet {

	public static final TagSetId VERSION = new TagSetId(1);

	public static final String EXECUTIVE = "politics.executive";

	public static final String ASSEMBLY = "politics.assembly";

	public static final String FINANCE = "economy.finance";

	public static final TagSetView TAG_SET = new TagSetView(VERSION, 1,
			List.of(new TopicCategory("politics", "정치", "정치", List.of(
					new AskedTag(EXECUTIVE, "대통령실·행정부", "대통령실과 행정부", "대통령실과 행정부를 다루는가"),
					new AskedTag(ASSEMBLY, "국회·정당·선거", "국회와 정당", "국회와 정당을 다루는가"))),
					new TopicCategory("economy", "경제", "경제",
							List.of(new AskedTag(FINANCE, "금융", "금융", "금융을 다루는가")))),
			List.of(new AskedTag("world", "국제", "국외", "국외의 일인가"),
					new AskedTag("domestic", "국내", "국내", "국내의 일인가")),
			new FormatChoice("이 기사의 형식은", List.of(new FormatOption("straight", "스트레이트", "사실 보도", false),
					new FormatOption("opinion", "오피니언", "의견", true),
					new FormatOption("feature", "기획", "기획 기사", false))),
			new ImportanceScale("얼마나 중요한가", List.of(new ImportanceLevel(1, "사소"), new ImportanceLevel(2, "작음"),
					new ImportanceLevel(3, "보통"), new ImportanceLevel(4, "큼"), new ImportanceLevel(5, "매우 큼"))));

	private ResultTagSet() {
	}

	/** 버전 1 의 태그 기준 · 저장 기준 — 주제 · 지역 */
	public static TagSetThresholds thresholds(String topicTag, String topicStore, String regionTag, String regionStore) {
		return new TagSetThresholds(VERSION,
				new TagThresholds(MultiTagKind.TOPIC, new BigDecimal(topicTag), new BigDecimal(topicStore)),
				new TagThresholds(MultiTagKind.REGION, new BigDecimal(regionTag), new BigDecimal(regionStore)));
	}

	public static StoredTag topic(String key, double probability) {
		return new StoredTag(MultiTagKind.TOPIC, key, probability);
	}

	public static StoredTag region(String key, double probability) {
		return new StoredTag(MultiTagKind.REGION, key, probability);
	}

}
