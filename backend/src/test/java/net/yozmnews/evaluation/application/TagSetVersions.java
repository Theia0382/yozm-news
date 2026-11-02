package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetDefinition;
import net.yozmnews.evaluation.domain.tagset.TagSetParse;
import net.yozmnews.evaluation.domain.tagset.TagSetParser;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시험 픽스처 — 저장소의 수단(기준 버전 끄기 · 다음 버전 저장 · 처음 태그 기준 · 저장 기준)만으로 새 버전을 활성으로 만든다. 대조 ·
 * 재평가는 하지 않는다 — 그 유스케이스는 {@code TagSetVersioning.activate} 다. 뒤처리는 {@link TagSetRows#removeVersionsAfterFirst}.
 */
final class TagSetVersions {

	private TagSetVersions() {
	}

	static TagSetDefinition parsed(String text) {
		TagSetParse parse = new TagSetParser().parse(text);
		assertThat(parse).as("%s", parse).isInstanceOf(TagSetParse.Parsed.class);
		return ((TagSetParse.Parsed) parse).definition();
	}

	/** 원문을 다음 버전으로 저장하고 활성으로 바꾼다 */
	static TagSetId activate(String text, TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction, Instant now) {
		TagSetDefinition definition = parsed(text);
		return transaction.execute(status -> {
			TagSet base = tagSets.findByActiveTrue().orElseThrow();
			assertThat(tagSets.deactivate(base.id().value())).isTrue();
			TagSet saved = tagSets.save(TagSet.next(base, definition, ChangeActor.OPERATOR, now));
			definition.initialThresholds()
				.forEach((kind, values) -> thresholds.insertInitial(saved.id(), kind, values, ChangeActor.OPERATOR, now));
			return saved.id();
		});
	}

}
