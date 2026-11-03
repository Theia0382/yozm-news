package net.yozmnews.evaluation.application;

import java.util.List;

import net.yozmnews.evaluation.TagKeyLineage;
import net.yozmnews.evaluation.TagKeyMapping;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.tagset.KeyLineage;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link TagKeyLineage} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R26.
 * <p>
 * 그 버전부터 최신 버전까지를 번호 순으로 한 번에 읽어 {@link KeyLineage} 로 해석한다. 버전은 만들 때 바로 활성이 되므로 최신 버전이
 * 활성 버전이다(evaluation-R16). 버전 내용 · 계보는 바뀌지 않으므로 한 번의 조회로 읽은 버전들은 서로 맞는다. 트랜잭션 · 역할
 * 애너테이션이 없다(두 역할 공통).
 */
@Service
class TagKeyLineageService implements TagKeyLineage {

	private final TagSetRepository tagSets;

	TagKeyLineageService(TagSetRepository tagSets) {
		this.tagSets = tagSets;
	}

	@Override
	public TagKeyMapping toActive(TagSetId from) {
		TagSet start = this.tagSets.findById(from.value())
			.orElseThrow(() -> new IllegalArgumentException("태그 체계 버전이 없다: " + from.value()));
		List<KeyLineage.Version> versions = this.tagSets.findByNumberGreaterThanEqualOrderByNumber(start.number())
			.stream()
			.map(tagSet -> new KeyLineage.Version(tagSet.id(), tagSet.content(), tagSet.lineage()))
			.toList();
		return KeyLineage.toLatest(versions);
	}

}
