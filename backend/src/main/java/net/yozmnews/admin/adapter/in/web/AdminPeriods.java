package net.yozmnews.admin.adapter.in.web;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import net.yozmnews.evaluation.JudgementPeriod;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.tag.EvaluationPeriod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API 의 기간 매개변수 — 시작일 · 종료일({@code yyyy-MM-dd})과 시간대(IANA ID)를 읽어 {@link EvaluationPeriod#ofDays}
 * (평가 현황 · 보도량 · 검수 · 기준 미리보기), {@link JudgementPeriod#ofDays}(판정 기록) 또는 {@link EventPeriod#ofDays}(사건 ·
 * 스토리라인 · 큰 스토리라인)에 넘긴다. 형식 · 없는 시간대 · 범위 밖은 400 이다 — 범위는 공개 타입이 판정하고, 설명의 상한은 그 타입의 {@code MAX_DAYS}
 * 에서 읽는다(admin-R3).
 */
final class AdminPeriods {

	/** 기간 매개변수의 400 을 화면용 API 명세에 적는 설명 — 기간을 받는 운영자 엔드포인트가 함께 쓴다 */
	static final String PROBLEM = "날짜 · 시간대의 형식이 틀렸거나 기간이 범위 밖이다";

	private AdminPeriods() {
	}

	static EvaluationPeriod of(String from, String to, String zone) {
		return EvaluationPeriod.ofDays(date(from), date(to), zone(zone))
			.orElseThrow(() -> badRequest("기간은 1일에서 " + EvaluationPeriod.MAX_DAYS + "일까지다"));
	}

	/** 판정 원본 조회의 기간 — 범위는 {@link JudgementPeriod#ofDays} 가 판정하고 설명의 상한은 {@link JudgementPeriod#MAX_DAYS} 에서 읽는다 */
	static JudgementPeriod judgements(String from, String to, String zone) {
		return JudgementPeriod.ofDays(date(from), date(to), zone(zone))
			.orElseThrow(() -> badRequest("기간은 1일에서 " + JudgementPeriod.MAX_DAYS + "일까지다"));
	}

	/** 사건 · 스토리라인 조회의 기간 — 범위는 {@link EventPeriod#ofDays} 가 판정하고 설명의 상한은 {@link EventPeriod#MAX_DAYS} 에서 읽는다 */
	static EventPeriod events(String from, String to, String zone) {
		return EventPeriod.ofDays(date(from), date(to), zone(zone))
			.orElseThrow(() -> badRequest("기간은 1일에서 " + EventPeriod.MAX_DAYS + "일까지다"));
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
