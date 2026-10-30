package net.yozmnews.source.domain.fetch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 수집 요청의 {@code User-Agent} — docs/specs/source.md §4 · source-R29
 */
public record UserAgent(String contact) {

	public static final String PRODUCT = "YozmNews";

	public static final String PROJECT_URL = "https://github.com/Theia0382/yozm-news";

	public static final int MAX_CONTACT_LENGTH = 200;

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	/** 연락처에 받지 않는 문자 — 헤더 주석({@code (...)}) 안에 넣으므로 주석을 깨는 문자와 구분자다 */
	private static final String FORBIDDEN = "()\\;";

	/**
	 * 연락처 형식을 확인한다 — source-R29.
	 * @throws IllegalArgumentException 연락처가 형식에 맞지 않을 때
	 */
	public UserAgent {
		Objects.requireNonNull(contact, "contact");
		if (contact.isEmpty() || contact.length() > MAX_CONTACT_LENGTH) {
			throw new IllegalArgumentException("연락처는 1~" + MAX_CONTACT_LENGTH + "자다: " + contact.length() + "자");
		}
		if (!contact.chars().allMatch(c -> c >= 0x21 && c <= 0x7E && FORBIDDEN.indexOf(c) < 0)) {
			throw new IllegalArgumentException("연락처에 쓸 수 없는 문자가 있다(공백 · 제어 문자 · 비ASCII · 괄호 · 역슬래시 · 세미콜론)");
		}
		if (!EMAIL.matcher(contact).matches() && !isWebUrl(contact)) {
			throw new IllegalArgumentException("연락처는 이메일 주소나 http · https URL 이어야 한다: " + contact);
		}
	}

	/** 헤더 값을 만든다 — source-R29 */
	public String headerValue() {
		return PRODUCT + " (+" + PROJECT_URL + "; " + this.contact + ")";
	}

	private static boolean isWebUrl(String text) {
		URI uri;
		try {
			uri = new URI(text);
		}
		catch (URISyntaxException ex) {
			return false;
		}
		String scheme = uri.getScheme();
		if (!uri.isAbsolute() || scheme == null || uri.getHost() == null) {
			return false;
		}
		String lower = scheme.toLowerCase(Locale.ROOT);
		return lower.equals("http") || lower.equals("https");
	}

}
