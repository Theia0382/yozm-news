package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.user.AccountAdministration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API 의 계정 — 계정 생성과 첫 로그인 전 계정의 초기 비밀번호 다시 정하기. {@code user} 의 {@link AccountAdministration} 결과를 옮긴다.
 * docs/specs/admin.md §3 · admin-R1 ~ admin-R3
 */
@WebRole
@RestController
class AdminAccountController {

	private final AccountAdministration accounts;

	AdminAccountController(AccountAdministration accounts) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
	}

	@PostMapping("/api/admin/accounts")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸거나, 이메일 · 표시 이름 형식 또는 초기 비밀번호 규칙을 어겼다",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ApiProblem.class)))
	AccountCreationResponse createAccount(@Valid @RequestBody AccountCreationRequest body) {
		return AccountCreationResponse
			.of(this.accounts.create(body.email(), body.displayName(), body.initialPassword()));
	}

	@PutMapping("/api/admin/accounts/initial-password")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸거나 초기 비밀번호 규칙을 어겼다",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "그 이메일의 계정이 없다",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ApiProblem.class)))
	InitialPasswordResetResponse resetInitialPassword(@Valid @RequestBody InitialPasswordResetRequest body) {
		return InitialPasswordResetResponse
			.of(this.accounts.resetInitialPassword(body.email(), body.initialPassword()));
	}

}
