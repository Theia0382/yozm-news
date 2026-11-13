package net.yozmnews.user.domain.bootstrap;

import java.util.Objects;

import net.yozmnews.user.domain.account.EmailAddress;

/**
 * 기동 때 첫 운영자를 어떻게 할지 — 판정의 정본. docs/specs/user.md §4.
 * <p>
 * 운영자(쓰는 {@code ADMIN} 계정)가 있으면 값은 쓰이지 않는다 — 무엇이 남았든(하나만 · 규칙 위반 포함) 남아 있다고 경고만 한다. 운영자가 없으면
 * 값이 없을 때 경고하고 운영자 없이 기동하며, 하나만 있거나 규칙을 어기거나 이미 있는 계정의 이메일이면 이유를 말하고 기동하지 않고, 그 밖이면
 * 그 값으로 첫 운영자를 만든다.
 */
public sealed interface FirstAdminDecision {

	/**
	 * 판정한다.
	 * @param adminExists 쓰는 운영자 계정이 있는가
	 * @param emailTaken 값의 이메일이 이미 있는 계정의 이메일인가 — 값이 없거나 맞지 않으면 쓰이지 않는다
	 */
	static FirstAdminDecision decide(FirstAdminValues values, boolean adminExists, boolean emailTaken) {
		if (adminExists) {
			return switch (values) {
				case FirstAdminValues.None none -> new Nothing();
				case FirstAdminValues.Incomplete incomplete -> new WarnLeftover();
				case FirstAdminValues.Invalid invalid -> new WarnLeftover();
				case FirstAdminValues.Given given -> new WarnLeftover();
			};
		}
		return switch (values) {
			case FirstAdminValues.None none -> new WarnNoAdmin();
			case FirstAdminValues.Incomplete incomplete -> new Fail(incomplete.missingVariable()
					+ " 이 없다 — 첫 운영자의 이메일 · 초기 비밀번호는 함께 준다");
			case FirstAdminValues.Invalid invalid -> new Fail(invalid.reason());
			case FirstAdminValues.Given given -> emailTaken ? emailTaken()
					: new Create(given.email(), given.normalizedPassword());
		};
	}

	/** 값의 이메일이 이미 있는 계정의 이메일이라 기동하지 않는다 — 판정 때 본 것이든, 만들다 경합으로 부딪힌 것이든 같은 이유다 */
	static Fail emailTaken() {
		return new Fail(FirstAdminValues.EMAIL_VARIABLE + " 이 이미 있는 계정의 이메일이다 — 다른 이메일을 준다");
	}

	/** 운영자가 있고 값도 없다 — 할 일이 없다 */
	record Nothing() implements FirstAdminDecision {

	}

	/** 이 값으로 첫 운영자를 만든다 */
	record Create(EmailAddress email, String normalizedPassword) implements FirstAdminDecision {

		public Create {
			Objects.requireNonNull(email, "email");
			Objects.requireNonNull(normalizedPassword, "normalizedPassword");
		}

		@Override
		public String toString() {
			return "Create[email=" + this.email.value() + ", password=숨김]";
		}

	}

	/** 운영자가 있는데 값이 남아 있다 — 경고만 한다 */
	record WarnLeftover() implements FirstAdminDecision {

	}

	/** 운영자도 값도 없다 — 경고하고 운영자 없이 기동한다 */
	record WarnNoAdmin() implements FirstAdminDecision {

	}

	/**
	 * 기동하지 않는다.
	 * @param reason 이유 — 변수 이름만 싣는다
	 */
	record Fail(String reason) implements FirstAdminDecision {

		public Fail {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
