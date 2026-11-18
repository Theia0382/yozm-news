package net.yozmnews.digest.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.domain.interest.InterestLevels;
import net.yozmnews.digest.domain.interest.InterestWindow;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetId;
import org.springframework.stereotype.Service;

/**
 * 지금의 관심 — 기준 시각과 창 끝을 모두 부르는 시각으로 둔 관심 x 를 낸다. 재료는 {@link InterestMaterials} 가 창
 * [{@link InterestWindow#start}(지금), 지금)에서 한 번 읽고, 계산은 {@link InterestWindow#levels} 가 한다. 구독 제안의 조회와 거절 때 관심이
 * 함께 쓰는 정본이다. docs/specs/digest.md §4
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다. 사용자를 인자로 받는다 — 화면 경로에서는 세션 사용자로만 부른다.
 */
@Service
class CurrentInterests {

	private final InterestMaterials materials;

	CurrentInterests(InterestMaterials materials) {
		this.materials = Objects.requireNonNull(materials, "materials");
	}

	/**
	 * @param version 맞출 태그 체계 활성 버전
	 * @param now 기준 시각이자 창 끝
	 * @return 지금의 관심. 재료의 키를 푼 활성 버전이 {@code version} 과 다르면 빈 값 — 부르는 쪽이 처음부터 다시 한다
	 */
	Optional<InterestLevels> levels(UserId user, TagSetId version, Instant now) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(now, "now");
		return this.materials.read(user, version, InterestWindow.start(now), now)
			.map(record -> InterestWindow.levels(record, now, now));
	}

}
