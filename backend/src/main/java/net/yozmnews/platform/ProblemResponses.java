package net.yozmnews.platform;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 오류 응답({@link ApiProblem})을 만드는 정본 — docs/specs/platform.md §3. 보안 진입점과 컨트롤러 조언이 같이 쓴다.
 * <p>
 * 본문은 JSON(UTF-8)이라 문자 집합 매개변수를 붙이지 않는다. 모듈 공개 빈이라 관측 프록시가 감쌀 수 있어 {@code final} 이 아니다.
 */
@Component
public class ProblemResponses {

	/** 따로 정하지 않은 문제의 유형 */
	public static final URI BLANK = URI.create("about:blank");

	/** 문제 응답을 저장하지 않게 하는 {@code Cache-Control} 값 — 컨트롤러 응답과 보안 필터 응답이 같이 쓴다 */
	private static final String NO_STORE = "no-store";

	private final JsonMapper jsonMapper;

	public ProblemResponses(JsonMapper jsonMapper) {
		this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
	}

	/** 유형을 따로 정하지 않은 문제 응답 — 제목은 상태 코드의 표준 문구 */
	public ResponseEntity<ApiProblem> entity(HttpStatusCode status, String detail, HttpServletRequest request) {
		return entity(problem(BLANK, standardTitle(status), status, detail, request));
	}

	/** 문제 응답 — {@code application/problem+json}, 저장하지 않게 한다 */
	public ResponseEntity<ApiProblem> entity(ApiProblem problem) {
		return ResponseEntity.status(problem.status())
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.header(HttpHeaders.CACHE_CONTROL, NO_STORE)
			.body(problem);
	}

	/** 문제를 만든다 — 사례는 요청 경로(질의 문자열 제외) */
	public ApiProblem problem(URI type, String title, HttpStatusCode status, String detail,
			HttpServletRequest request) {
		return new ApiProblem(type, title, status.value(), detail, request.getRequestURI());
	}

	/** 컨트롤러 밖(보안 필터)에서 문제 응답을 쓴다 */
	public void write(HttpServletResponse response, ApiProblem problem) throws IOException {
		response.setStatus(problem.status());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
		this.jsonMapper.writeValue(response.getOutputStream(), problem);
	}

	/** 상태 코드의 표준 문구 */
	public static String standardTitle(HttpStatusCode status) {
		HttpStatus known = HttpStatus.resolve(status.value());
		return (known != null) ? known.getReasonPhrase() : "HTTP " + status.value();
	}

}
