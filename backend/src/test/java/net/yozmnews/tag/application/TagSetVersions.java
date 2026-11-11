package net.yozmnews.tag.application;

import java.time.Instant;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.domain.tagset.TagSet;
import net.yozmnews.tag.domain.tagset.TagSetDefinition;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.tagset.TagSetTexts;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시험 픽스처 — 제품의 새 버전 쓰기({@link TagSetVersionStore#next})로 새 버전을 활성으로 만든다. 대조 · 재평가는 하지 않는다 — 그
 * 유스케이스는 {@code TagSetVersioning.activate} 다. 뒤처리는 {@link TagSetRows#removeVersionsAfterFirst}.
 */
final class TagSetVersions {

	private TagSetVersions() {
	}

	/** 원문을 다음 버전으로 저장하고 활성으로 바꾼다 */
	static TagSetId activate(String text, TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction, Instant now) {
		TagSetDefinition definition = TagSetTexts.parsed(text);
		TagSetVersionStore versions = new TagSetVersionStore(tagSets, thresholds);
		return transaction.execute(status -> {
			TagSet base = tagSets.findByActiveTrue().orElseThrow();
			return versions.next(base, definition, ChangeActor.OPERATOR, now).id();
		});
	}

}
