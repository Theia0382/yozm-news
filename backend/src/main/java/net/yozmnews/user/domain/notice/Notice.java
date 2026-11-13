package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 알림 하나 — 읽기 값. docs/specs/user.md §4
 *
 * @param id 알림 ID
 * @param kind 종류 — 내용의 종류와 같다
 * @param content 내용
 * @param revision 판 — 내용을 고칠 때마다 하나 오른다(1 부터)
 * @param createdAt 만든 시각
 * @param updatedAt 내용을 마지막으로 고친 시각 — 고친 적이 없으면 만든 시각
 * @param readAt 읽은 시각 — 안 읽었으면 null
 */
public record Notice(long id, NoticeKind kind, NoticeContent content, int revision, Instant createdAt,
		Instant updatedAt, @Nullable Instant readAt) {

	public Notice {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
		if (content.kind() != kind) {
			throw new IllegalArgumentException("종류와 내용이 다르다: " + kind + " · " + content.kind());
		}
		if (revision < 1) {
			throw new IllegalArgumentException("판은 1 이상이다: " + revision);
		}
	}

	/** 읽었는가 */
	public boolean read() {
		return this.readAt != null;
	}

}
