package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import net.yozmnews.account.AccountCreated;
import net.yozmnews.account.PasswordChanged;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 계정의 사실 이벤트(계정 생김 · 비밀번호 바뀜)를 받은 리스너가 끝나기를 기다리는 시험 도우미 — 끝낸 발행 행은 지워지므로(완료 방식 delete) 그
 * 이벤트 종류의 발행 행이 없어질 때까지 기다린다. 계정을 만들거나 비밀번호를 바꾼 뒤 그 결과(설정 행 · 알림)를 읽거나 계정을 지우는 시험이 쓴다
 * — 리스너가 시험의 정리보다 늦게 돌지 않게 한다.
 */
public final class AccountEvents {

	private AccountEvents() {
	}

	/** 남은 계정 생김 발행이 없어질 때까지(10초) 기다린다 */
	public static void awaitCreatedHandled(JdbcClient jdbc) {
		awaitHandled(jdbc, AccountCreated.class);
	}

	/** 남은 비밀번호 바뀜 발행이 없어질 때까지(10초) 기다린다 */
	public static void awaitPasswordChangeHandled(JdbcClient jdbc) {
		awaitHandled(jdbc, PasswordChanged.class);
	}

	private static void awaitHandled(JdbcClient jdbc, Class<?> eventType) {
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(jdbc.sql("select count(*) from event_publication where event_type = :type")
				.param("type", eventType.getName())
				.query(Long.class)
				.single()).isZero());
	}

}
