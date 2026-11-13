package net.yozmnews.admin.adapter.in.web;

import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.WebRole;
import net.yozmnews.user.InvitationAdministration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API 의 초대 — 최근 목록 · 발급 · 취소. {@code user} 의 {@link InvitationAdministration} 결과를 옮긴다. docs/specs/admin.md §3 ·
 * admin-R1 ~ admin-R3
 */
@WebRole
@RestController
class AdminInvitationController {

	private final InvitationAdministration invitations;

	AdminInvitationController(InvitationAdministration invitations) {
		this.invitations = Objects.requireNonNull(invitations, "invitations");
	}

	@GetMapping("/api/admin/invitations")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	InvitationListResponse invitations() {
		return new InvitationListResponse(
				this.invitations.recent().stream().map(InvitationRowResponse::of).toList());
	}

	@PostMapping("/api/admin/invitations")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "요청 본문의 형식이 틀렸거나 메모가 너무 길다",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ApiProblem.class)))
	InvitationIssueResponse issueInvitation(@RequestBody InvitationIssueRequest body) {
		return InvitationIssueResponse.of(this.invitations.issue(body.memo()));
	}

	@PostMapping("/api/admin/invitations/{id}/revoke")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 초대다",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ApiProblem.class)))
	InvitationRevocationResponse revokeInvitation(@PathVariable long id) {
		return InvitationRevocationResponse.of(this.invitations.revoke(AdminIds.invitation(id)));
	}

}
