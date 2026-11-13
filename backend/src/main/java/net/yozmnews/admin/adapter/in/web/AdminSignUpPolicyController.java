package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.WebRole;
import net.yozmnews.user.SignUpPolicyAdministration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API 의 가입 정책 — 지금 값 · 이력과 변경. {@code user} 의 {@link SignUpPolicyAdministration} 결과를 옮기고, 변경은
 * {@link ChangeActor#OPERATOR} 로 부른다. docs/specs/admin.md §3 · admin-R1 · admin-R3
 */
@WebRole
@RestController
class AdminSignUpPolicyController {

	private final SignUpPolicyAdministration policy;

	AdminSignUpPolicyController(SignUpPolicyAdministration policy) {
		this.policy = Objects.requireNonNull(policy, "policy");
	}

	@GetMapping("/api/admin/sign-up-policy")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	SignUpPolicyStatusResponse signUpPolicyStatus() {
		return new SignUpPolicyStatusResponse(this.policy.open(),
				this.policy.changes().stream().map(SignUpPolicyHistoryResponse::of).toList());
	}

	@PutMapping("/api/admin/sign-up-policy")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸다",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ApiProblem.class)))
	SignUpPolicyChangeResponse changeSignUpPolicy(@Valid @RequestBody SignUpPolicyChangeRequest body) {
		return SignUpPolicyChangeResponse.of(this.policy.change(body.open(), ChangeActor.OPERATOR));
	}

}
