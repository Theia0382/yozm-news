package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 계정 생김 이벤트를 받은 리스너가 끝나기를 기다리는 시험 도우미 — 끝낸 발행 행은 지워지므로(완료 방식 delete) 그 이벤트 종류의 발행 행이 없어질
 * 때까지 기다린다. 계정을 만든 뒤 설정 행을 읽거나 계정을 지우는 시험이 쓴다 — 리스너가 시험의 정리보다 늦게 돌지 않게 한다.
 */
public final class AccountCreatedEvents {

	private AccountCreatedEvents() {
	}

	/** 남은 계정 생김 발행이 없어질 때까지(10초) 기다린다 */
	public static void awaitHandled(JdbcClient jdbc) {
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(jdbc.sql("select count(*) from event_publication where event_type = :type")
				.param("type", AccountCreated.class.getName())
				.query(Long.class)
				.single()).isZero());
	}

}
