package net.yozmnews.digest.domain.content;

import org.springframework.data.repository.CrudRepository;

/**
 * 다이제스트 내용 저장소 — docs/specs/digest.md §4
 */
public interface DigestSectionRepository extends CrudRepository<DigestSection, Long> {
}
