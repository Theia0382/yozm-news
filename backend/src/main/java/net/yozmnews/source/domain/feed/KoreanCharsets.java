package net.yozmnews.source.domain.feed;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 문자 인코딩 레이블 → 문자셋. 한국어 계열 레이블은 웹 표준(WHATWG Encoding)처럼 windows-949 로 읽는다 — EUC-KR 을
 * 선언하고 그 밖의 한글(CP949 확장)을 쓰는 피드가 흔하고, windows-949 는 EUC-KR 을 모두 담는다.
 */
final class KoreanCharsets {

	private static final Charset WINDOWS_949 = Charset.forName("x-windows-949");

	/**
	 * windows-949 로 읽는 레이블(소문자) — WHATWG 의 EUC-KR 레이블에 cp949 계열을 더했다. Java 의 {@code CP949} 는 다른
	 * 문자셋(IBM949)이라 여기서 가로챈다
	 */
	private static final Set<String> LABELS = Set.of("euc-kr", "cseuckr", "csksc56011987", "iso-ir-149", "korean",
			"ks_c_5601-1987", "ks_c_5601-1989", "ksc5601", "ksc_5601", "windows-949", "cp949", "ms949", "x-windows-949");

	/** windows-949 로 바꿔 읽는 컴파일된 문자셋의 정식 이름 */
	private static final Set<String> NARROW = Set.of("EUC-KR", "x-IBM949");

	private KoreanCharsets() {
	}

	/** 레이블의 문자셋 — 지원하지 않으면 빈 값 */
	static Optional<Charset> forLabel(String label) {
		String key = label.strip();
		if (LABELS.contains(key.toLowerCase(Locale.ROOT))) {
			return Optional.of(WINDOWS_949);
		}
		try {
			if (Charset.isSupported(key)) {
				return Optional.of(effective(Charset.forName(key)));
			}
		}
		catch (IllegalCharsetNameException ex) {
			// 지원하지 않는 이름과 같다
		}
		return Optional.empty();
	}

	/** 프로필이 강제한 문자셋에도 같은 해석을 적용한다 */
	static Charset effective(Charset charset) {
		return NARROW.contains(charset.name()) ? WINDOWS_949 : charset;
	}

}
