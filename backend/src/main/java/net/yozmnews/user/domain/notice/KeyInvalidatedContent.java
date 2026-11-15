package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.llm.Provider;

/**
 * 키 무효 알림의 내용 — 무효가 된 키의 공급자 · 키 ID · 무효 시각. 모으지 않는다(전이마다 알림 하나). docs/specs/user.md §4 · user-R36
 *
 * @param provider 그 키의 공급자
 * @param keyId 무효가 된 키의 ID — 무효 시각과 함께 전이 하나를 가리킨다. 화면에는 내지 않는다
 * @param invalidatedAt 무효로 바뀐 시각
 */
public record KeyInvalidatedContent(Provider provider, long keyId, Instant invalidatedAt) implements NoticeContent {

	public KeyInvalidatedContent {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(invalidatedAt, "invalidatedAt");
	}

}
