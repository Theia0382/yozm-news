package net.yozmnews;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 사용자에게 LLM 키 행을 주는 도우미 — 키 관문(user-R5)이 막지 않게, 키 없이도 여는 표시가 없는 처리기를 부르는 시험이 쓴다. 암호문은
 * 열 수 없는 자리 값이다 — 키 유무만 보는 시험용이다.
 */
public final class LlmKeyRows {

	private LlmKeyRows() {
	}

	/** 그 사용자에게 키 행 하나를 넣는다 */
	public static void give(JdbcClient jdbc, long userId) {
		jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:userId, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', 'VALID', now(), now())
				""").param("userId", userId).update();
	}

}
