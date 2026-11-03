package net.yozmnews.admin.adapter.in.web;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import net.yozmnews.evaluation.EvaluationPeriod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API 의 집계 기간 매개변수 — 시작일 · 종료일({@code yyyy-MM-dd})과 시간대(IANA ID)를 읽어 {@link EvaluationPeriod#ofDays} 에
 * 넘긴다. 형식 · 없는 시간대 · 범위 밖은 400 이다 — 범위는 공개 타입이 판정하고, 설명의 상한은 {@link EvaluationPeriod#MAX_DAYS} 에서
 * 읽는다(admin-R3).
 */
final class AdminPeriods {

	private AdminPeriods() {
	}

	static EvaluationPeriod of(String from, String to, String zone) {
		return EvaluationPeriod.ofDays(date(from), date(to), zone(zone))
			.orElseThrow(() -> badRequest("기간은 1일에서 " + EvaluationPeriod.MAX_DAYS + "일까지다"));
	}

	private static LocalDate date(String value) {
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw badRequest("날짜는 yyyy-MM-dd 형식이다: " + value);
		}
	}

	private static ZoneId zone(String value) {
		try {
			return ZoneId.of(value);
		}
		catch (DateTimeException ex) {
			throw badRequest("없는 시간대다: " + value);
		}
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
