package net.yozmnews.user.domain.bootstrap;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import org.jspecify.annotations.Nullable;

/**
 * 첫 운영자 설정 값 — 이메일 · 초기 비밀번호 두 변수의 읽기. 빈 값은 없는 것이다. docs/specs/user.md §4.
 * <p>
 * 이유 문구에는 변수 이름만 싣고 값은 싣지 않는다. 문자열 표현에 비밀번호를 싣지 않는다.
 */
public sealed interface FirstAdminValues {

	/** 이메일 변수 — 설정 키와 배포 환경 변수 */
	String EMAIL_VARIABLE = "yozmnews.first-admin.email(YOZMNEWS_FIRST_ADMIN_EMAIL)";

	/** 초기 비밀번호 변수 — 설정 키와 배포 환경 변수 */
	String PASSWORD_VARIABLE = "yozmnews.first-admin.password(YOZMNEWS_FIRST_ADMIN_PASSWORD)";

	/** 두 변수를 읽는다 — 둘 다 없음 · 둘 다 있고 맞음 · 하나만 · 규칙 위반 */
	static FirstAdminValues of(@Nullable String email, @Nullable String password) {
		boolean hasEmail = email != null && !email.isBlank();
		boolean hasPassword = password != null && !password.isEmpty();
		if (!hasEmail && !hasPassword) {
			return new None();
		}
		if (!hasEmail) {
			return new Incomplete(EMAIL_VARIABLE);
		}
		if (!hasPassword) {
			return new Incomplete(PASSWORD_VARIABLE);
		}
		Optional<EmailAddress> address = EmailAddress.parse(email);
		if (address.isEmpty()) {
			return new Invalid(EMAIL_VARIABLE + " 이 이메일이 아니다");
		}
		String normalized = PasswordPolicy.normalize(password);
		if (PasswordPolicy.check(normalized).isPresent()) {
			return new Invalid(PASSWORD_VARIABLE + " 이 비밀번호 규칙(" + PasswordPolicy.MIN_CODE_POINTS + " ~ "
					+ PasswordPolicy.MAX_CODE_POINTS + " 코드 포인트)을 어긴다");
		}
		return new Given(address.get(), normalized);
	}

	/** 두 변수가 모두 없다 */
	record None() implements FirstAdminValues {

	}

	/**
	 * 두 변수가 모두 있고 규칙을 지킨다.
	 * @param normalizedPassword 정규화한 초기 비밀번호
	 */
	record Given(EmailAddress email, String normalizedPassword) implements FirstAdminValues {

		public Given {
			Objects.requireNonNull(email, "email");
			Objects.requireNonNull(normalizedPassword, "normalizedPassword");
		}

		@Override
		public String toString() {
			return "Given[email=" + this.email.value() + ", password=숨김]";
		}

	}

	/**
	 * 하나만 있다.
	 * @param missingVariable 빠진 변수
	 */
	record Incomplete(String missingVariable) implements FirstAdminValues {

		public Incomplete {
			Objects.requireNonNull(missingVariable, "missingVariable");
		}

	}

	/**
	 * 둘 다 있지만 규칙을 어긴다.
	 * @param reason 이유 — 변수 이름만 싣는다
	 */
	record Invalid(String reason) implements FirstAdminValues {

		public Invalid {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
