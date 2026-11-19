package net.yozmnews.digest.domain.selection;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 사건마다 생성에 넘길 기사(표본) — 정본 · docs/specs/digest.md §5.
 * <p>
 * 후보 사건의 기사({@link CandidateEvent#articles()}) 가운데 (사건 ID, 기사 ID) 해시({@link #hash}) 오름차순 — 부호 없는 64비트로 견주고
 * 같으면 기사 ID 순 — 으로 앞 {@link #ARTICLES_PER_EVENT} 건을 고른다. 같은 사건 · 같은 기사면 언제나 같은 표본이고, 기사가 늘어도 앞서
 * 뽑힌 기사는 해시가 그대로라 새 기사가 그보다 앞설 때만 밀린다. 표본이 인용 · 화면의 출처 기사다.
 */
public final class EventSample {

	/** 사건 하나에서 넘기는 기사 수의 상한 — 측정 전 목표 */
	public static final int ARTICLES_PER_EVENT = 5;

	private EventSample() {
	}

	/**
	 * @return 표본 — 발행 시각 · 기사 ID 순
	 */
	public static List<ArticleFacts> of(CandidateEvent event) {
		Objects.requireNonNull(event, "event");
		long eventId = event.facts().id().value();
		Comparator<ArticleFacts> order = Comparator
			.comparing((ArticleFacts article) -> hash(eventId, article.id().value()), Long::compareUnsigned)
			.thenComparingLong(article -> article.id().value());
		return event.articles()
			.stream()
			.sorted(order)
			.limit(ARTICLES_PER_EVENT)
			.sorted(Comparator.comparing(ArticleFacts::publishedAt).thenComparingLong(article -> article.id().value()))
			.toList();
	}

	/**
	 * 고르는 순서의 해시 — {@code eventId × 0x9E3779B97F4A7C15 ^ articleId} 에 SplitMix64 의 마무리 섞기를 한 64비트 값. 실행 · JVM
	 * 과 무관하게 같다
	 */
	static long hash(long eventId, long articleId) {
		long z = (eventId * 0x9E3779B97F4A7C15L) ^ articleId;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

}
