package net.yozmnews.storyline.domain.linking;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventKeyTerm;
import net.yozmnews.event.EventProfile;

/**
 * 스토리라인의 표현과 대표 사건 — 구성 사건 표현으로 정하는 정본 · docs/specs/storyline.md §5 · storyline-R7.
 *
 * @param modelVersion 구성 사건의 모델 버전
 * @param centroid 구성 사건 중심의 기사 수 가중 평균 — 복사해 받고 복사해 낸다
 * @param articleCount 구성 사건 기사 수의 합
 * @param firstArticleAt 구성 사건의 가장 이른 첫 기사 시각
 * @param lastArticleAt 구성 사건의 가장 늦은 마지막 기사 시각
 * @param keyTerms 말 → 구성 사건마다 상위 {@link LinkingScoring#KEY_TERMS} 개 핵심 말의 기사 수 합
 * @param representativeEventId 기사 수가 가장 많은 구성 사건(같으면 ID 가 작은 것)
 */
public record Representation(String modelVersion, float[] centroid, int articleCount, Instant firstArticleAt,
		Instant lastArticleAt, Map<String, Integer> keyTerms, long representativeEventId) {

	/** 기사 수가 많은 순, 같으면 말의 코드 포인트 순(UTF-8 바이트 순과 같다 — DB 의 {@code COLLATE "C"}) */
	private static final Comparator<Map.Entry<String, Integer>> TOP = Comparator
		.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
		.reversed()
		.thenComparing(Map.Entry::getKey, Representation::byCodePoint);

	public Representation {
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		centroid = centroid.clone();
		keyTerms = Collections.unmodifiableMap(new LinkedHashMap<>(keyTerms));
	}

	@Override
	public float[] centroid() {
		return this.centroid.clone();
	}

	/**
	 * 구성 사건들의 표현.
	 * @throws IllegalArgumentException 비었거나 모델 버전 · 중심 길이가 다를 때
	 */
	public static Representation of(List<EventProfile> members) {
		if (members.isEmpty()) {
			throw new IllegalArgumentException("구성 사건이 없다");
		}
		EventProfile first = members.getFirst();
		int dimensions = first.centroid().length;
		double[] sum = new double[dimensions];
		long count = 0;
		Instant earliest = first.firstArticleAt();
		Instant latest = first.lastArticleAt();
		Map<String, Integer> terms = new TreeMap<>(Representation::byCodePoint);
		EventProfile representative = first;
		for (EventProfile member : members) {
			if (!member.modelVersion().equals(first.modelVersion())) {
				throw new IllegalArgumentException(
						"구성 사건의 모델 버전이 다르다: " + first.modelVersion() + " ≠ " + member.modelVersion());
			}
			float[] centroid = member.centroid();
			if (centroid.length != dimensions) {
				throw new IllegalArgumentException("구성 사건의 중심 길이가 다르다: " + dimensions + " ≠ " + centroid.length);
			}
			for (int i = 0; i < dimensions; i++) {
				sum[i] += (double) centroid[i] * member.articleCount();
			}
			count += member.articleCount();
			earliest = member.firstArticleAt().isBefore(earliest) ? member.firstArticleAt() : earliest;
			latest = member.lastArticleAt().isAfter(latest) ? member.lastArticleAt() : latest;
			for (EventKeyTerm term : member.keyTerms().stream().limit(LinkingScoring.KEY_TERMS).toList()) {
				terms.merge(term.term(), term.articleCount(), Integer::sum);
			}
			if (member.articleCount() > representative.articleCount()
					|| (member.articleCount() == representative.articleCount()
							&& member.id().value() < representative.id().value())) {
				representative = member;
			}
		}
		float[] centroid = new float[dimensions];
		for (int i = 0; i < dimensions; i++) {
			centroid[i] = (float) (sum[i] / count);
		}
		return new Representation(first.modelVersion(), centroid, Math.toIntExact(count), earliest, latest, terms,
				representative.id().value());
	}

	/** 한 사건을 뺀 구성 사건들의 표현 — 남는 사건이 없으면 빈 값 */
	public static Optional<Representation> without(List<EventProfile> members, EventId excluded) {
		List<EventProfile> rest = members.stream().filter(member -> !member.id().equals(excluded)).toList();
		return rest.isEmpty() ? Optional.empty() : Optional.of(of(rest));
	}

	/** 기사 수가 많은 순(같으면 말 바이트 순) 상위 {@code n} 개 */
	public List<String> topTerms(int n) {
		return this.keyTerms.entrySet().stream().sorted(TOP).limit(n).map(Map.Entry::getKey).toList();
	}

	/** 판정할 사건과 견줄 이 표현 — 코사인은 {@link Cosine}, 핵심 말은 상위 {@link LinkingScoring#KEY_TERMS} 개 */
	public Compared comparedTo(long storylineId, Subject subject) {
		return new Compared(storylineId, Cosine.of(subject.centroid(), this.centroid), this.firstArticleAt,
				this.lastArticleAt, topTerms(LinkingScoring.KEY_TERMS));
	}

	private static int byCodePoint(String a, String b) {
		return Arrays.compare(a.codePoints().toArray(), b.codePoints().toArray());
	}

}
