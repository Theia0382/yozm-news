package net.yozmnews.admin.adapter.in.web;

import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.SourceAdministration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 프리셋 목록과 프리셋 소스 등록. docs/specs/admin.md §3.
 */
@WebRole
@RestController
@ApiResponse(responseCode = "401", description = "운영자 토큰이 없거나 맞지 않는다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
@ApiResponse(responseCode = "403", description = "운영자 기능이 닫혀 있다",
		content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(implementation = ApiProblem.class)))
class AdminPresetController {

	private final SourceAdministration administration;

	AdminPresetController(SourceAdministration administration) {
		this.administration = Objects.requireNonNull(administration, "administration");
	}

	@GetMapping("/api/admin/presets")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	List<PresetResponse> presets() {
		return this.administration.presets().stream().map(PresetResponse::of).toList();
	}

	@PostMapping("/api/admin/presets/{key}/registration")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "카탈로그에 없는 프리셋이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	RegistrationResponse register(@PathVariable String key) {
		return RegistrationResponse.of(this.administration.registerPreset(key));
	}

}
