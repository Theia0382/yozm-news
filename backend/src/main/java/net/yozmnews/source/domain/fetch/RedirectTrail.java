package net.yozmnews.source.domain.fetch;

import java.net.URI;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 한 번의 수집에서 따라간 리다이렉트 홉을 센다. 영구 이동은 처음 URL 에서 끊기지 않고 이어진 영구 이동(301 · 308) 홉만
 * 친다 — 그 마지막 대상이 프로필이 앞으로 쓸 주소다. 임시 이동이 한 번이라도 끼면 그 뒤의 영구 이동은 치지 않는다.
 */
public final class RedirectTrail {

	private final int maxRedirects;

	private int followed;

	private boolean permanentSoFar = true;

	private @Nullable URI movedTo;

	/**
	 * @param maxRedirects 따라갈 수 있는 홉 수
	 * @throws IllegalArgumentException 음수일 때
	 */
	public RedirectTrail(int maxRedirects) {
		if (maxRedirects < 0) {
			throw new IllegalArgumentException("리다이렉트 상한은 0 이상이다: " + maxRedirects);
		}
		this.maxRedirects = maxRedirects;
	}

	/**
	 * 홉 하나를 따라간다.
	 * @return 홉 수가 상한을 넘으면 false — 그 홉은 기록하지 않는다
	 */
	public boolean follow(boolean permanent, URI target) {
		Objects.requireNonNull(target, "target");
		if (this.followed == this.maxRedirects) {
			return false;
		}
		this.followed++;
		if (this.permanentSoFar && permanent) {
			this.movedTo = target;
		}
		else {
			this.permanentSoFar = false;
		}
		return true;
	}

	/** 영구 이동한 주소 — 없으면 null */
	public @Nullable URI movedTo() {
		return this.movedTo;
	}

}
