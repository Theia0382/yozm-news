package net.yozmnews.platform;

import org.jspecify.annotations.Nullable;

/**
 * YAML 원문 안의 위치 — docs/specs/platform.md §3. 줄과 열은 1부터 센다
 */
public record TextLocation(int line, int column) {

	/** 위치를 알 수 없을 때 */
	public static final TextLocation UNKNOWN = new TextLocation(0, 0);

	/** 공개 보기의 줄 — 모르는 위치면 null */
	public @Nullable Integer knownLine() {
		return known() ? this.line : null;
	}

	/** 공개 보기의 열 — 모르는 위치면 null */
	public @Nullable Integer knownColumn() {
		return known() ? this.column : null;
	}

	private boolean known() {
		return !equals(UNKNOWN);
	}

	@Override
	public String toString() {
		return (this.line == 0) ? "?" : this.line + ":" + this.column;
	}

}
