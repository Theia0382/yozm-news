package net.yozmnews.source.domain.feed;

/**
 * 사전 보정이 원문을 훑을 때 참조가 해석되지 않는 구간(CDATA · 주석 · 처리 명령)을 건너뛴다.
 */
final class MarkupScanner {

	private static final String[][] VERBATIM = { { "<![CDATA[", "]]>" }, { "<!--", "-->" }, { "<?", "?>" } };

	private MarkupScanner() {
	}

	/**
	 * {@code index} 에서 참조가 해석되지 않는 구간이 시작하면 그 구간 끝(닫는 표지 다음, 닫히지 않으면 문서 끝)의 위치, 아니면 -1
	 */
	static int verbatimEnd(String text, int index) {
		for (String[] span : VERBATIM) {
			if (text.startsWith(span[0], index)) {
				int close = text.indexOf(span[1], index + span[0].length());
				return (close < 0) ? text.length() : close + span[1].length();
			}
		}
		return -1;
	}

}
