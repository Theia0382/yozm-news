package net.yozmnews.user.domain.credential;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 자체 로그인 자격 저장소 — 식별자는 사용자 ID. docs/specs/user.md §4 · user-R16
 */
public interface PasswordCredentialRepository extends CrudRepository<PasswordCredential, Long> {

	/**
	 * 자격 행을 잠근다 — 자격을 바꾸는 유스케이스(비밀번호 변경 · 복구 재설정 · 초기 비밀번호 재설정)가 판정 전에 부른다. 같은 계정의 변경은
	 * 이 잠금에서 줄을 서, 나중 쪽이 앞 쪽의 결과를 보고 판정한다. 트랜잭션 안에서 부른다.
	 * @return 자격이 없으면 비어 있다
	 */
	@Query("select user_id from user_password_credentials where user_id = :userId for update")
	Optional<Long> lockForUpdate(long userId);

}
