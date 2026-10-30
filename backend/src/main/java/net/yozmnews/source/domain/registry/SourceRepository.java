package net.yozmnews.source.domain.registry;

import org.springframework.data.repository.CrudRepository;

/**
 * 소스 저장소 — docs/specs/source.md §4 · source-R2 · source-R3.
 * <p>
 * 같은 프리셋의 판정은 {@code sources.preset_key} 의 고유 제약이 한다.
 */
public interface SourceRepository extends CrudRepository<Source, Long> {

}
