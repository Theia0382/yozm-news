package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 역할이 하나 이상이어야 한다는 판정 — 스프링 없이 값을 직접 만든다.
 * <p>
 * 지키는 규칙: platform-R8
 */
class ActiveRolesTests {

	@ParameterizedTest
	@CsvSource({ "true, false", "false, true", "true, true" })
	void acceptsAtLeastOneRole(boolean web, boolean worker) {
		ActiveRoles roles = new ActiveRoles(web, worker);

		assertThat(roles.web()).isEqualTo(web);
		assertThat(roles.worker()).isEqualTo(worker);
	}

	@Test
	void rejectsNoRoleAndTellsHowToTurnOneOn() {
		assertThatIllegalStateException().isThrownBy(() -> new ActiveRoles(false, false))
			.withMessageContaining("web · worker")
			.withMessageContaining("--spring.profiles.active=local,web,worker");
	}

}
