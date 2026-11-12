package net.yozmnews.user.domain.credential;

import org.springframework.data.repository.CrudRepository;

/**
 * 자체 로그인 자격 저장소 — 식별자는 사용자 ID. docs/specs/user.md §4 · user-R16
 */
public interface PasswordCredentialRepository extends CrudRepository<PasswordCredential, Long> {

}
