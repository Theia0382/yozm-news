package net.yozmnews.platform.adapter.in.web;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.platform.WebRole;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 컨트롤러 오류를 {@link ApiProblem} 으로 낸다 — docs/specs/platform.md §4 · platform-R12.
 * <p>
 * 프레임워크 예외(형식 오류 · 없는 경로 · {@code ResponseStatusException} 등)는 상위 클래스가 받고 {@link #createResponseEntity} 가
 * 본문을 바꾼다. 다른 모듈의 조언이 먼저 받도록 우선순위를 가장 낮게 둔다.
 */
@WebRole
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ProblemResponseAdvice extends ResponseEntityExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(ProblemResponseAdvice.class);

	private final ProblemResponses problems;

	ProblemResponseAdvice(ProblemResponses problems) {
		this.problems = Objects.requireNonNull(problems, "problems");
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ApiProblem> concurrentChange(HttpServletRequest request) {
		return this.problems.entity(HttpStatus.CONFLICT, "다른 요청이 먼저 바꿨다 — 다시 읽고 시도한다", request);
	}

	/** 처리하지 못한 예외 — 본문에 예외 메시지를 싣지 않는다 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiProblem> unexpected(Exception ex, HttpServletRequest request) {
		logger.error("요청을 처리하지 못했다: {}", request.getRequestURI(), ex);
		return this.problems.entity(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 요청을 처리하지 못했다", request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
		String title = ProblemResponses.standardTitle(statusCode);
		String detail = title;
		if (body instanceof ProblemDetail problem) {
			if (problem.getTitle() != null) {
				title = problem.getTitle();
			}
			detail = (problem.getDetail() != null) ? problem.getDetail() : title;
		}
		ApiProblem apiProblem = this.problems.problem(ProblemResponses.BLANK, title, statusCode, detail,
				servletRequest);
		ResponseEntity<ApiProblem> entity = this.problems.entity(apiProblem);
		HttpHeaders merged = new HttpHeaders();
		merged.addAll(headers);
		entity.getHeaders().forEach(merged::put);
		return new ResponseEntity<>(apiProblem, merged, statusCode);
	}

}
