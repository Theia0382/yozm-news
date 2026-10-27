package net.yozmnews.source.domain.profile.text;

/**
 * 프로필 텍스트 안의 위치. 줄과 열은 1부터 센다.
 */
public record TextLocation(int line, int column) {

	/** 위치를 알 수 없을 때 */
	public static final TextLocation UNKNOWN = new TextLocation(0, 0);

	@Override
	public String toString() {
		return (this.line == 0) ? "?" : this.line + ":" + this.column;
	}

}
