package net.yozmnews.storyline.domain.linking;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.EventDescriptor;

/**
 * 띠 안 후보를 물을 때의 정본 — 후보 스토리라인을 서술하는 구성 사건 고르기와 다시 물을 때 · docs/specs/storyline.md §5 ·
 * storyline-R36.
 */
public final class LinkingAsk {

	/**
	 * 후보 스토리라인을 서술하는 구성 사건 수 — 측정 전 목표. 서술 기사 상한({@link EventDescriptor#MAX_ARTICLES})을 넘지 않는다
	 */
	public static final int DESCRIBED_EVENTS = 5;

	private LinkingAsk() {
	}

	/**
	 * 후보 스토리라인의 구성 사건 가운데 판정할 사건과 중심 코사인이 큰 순(같으면 사건 ID 순) {@link #DESCRIBED_EVENTS} 개 — 판정할 사건
	 * 자신은 뺀다. 코사인을 셀 수 없는(NaN) 사건은 뒤로 간다.
	 * @param members 구성 사건 ID → 중심 — 모델 버전이 같은 것만
	 */
	public static List<Long> describe(Subject subject, long subjectId, Map<Long, float[]> members) {
		float[] centroid = subject.centroid();
		Comparator<Map.Entry<Long, Double>> order = Comparator
			.comparing((Map.Entry<Long, Double> entry) -> Double.isNaN(entry.getValue()))
			.thenComparing(Map.Entry::getValue, Comparator.reverseOrder())
			.thenComparing(Map.Entry::getKey);
		return members.entrySet()
			.stream()
			.filter(entry -> entry.getKey() != subjectId)
			.map(entry -> Map.entry(entry.getKey(), Cosine.of(centroid, entry.getValue())))
			.sorted(order)
			.limit(DESCRIBED_EVENTS)
			.map(Map.Entry::getKey)
			.toList();
	}

	/**
	 * 다시 물을 때인가 — 판정할 사건의 기사 수나 상대의 크기(후보 스토리라인은 구성 사건 수, 짝 이웃은 기사 수)가 물었을 때의 두 배
	 * 이상이다.
	 */
	public static boolean due(AskedRecord record, int eventArticlesNow, int otherSizeNow) {
		return eventArticlesNow >= 2 * record.eventArticles() || otherSizeNow >= 2 * record.otherSize();
	}

}
