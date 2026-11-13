package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.user.AccountAdministration;
import net.yozmnews.user.PasswordProblem;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 계정 운영의 요청 문제 — 형식 · 규칙 위반 400 과 없는 계정 404. 길이는 공개 API {@link AccountAdministration} 의 상수에서 읽는다
 * (admin-R2 · admin-R3).
 */
final class AccountProblem {

	private AccountProblem() {
	}

	static ResponseStatusException emailMalformed() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일 형식이 아니다");
	}

	static ResponseStatusException displayNameMalformed() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "표시 이름은 앞뒤 공백을 빼고 1 ~ "
				+ AccountAdministration.DISPLAY_NAME_MAX_CODE_POINTS + "자다");
	}

	static ResponseStatusException passwordRejected(PasswordProblem problem) {
		String reason = switch (problem) {
			case TOO_SHORT -> "짧다";
			case TOO_LONG -> "길다";
		};
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "초기 비밀번호가 " + reason + " — "
				+ AccountAdministration.PASSWORD_MIN_CODE_POINTS + " ~ " + AccountAdministration.PASSWORD_MAX_CODE_POINTS
				+ "자다");
	}

	static ResponseStatusException noAccount() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "그 이메일의 계정이 없다");
	}

}
