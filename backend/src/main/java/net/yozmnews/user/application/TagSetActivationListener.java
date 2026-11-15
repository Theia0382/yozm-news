package net.yozmnews.user.application;

import net.yozmnews.tag.TagSetActivated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * {@link TagSetActivated} 리스너 — 선호 버전이 지금 활성 버전과 다른 사용자를 따라가게 한다. docs/specs/user.md §4 · user-R47
 * <p>
 * 이벤트의 두 버전 ID 는 쓰지 않는다 — 전환이 겹치거나 다시 처리돼도 지금 활성 버전으로 수렴한다. 트랜잭션 없이 받는다({@code NOT_SUPPORTED})
 * — 사용자마다 트랜잭션 하나로 커밋해야 한 사용자의 실패가 앞 사용자를 되돌리지 않는다. 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 활성화는
 * 화면 역할의 운영자 요청이 낸다.
 */
@Component
class TagSetActivationListener {

	private final PreferenceFollowups followups;

	TagSetActivationListener(PreferenceFollowups followups) {
		this.followups = followups;
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	public void on(TagSetActivated activated) {
		this.followups.followAll();
	}

}
