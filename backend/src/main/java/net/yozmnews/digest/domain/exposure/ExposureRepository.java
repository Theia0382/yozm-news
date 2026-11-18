package net.yozmnews.digest.domain.exposure;

import java.time.Instant;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.UserId;

/**
 * 노출 기록 읽기 — 관심도의 분모. docs/specs/digest.md §5
 */
public interface ExposureRepository {

	/**
	 * 그 사용자의 {@code READY} 다이제스트 가운데 끝낸 시각이 {@code from} 이상 · {@code to} 미만인 것의 인용 기사 — 같은 날짜의 덮인
	 * 다이제스트도 센다(피드백을 받는 범위와 같다). 받은 소식(날짜별로 보이는 다이제스트만)과 다른 정의다.
	 * @return 기사 → 그 기사를 인용한 다이제스트 가운데 가장 늦게 끝낸 시각
	 */
	Map<ArticleId, Instant> latestExposures(UserId user, Instant from, Instant to);

}
