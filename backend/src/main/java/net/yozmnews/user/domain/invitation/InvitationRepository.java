package net.yozmnews.user.domain.invitation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 초대 저장소 — docs/specs/user.md §4. 토큰은 다이제스트로만 찾는다.
 */
public interface InvitationRepository extends CrudRepository<Invitation, Long> {

	/**
	 * 토큰 다이제스트의 초대를 잠그고 읽는다 — 같은 초대의 가입 · 취소가 줄을 선다. 트랜잭션 안에서 부른다.
	 */
	@Query("select * from user_invitations where token_digest = :tokenDigest for update")
	Optional<Invitation> lockByTokenDigest(byte[] tokenDigest);

	/** 토큰 다이제스트의 초대 */
	@Query("select * from user_invitations where token_digest = :tokenDigest")
	Optional<Invitation> findByTokenDigest(byte[] tokenDigest);

	/** ID 의 초대를 잠그고 읽는다 — 트랜잭션 안에서 부른다 */
	@Query("select * from user_invitations where id = :id for update")
	Optional<Invitation> lockById(long id);

	/** 최근에 발급한 초대 — 발급 순 역순(ID 역순) */
	@Query("select * from user_invitations order by id desc limit :size")
	List<Invitation> recent(int size);

}
