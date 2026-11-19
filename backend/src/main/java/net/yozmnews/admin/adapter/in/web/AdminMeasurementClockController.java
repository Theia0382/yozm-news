package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.AdvanceRefusal;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.MeasurementClockAdministration;
import net.yozmnews.platform.MeasurementClockStatus;
import net.yozmnews.platform.WebRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 API — 측정용 시계의 상태 · 앞으로 넘기기. docs/specs/admin.md §3.
 * <p>
 * {@code platform} 의 공개 운영 API 를 부르고 결과를 응답 레코드로 옮긴다. 처리기는 언제나 등록되고, 앱의 시계가 측정용 시계가 아니면 409 로
 * 거부한다. 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class AdminMeasurementClockController {

	/** 측정용 시계가 아닐 때의 문제 detail */
	static final String OFF = "측정용 시계가 켜져 있지 않다 — 이 인스턴스의 시계는 실제 시간이다";

	private final MeasurementClockAdministration administration;

	AdminMeasurementClockController(MeasurementClockAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@GetMapping("/api/admin/measurement-clock")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "409", description = "측정용 시계가 켜져 있지 않다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	MeasurementClockResponse status() {
		return switch (this.administration.status()) {
			case MeasurementClockStatus.On on -> MeasurementClockResponse.of(on);
			case MeasurementClockStatus.Off off -> throw off();
		};
	}

	@PostMapping("/api/admin/measurement-clock/advance")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "본문 형식이 틀렸거나 넘길 길이 · 도착 시각이 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "측정용 시계가 켜져 있지 않다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	MeasurementClockResponse advance(@Valid @RequestBody AdvanceRequest body) {
		return switch (this.administration.advance(body.by(), body.to())) {
			case ClockAdvanceResult.Advanced advanced -> MeasurementClockResponse.of(advanced.status());
			case ClockAdvanceResult.Rejected rejected ->
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail(rejected.reason()));
			case ClockAdvanceResult.Off off -> throw off();
		};
	}

	private static ResponseStatusException off() {
		return new ResponseStatusException(HttpStatus.CONFLICT, OFF);
	}

	private static String detail(AdvanceRefusal reason) {
		return switch (reason) {
			case BOTH_GIVEN -> "넘길 길이(by)와 도착 시각(to) 가운데 하나만 준다";
			case NONE_GIVEN -> "넘길 길이(by)나 도착 시각(to) 가운데 하나를 준다";
			case NOT_POSITIVE -> "넘길 길이는 0 보다 길어야 한다";
			case NOT_AHEAD -> "도착 시각은 지금보다 뒤여야 한다 — 측정용 시계는 앞으로만 넘긴다";
		};
	}

}
