package net.yozmnews.user.domain.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;

import net.yozmnews.user.UserId;
import org.junit.jupiter.api.Test;

/**
 * 자격의 강제 변경 — 본인이 바꾸면 풀리고, 초기 비밀번호를 다시 정해도 그대로이며, 본인이 바꾼 뒤에는 다시 정하지 않는다.
 * <p>
 * 지키는 규칙: user-R29
 */
class PasswordCredentialTests {

	private static final Instant T0 = Instant.parse("2026-11-13T09:00:00Z");

	private static final Instant T1 = Instant.parse("2026-11-13T10:00:00Z");

	@Test
	void changingThePasswordLiftsTheForcedChange() {
		PasswordCredential initial = PasswordCredential.of(new UserId(7), "$argon2id$initial", true, T0);

		PasswordCredential changed = initial.changed("$argon2id$mine", T1);

		assertThat(changed.mustChange()).isFalse();
		assertThat(changed.passwordHash()).isEqualTo("$argon2id$mine");
		assertThat(changed.passwordChangedAt()).isEqualTo(T1);
		assertThat(changed.userId()).isEqualTo(new UserId(7));
	}

	@Test
	void resettingTheInitialPasswordKeepsTheForcedChange() {
		PasswordCredential initial = PasswordCredential.of(new UserId(7), "$argon2id$initial", true, T0);

		PasswordCredential reset = initial.resetInitial("$argon2id$again", T1);

		assertThat(reset.mustChange()).isTrue();
		assertThat(reset.passwordHash()).isEqualTo("$argon2id$again");
	}

	@Test
	void initialPasswordIsNotResetOnceTheOwnerChangedIt() {
		PasswordCredential owned = PasswordCredential.of(new UserId(7), "$argon2id$mine", false, T0);

		assertThatIllegalStateException().isThrownBy(() -> owned.resetInitial("$argon2id$again", T1));
	}

	@Test
	void textFormHidesTheHash() {
		assertThat(PasswordCredential.of(new UserId(7), "$argon2id$secret", true, T0).toString())
			.doesNotContain("secret");
	}

}
