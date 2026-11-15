package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.llm.Provider;
import net.yozmnews.user.domain.notice.KeyInvalidatedContent;

/**
 * 키 무효 알림의 본문 — 공급자와 무효 시각. 키 ID 는 내지 않는다. docs/specs/user.md §3
 *
 * @param provider 무효가 된 키의 공급자
 * @param invalidatedAt 무효로 바뀐 시각
 */
record KeyInvalidatedNoticeResponse(Provider provider, Instant invalidatedAt) {

	static KeyInvalidatedNoticeResponse of(KeyInvalidatedContent content) {
		return new KeyInvalidatedNoticeResponse(content.provider(), content.invalidatedAt());
	}

}
