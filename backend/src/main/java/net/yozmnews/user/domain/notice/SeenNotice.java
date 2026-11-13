package net.yozmnews.user.domain.notice;

/**
 * 화면이 받은 알림의 판 — 읽음 처리는 판이 그대로인 알림만 읽음으로 만든다. docs/specs/user.md §4 · user-R25
 *
 * @param id 알림 ID — 1 이상
 * @param revision 받은 판 — 1 이상
 */
public record SeenNotice(long id, int revision) {

	public SeenNotice {
		if (id < 1) {
			throw new IllegalArgumentException("알림 ID 는 1 이상이다: " + id);
		}
		if (revision < 1) {
			throw new IllegalArgumentException("판은 1 이상이다: " + revision);
		}
	}

}
