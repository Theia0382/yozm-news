package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.IssuedInvitation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 발급한 초대 — 토큰은 이 응답에 한 번만 나간다. 화면이 자기 주소로 링크({@code /invite#토큰})를 만든다. docs/specs/admin.md §3
 *
 * @param id 초대 ID
 * @param token 초대 토큰
 * @param expiresAt 만료 시각
 */
record InvitationIssueResponse(long id, String token, Instant expiresAt) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 400 — 메모가 너무 길 때(상한은 결과가 함께 낸다)
	 */
	static InvitationIssueResponse of(IssuedInvitation issued) {
		return switch (issued) {
			case IssuedInvitation.Issued ok -> new InvitationIssueResponse(ok.id(), ok.token(), ok.expiresAt());
			case IssuedInvitation.MemoTooLong tooLong -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"메모는 " + tooLong.max() + "자까지다");
		};
	}

	@Override
	public String toString() {
		return "InvitationIssueResponse[id=" + this.id + ", token=숨김]";
	}

}
