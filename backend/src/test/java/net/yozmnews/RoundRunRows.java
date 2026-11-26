package net.yozmnews;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 회차 기록 {@code round_runs} 를 시험이 비우고 읽는 도우미 — 회차가 뭉치를 되풀이한 결과(뭉치 수 · 처리 수 · 끝난 까닭)를 본다.
 */
public final class RoundRunRows {

	private RoundRunRows() {
	}

	/** 회차 기록을 모두 지운다 — 앞선 시험의 회차가 남긴 행을 치운다 */
	public static void clear(JdbcClient jdbc) {
		jdbc.sql("delete from round_runs").update();
	}

	/**
	 * 회차 하나의 기록 — 남긴 순서대로 {@code batches · handled · ended_by} 를 {@code "2 · 64 · DRAINED"} 꼴로 낸다.
	 */
	public static List<String> of(JdbcClient jdbc, String round) {
		return jdbc.sql("select batches, handled, ended_by from round_runs where round = :round order by id")
			.param("round", round)
			.query((rs, row) -> rs.getInt("batches") + " · " + rs.getInt("handled") + " · " + rs.getString("ended_by"))
			.list();
	}

}
