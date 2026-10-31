package net.yozmnews.platform.domain;

import net.yozmnews.platform.WebRole;
import net.yozmnews.platform.WorkerRole;

/**
 * 이 프로세스가 맡는 역할 — 정본 · docs/specs/platform.md §4 · platform-R8
 *
 * @param web 화면 역할을 맡는가
 * @param worker 작업 역할을 맡는가
 */
public record ActiveRoles(boolean web, boolean worker) {

	/**
	 * @throws IllegalStateException 두 역할 모두 맡지 않을 때
	 */
	public ActiveRoles {
		if (!web && !worker) {
			throw new IllegalStateException("역할 profile 이 하나도 켜지지 않았다 — " + WebRole.PROFILE + " · "
					+ WorkerRole.PROFILE + " 가운데 하나 이상을 켠다 (예: --spring.profiles.active=local,"
					+ WebRole.PROFILE + "," + WorkerRole.PROFILE + ")");
		}
	}

}
