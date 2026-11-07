package net.yozmnews.event.domain.event;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.domain.assignment.CentroidUpdate;

/**
 * 잠근 사건 한 행 — 갱신의 입력이자 결과 · docs/specs/event.md §4 · event-R12.
 *
 * @param modelVersion 사건을 연 기사 표현의 모델 버전 — 같은 모델 버전의 기사만 붙인다
 * @param centroid 중심 벡터 — 구성 기사 벡터의 평균. 복사해 받고 복사해 낸다
 * @param articleCount 구성 기사 수 — 1 이상
 * @param firstArticleAt 구성 기사 가운데 가장 이른 발행 시각
 * @param lastArticleAt 구성 기사 가운데 가장 늦은 발행 시각
 * @param openedAt 사건을 연 시각 — 최대 기간으로 닫는 기준
 * @param lastJoinedAt 마지막으로 기사가 붙은 시각 — 닫는 기간의 기준
 */
public record LockedEvent(long id, EventStatus status, String modelVersion, float[] centroid, int articleCount,
		Instant firstArticleAt, Instant lastArticleAt, Instant openedAt, Instant lastJoinedAt) {

	/**
	 * @throws IllegalArgumentException 중심이 비었거나 기사 수가 1 미만이거나 첫 시각이 마지막 시각보다 늦을 때
	 */
	public LockedEvent {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(openedAt, "openedAt");
		Objects.requireNonNull(lastJoinedAt, "lastJoinedAt");
		if (centroid.length == 0) {
			throw new IllegalArgumentException("중심 벡터가 비었다");
		}
		if (articleCount < 1) {
			throw new IllegalArgumentException("사건의 기사 수는 1 이상이다: " + articleCount);
		}
		if (firstArticleAt.isAfter(lastArticleAt)) {
			throw new IllegalArgumentException("첫 기사 시각이 마지막 기사 시각보다 늦다: " + firstArticleAt + " > " + lastArticleAt);
		}
		centroid = centroid.clone();
	}

	@Override
	public float[] centroid() {
		return this.centroid.clone();
	}

	/**
	 * 기사 하나를 더한 사건 — 중심은 그 벡터를 더한 평균({@link CentroidUpdate#add}), 수 + 1, 첫 · 마지막 기사 시각은 그 발행 시각까지
	 * 넓히고, 마지막으로 붙은 시각은 지금이다. 배정과 재판정의 옮기기가 함께 쓰는 정본이다.
	 * @param now 붙인 시각
	 * @throws IllegalArgumentException 벡터 길이가 중심과 다를 때
	 */
	public LockedEvent joined(float[] vector, Instant publishedAt, Instant now) {
		return new LockedEvent(this.id, this.status, this.modelVersion,
				CentroidUpdate.add(this.centroid, this.articleCount, vector), this.articleCount + 1,
				publishedAt.isBefore(this.firstArticleAt) ? publishedAt : this.firstArticleAt,
				publishedAt.isAfter(this.lastArticleAt) ? publishedAt : this.lastArticleAt, this.openedAt, now);
	}

	/**
	 * 다른 사건을 합친 이 사건 — 합친 사건의 정본이다(event-R13). 중심은 기사 수 가중 평균({@link CentroidUpdate#merge}), 수는 합, 첫 ·
	 * 마지막 기사 시각과 연 시각은 이른 쪽 · 늦은 쪽 · 이른 쪽, 마지막으로 붙은 시각은 늦은 쪽이다. ID · 상태 · 모델 버전은 이 사건의 것이다.
	 * @throws IllegalArgumentException 중심 길이가 다를 때
	 */
	public LockedEvent absorbed(LockedEvent other) {
		return new LockedEvent(this.id, this.status, this.modelVersion,
				CentroidUpdate.merge(this.centroid, this.articleCount, other.centroid, other.articleCount),
				this.articleCount + other.articleCount, earlier(this.firstArticleAt, other.firstArticleAt),
				later(this.lastArticleAt, other.lastArticleAt), earlier(this.openedAt, other.openedAt),
				later(this.lastJoinedAt, other.lastJoinedAt));
	}

	private static Instant earlier(Instant a, Instant b) {
		return b.isBefore(a) ? b : a;
	}

	private static Instant later(Instant a, Instant b) {
		return b.isAfter(a) ? b : a;
	}

}
