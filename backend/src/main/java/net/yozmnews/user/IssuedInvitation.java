package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;

/**
 * 초대 발급의 결과 — 발급 · 메모가 너무 김. docs/specs/user.md §3
 */
public sealed interface IssuedInvitation {

	/**
	 * 발급했다.
	 * @param token 링크의 조각에 싣는 토큰 — 여기서 한 번만 나간다
	 */
	record Issued(long id, String token, Instant expiresAt) implements IssuedInvitation {

		public Issued {
			Objects.requireNonNull(token, "token");
			Objects.requireNonNull(expiresAt, "expiresAt");
		}

		@Override
		public String toString() {
			return "Issued[id=" + this.id + ", token=숨김, expiresAt=" + this.expiresAt + "]";
		}

	}

	/**
	 * 메모가 상한보다 길어 발급하지 않았다.
	 * @param max 메모의 최대 길이(코드 포인트)
	 */
	record MemoTooLong(int max) implements IssuedInvitation {

	}

}
