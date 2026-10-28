package net.yozmnews.source.domain.fetch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 수집 요청의 {@code User-Agent} — 서비스 이름 · 프로젝트 주소 · 운영자 연락처를 밝힌다. 연락처는 셀프 호스팅마다 다른
 * 운영자 설정값이다.
 *
 * @param contact 운영자 연락처 — 이메일 주소나 http · https URL
 */
public record UserAgent(String contact) {

	public static final String PRODUCT = "YozmNews";

	public static final String PROJECT_URL = "https://github.com/Theia0382/yozm-news";

	public static final int MAX_CONTACT_LENGTH = 200;

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	/** 헤더 주석({@code (...)}) 안에 넣으므로 주석을 깨는 문자와 구분자를 받지 않는다 */
	private static final String FORBIDDEN = "()\\;";

	/**
	 * @throws IllegalArgumentException 연락처가 비었거나, {@link #MAX_CONTACT_LENGTH} 를 넘거나, 0x21~0x7E 밖의 문자 · 괄호 ·
	 * 역슬래시 · 세미콜론이 있거나, 이메일 주소도 http · https URL 도 아닐 때
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

	/** {@code YozmNews (+https://github.com/Theia0382/yozm-news; {contact})} */
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
