package net.yozmnews.digest.domain.exposure;

import java.time.Instant;
import java.util.List;

import net.yozmnews.digest.domain.interest.ExposureRow;
import net.yozmnews.platform.UserId;

/**
 * 노출 기록 읽기 — 관심도의 분모. docs/specs/digest.md §5 · digest-R24
 */
public interface ExposureRepository {

	/**
	 * 그 사용자의 {@code READY} 다이제스트 가운데 끝낸 시각이 {@code from} 이상 · {@code to} 미만인 것의 섹션 사건 행 — 같은 날짜의
	 * 덮인 다이제스트도 센다(피드백을 받는 범위와 같다). 받은 소식(날짜별로 보이는 다이제스트만)과 다른 정의다. 행마다 몫은 1 ÷ 그 섹션의 사건 행
	 * 수다 — 생성 때 항목에 든 사건 행 수라 그 뒤 지운 사건도 센다(화면에 보인 구성). 사건을 지금 사건으로 푸는 일은 부르는 쪽, 사건마다 가장
	 * 늦은 노출을 고르는 일은 {@link net.yozmnews.digest.domain.interest.InterestWindow} 가 한다.
	 * @return 섹션 사건 행마다 한 행 — 섹션에 쓴 사건 ID 그대로, 끝낸 시각 · 다이제스트 · 사건 순
	 */
	List<ExposureRow> exposures(UserId user, Instant from, Instant to);

}
