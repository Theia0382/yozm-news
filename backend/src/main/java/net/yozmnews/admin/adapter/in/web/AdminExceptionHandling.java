package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.UnknownFeedException;
import net.yozmnews.source.UnknownSourceException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 운영자 API 의 없는 소스 · 피드를 404 문제 응답으로 낸다 — docs/specs/admin.md §5.
 * <p>
 * 운영자 컨트롤러에만 적용하고, 앱 전체 조언의 처리하지 못한 예외(500)보다 먼저 받는다.
 */
@WebRole
@RestControllerAdvice(basePackageClasses = AdminExceptionHandling.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AdminExceptionHandling {

	private final ProblemResponses problems;

	AdminExceptionHandling(ProblemResponses problems) {
		this.problems = Objects.requireNonNull(problems, "problems");
	}

	@ExceptionHandler(UnknownSourceException.class)
	ResponseEntity<ApiProblem> unknownSource(UnknownSourceException ex, HttpServletRequest request) {
		return this.problems.entity(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(UnknownFeedException.class)
	ResponseEntity<ApiProblem> unknownFeed(UnknownFeedException ex, HttpServletRequest request) {
		return this.problems.entity(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

}
