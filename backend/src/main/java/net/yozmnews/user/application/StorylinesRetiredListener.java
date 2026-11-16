package net.yozmnews.user.application;

import net.yozmnews.storyline.StorylinesRetired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * {@link StorylinesRetired} 리스너 — 합치거나 지운 스토리라인을 구독한 사용자의 구독을 따라가게 한다. docs/specs/user.md §4 · user-R53
 * <p>
 * 트랜잭션 없이 받는다({@code NOT_SUPPORTED}) — 사용자마다 트랜잭션 하나로 커밋해야 한 사용자의 실패가 앞 사용자를 되돌리지 않는다. 역할
 * 애너테이션이 없어 두 역할 모두에서 켜진다 — 정돈은 작업 역할의 잇기 회차가 한다.
 */
@Component
class StorylinesRetiredListener {

	private final SubscriptionFollowups followups;

	StorylinesRetiredListener(SubscriptionFollowups followups) {
		this.followups = followups;
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	public void on(StorylinesRetired retired) {
		this.followups.followRetired(retired);
	}

}
