package net.yozmnews.event.adapter.out.persistence;

/**
 * "사건 중심에 가까운 순" 정렬의 정본 SQL 조각 — 대표 기사(event-R14)와 서술 기사(event-R11)가 함께 쓴다 · docs/specs/event.md §4.
 * <p>
 * 별칭 {@code a}(배정) · {@code e}(사건) · {@code f}(그 사건 모델 버전의 기사 표현, 없을 수 있다)를 전제한다. 표현이 있는 기사를 거리 순 ·
 * 같으면 기사 ID 순으로 먼저, 표현이 없는 기사는 그 뒤에 발행 시각 · 기사 ID 순으로 둔다.
 */
final class CentroidOrder {

	static final String ORDER = """
			(f.article_id is null), cast(f.embedding as halfvec) <=> e.centroid,
			case when f.article_id is null then a.published_at end, a.article_id""";

	private CentroidOrder() {
	}

}
