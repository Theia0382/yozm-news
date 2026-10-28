package net.yozmnews.source.domain.registry;

import org.springframework.data.repository.CrudRepository;

/**
 * 소스 저장소. 같은 프리셋을 두 소스가 참조하지 않는다 — {@code sources.preset_key} 의 고유 제약이 지킨다.
 */
public interface SourceRepository extends CrudRepository<Source, Long> {

}
