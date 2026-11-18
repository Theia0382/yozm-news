package net.yozmnews.digest.adapter.out.persistence;

import java.util.regex.Pattern;

/**
 * 날짜별로 보이는 다이제스트의 조건 — 정본. docs/specs/digest.md §5 · digest-R13
 * <p>
 * 날짜마다 그 날짜의 {@code READY} 다이제스트 가운데 ID 가 가장 큰 것이 보인다. 받은 소식 · 날짜 보기 · 날짜 목록의 질의가 모두 이 조각을 쓴다.
 */
final class ShownDigestSql {

	/** 조각에 넣을 수 있는 별칭 — 질의 안의 {@code digests} 별칭 */
	private static final Pattern ALIAS = Pattern.compile("[a-z]+");

	private ShownDigestSql() {
	}

	/**
	 * 별칭 {@code alias} 의 다이제스트 행과 같은 사용자 · 날짜의 보이는 다이제스트 ID 를 내는 부분 질의 — 그 날짜에 {@code READY} 가 없으면
	 * null 이다.
	 * @param alias 바깥 질의의 {@code digests} 별칭 — 영문 소문자. 부분 질의 안의 별칭 {@code shown} 과 달라야 한다
	 * @throws IllegalArgumentException 별칭이 영문 소문자가 아니거나 {@code shown} 일 때
	 */
	static String shownIdOf(String alias) {
		if (!ALIAS.matcher(alias).matches() || alias.equals("shown")) {
			throw new IllegalArgumentException("별칭은 shown 이 아닌 영문 소문자다: " + alias);
		}
		return "(select max(shown.id) from digests shown where shown.user_id = " + alias + ".user_id"
				+ " and shown.digest_date = " + alias + ".digest_date and shown.status = 'READY')";
	}

}
