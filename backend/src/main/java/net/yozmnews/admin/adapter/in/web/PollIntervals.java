package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.source.SourceAdministration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 하한보다 짧은 운영자 수집 주기의 400 — 하한 값은 {@link SourceAdministration#MINIMUM_POLL_INTERVAL_MINUTES} 하나에서 읽는다.
 */
final class PollIntervals {

	private PollIntervals() {
	}

	static ResponseStatusException belowMinimum() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"수집 주기는 " + SourceAdministration.MINIMUM_POLL_INTERVAL_MINUTES + "분 이상이다");
	}

}
