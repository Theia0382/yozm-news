package net.yozmnews.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.Objects;

import jakarta.servlet.http.Cookie;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 격리 시험의 사용자 — 계정을 저장소로 만들고 실제 로그인 API 로 세션을 만든다. 각 사용자는 세션 쿠키 · CSRF 를 요청에 싣는 후처리기다. 사용자
 * A 로 B 의 데이터에 닿는지 보는 시험이 쓴다(docs/qa/test-strategy.md §13).
 */
public final class SignedInUsers {

	private static final String PASSWORD = "isolation test password";

	private static final String CSRF = "signed-in-users";

	private static final Instant CREATED = Instant.parse("2026-11-13T09:00:00Z");

	private final MockMvc mockMvc;

	private final AccountRepository accounts;

	private final PasswordCredentialRepository credentials;

	private final PasswordHashing hashing;

	public SignedInUsers(MockMvc mockMvc, AccountRepository accounts, PasswordCredentialRepository credentials,
			PasswordHashing hashing) {
		this.mockMvc = Objects.requireNonNull(mockMvc, "mockMvc");
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
	}

	/** 이 이메일의 계정을 만들고 로그인한 사용자 */
	public SignedInUser signIn(String email) throws Exception {
		UserId id = this.accounts
			.save(Account.create(EmailAddress.parse(email).orElseThrow(), email, AccountRole.USER, CREATED))
			.id();
		this.credentials.save(PasswordCredential.of(id, this.hashing.hash(PASSWORD), CREATED));
		MockHttpServletResponse response = this.mockMvc
			.perform(post("/api/session").cookie(new Cookie("XSRF-TOKEN", CSRF))
				.header("X-XSRF-TOKEN", CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","keep":false}""".formatted(email, PASSWORD)))
			.andReturn()
			.getResponse();
		return new SignedInUser(id, cookieValue(response, "SESSION"), cookieValue(response, "XSRF-TOKEN"));
	}

	private static String cookieValue(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie != null && !cookie.getValue().isEmpty()) {
			return cookie.getValue();
		}
		String header = response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(value -> value.startsWith(name + "=") && !value.startsWith(name + "=;"))
			.reduce((a, b) -> b)
			.orElseThrow(() -> new IllegalStateException("로그인 응답에 " + name + " 쿠키가 없다"));
		String pair = header.split(";", 2)[0];
		return pair.substring(pair.indexOf('=') + 1);
	}

	/**
	 * 로그인한 사용자 — 요청에 세션 쿠키 · CSRF 쿠키와 헤더를 싣는다.
	 *
	 * @param id 사용자 ID
	 * @param session 세션 쿠키 값
	 * @param csrf CSRF 토큰
	 */
	public record SignedInUser(UserId id, String session, String csrf) implements RequestPostProcessor {

		@Override
		public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
			request.setCookies(new Cookie("SESSION", this.session), new Cookie("XSRF-TOKEN", this.csrf));
			request.addHeader("X-XSRF-TOKEN", this.csrf);
			return request;
		}

	}

}
