package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.WebRole;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetAdministration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 활성 버전의 태그 기준 · 저장 기준: 상태, 미리보기, 변경. docs/specs/admin.md §3.
 * <p>
 * {@code tag} 의 공개 운영 API 를 불러 옮기기만 한다. 변경은 행위자 {@link ChangeActor#OPERATOR} 로 부른다. 값의 범위는 공개 API 가
 * 판정하고 컨트롤러는 그 결과를 400 으로 옮긴다(admin-R3). 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class AdminThresholdController {

	private final TagSetAdministration administration;

	AdminThresholdController(TagSetAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@GetMapping("/api/admin/thresholds")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	ThresholdStatusResponse thresholdStatus() {
		return ThresholdStatusResponse.of(this.administration.thresholdStatus());
	}

	@PostMapping("/api/admin/threshold-previews")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400",
			description = "요청 본문의 형식이 틀렸거나, 날짜 · 시간대 · 기간 · 태그 기준 · 저장 기준이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ThresholdPreviewResponse previewThresholds(@Valid @RequestBody ThresholdPreviewRequest body) {
		return ThresholdPreviewResponse.of(this.administration.previewThresholds(body.kind(), body.tagThreshold(),
				body.storeThreshold(), AdminPeriods.of(body.from(), body.to(), body.zone())));
	}

	@PutMapping("/api/admin/thresholds/{kind}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문 · 경로의 형식이 틀렸거나 태그 기준 · 저장 기준이 범위 밖이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	ThresholdChangeResponse changeThresholds(@PathVariable MultiTagKind kind,
			@Valid @RequestBody ThresholdChangeRequest body) {
		return ThresholdChangeResponse.of(this.administration.changeThresholds(kind, body.tagThreshold(),
				body.storeThreshold(), ChangeActor.OPERATOR));
	}

}
