package net.yozmnews.admin.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 운영자 API 시험의 토큰과 요청 */
final class AdminRequests {

	/** 시험 문맥의 운영자 토큰 — 각 시험 클래스가 같은 값을 속성으로 준다 */
	static final String TOKEN = "9c2e7a41d0b84f6e3a5c19d7e2b06f48a1c3e5d7b9f20146";

	static final String TOKEN_PROPERTY = "yozmnews.admin.token=" + TOKEN;

	private AdminRequests() {
	}

	static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
	}

	static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return authorized(request).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
