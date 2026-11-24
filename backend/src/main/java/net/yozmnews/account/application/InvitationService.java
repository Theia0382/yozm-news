package net.yozmnews.account.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.yozmnews.account.InvitationAdministration;
import net.yozmnews.account.InvitationRevocationResult;
import net.yozmnews.account.InvitationState;
import net.yozmnews.account.InvitationView;
import net.yozmnews.account.IssuedInvitation;
import net.yozmnews.account.UnknownInvitationException;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.invitation.Invitation;
import net.yozmnews.account.domain.invitation.InvitationPolicy;
import net.yozmnews.account.domain.invitation.InvitationRepository;
import net.yozmnews.account.domain.secret.InvitationToken;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 — 운영(발급 · 최근 목록 · 취소, {@link InvitationAdministration} 의 구현)과 링크를 연 사람의 상태 확인. docs/specs/user.md §4
 */
@Service
public class InvitationService implements InvitationAdministration {

	private final InvitationRepository invitations;

	private final AccountRepository accounts;

	private final InstantSource clock;

	private final SecureRandom random = new SecureRandom();

	InvitationService(InvitationRepository invitations, AccountRepository accounts, InstantSource clock) {
		this.invitations = Objects.requireNonNull(invitations, "invitations");
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	@Transactional
	public IssuedInvitation issue(@Nullable String memo) {
		Optional<String> read = InvitationPolicy.memo(memo);
		if (read.isPresent() && !InvitationPolicy.memoFits(read.get())) {
			return new IssuedInvitation.MemoTooLong(InvitationPolicy.MEMO_MAX_CODE_POINTS);
		}
		InvitationToken token = InvitationToken.generate(this.random);
		Invitation saved = this.invitations.save(Invitation.issue(token.digest(), read.orElse(null),
				this.clock.instant()));
		return new IssuedInvitation.Issued(saved.id(), token.value(), saved.expiresAt());
	}

	@Override
	@Transactional(readOnly = true)
	public List<InvitationView> recent() {
		Instant now = this.clock.instant();
		List<Invitation> recent = this.invitations.recent(InvitationPolicy.LIST_SIZE);
		Map<Long, String> emails = StreamSupport
			.stream(this.accounts
				.findAllById(recent.stream().flatMap(invitation -> invitation.usedBy().stream()).map(UserId::value)
					.toList())
				.spliterator(), false)
			.collect(Collectors.toMap(account -> account.id().value(), account -> account.email().value(),
					(a, b) -> a));
		return recent.stream()
			.map(invitation -> new InvitationView(invitation.id(), invitation.memo().orElse(null),
					invitation.createdAt(), invitation.expiresAt(), invitation.state(now),
					invitation.usedAt().orElse(null),
					invitation.usedBy().map(user -> emails.get(user.value())).orElse(null),
					invitation.revokedAt().orElse(null)))
			.toList();
	}

	@Override
	@Transactional
	public InvitationRevocationResult revoke(long invitationId) {
		Invitation invitation = this.invitations.lockById(invitationId)
			.orElseThrow(() -> new UnknownInvitationException(invitationId));
		Instant now = this.clock.instant();
		InvitationState state = invitation.state(now);
		if (state != InvitationState.VALID) {
			return new InvitationRevocationResult.NotRevocable(state);
		}
		this.invitations.save(invitation.revoke(now));
		return new InvitationRevocationResult.Revoked();
	}

	/**
	 * 링크를 연 사람의 상태 확인.
	 * @return 읽을 수 없거나 없는 토큰이면 비어 있다
	 */
	@Transactional(readOnly = true)
	public Optional<InvitationState> check(String rawToken, Instant now) {
		return InvitationToken.parse(rawToken)
			.flatMap(token -> this.invitations.findByTokenDigest(token.digest().value()))
			.map(invitation -> invitation.state(now));
	}

	@Override
	public String toString() {
		return "InvitationService";
	}

}
