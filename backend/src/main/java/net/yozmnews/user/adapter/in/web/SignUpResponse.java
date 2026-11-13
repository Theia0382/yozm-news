package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.application.SignUpOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 가입(공개 가입 · 초대 가입) 결과 — 판정 결과 200. docs/specs/user.md §3
 *
 * @param result 가입 · 닫힘 · 초대를 쓸 수 없음 · 가입할 수 없는 이메일 · 이메일 형식 · 표시 이름 형식 · 비밀번호 규칙 위반
 * @param recoveryCode 복구 코드(4자씩 {@code -}) — 가입일 때만 한 번, 그 밖 null
 * @param invitation 초대를 쓸 수 없을 때 그 상태, 그 밖 null
 * @param passwordProblem 비밀번호가 어긴 규칙, 그 밖 null
 */
record SignUpResponse(Result result, @Nullable String recoveryCode, @Nullable InvitationStatus invitation,
		@Nullable PasswordProblem passwordProblem) {

	static SignUpResponse of(SignUpOutcome outcome) {
		return switch (outcome) {
			case SignUpOutcome.SignedUp signedUp -> new SignUpResponse(Result.SIGNED_UP,
					signedUp.recoveryCode().display(), null, null);
			case SignUpOutcome.Closed closed -> new SignUpResponse(Result.CLOSED, null, null, null);
			case SignUpOutcome.InvitationUnusable unusable -> new SignUpResponse(Result.INVITATION_UNUSABLE, null,
					InvitationStatus.of(unusable.state()), null);
			case SignUpOutcome.EmailUnavailable unavailable -> new SignUpResponse(Result.EMAIL_UNAVAILABLE, null, null,
					null);
			case SignUpOutcome.EmailMalformed malformed -> new SignUpResponse(Result.EMAIL_MALFORMED, null, null, null);
			case SignUpOutcome.DisplayNameMalformed malformed -> new SignUpResponse(Result.DISPLAY_NAME_MALFORMED, null,
					null, null);
			case SignUpOutcome.PasswordRejected rejected -> new SignUpResponse(Result.PASSWORD_REJECTED, null, null,
					rejected.problem());
		};
	}

	@Override
	public String toString() {
		return "SignUpResponse[result=" + this.result + "]";
	}

	/** 가입 결과의 판별자 */
	enum Result {

		/** 가입했다 — 새 세션으로 로그인했다 */
		SIGNED_UP,

		/** 공개 가입이 닫혔다 */
		CLOSED,

		/** 초대를 쓸 수 없다 */
		INVITATION_UNUSABLE,

		/** 가입할 수 없는 이메일이다 — 이미 있는 계정인지 드러내지 않는 일반 문구로 보인다 */
		EMAIL_UNAVAILABLE,

		/** 이메일로 읽을 수 없다 */
		EMAIL_MALFORMED,

		/** 표시 이름이 비었거나 너무 길다 */
		DISPLAY_NAME_MALFORMED,

		/** 비밀번호가 규칙을 어긴다 */
		PASSWORD_REJECTED

	}

}
