package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.evaluation.TagSetAdministration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 범위 밖 재평가 기간의 400 — 상한은 {@link TagSetAdministration#MAX_REEVALUATION_DAYS} 하나에서 읽는다. 범위는 공개 API 가
 * 판정한다(admin-R3).
 */
final class ReevaluationDays {

	private ReevaluationDays() {
	}

	static ResponseStatusException outOfRange() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"재평가 기간은 0일에서 " + TagSetAdministration.MAX_REEVALUATION_DAYS + "일까지다");
	}

}
