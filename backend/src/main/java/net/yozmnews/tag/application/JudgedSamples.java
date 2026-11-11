package net.yozmnews.tag.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagJudgementView;
import net.yozmnews.tag.domain.overview.EvaluationSample;
import net.yozmnews.tag.domain.result.SampleRow;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 흘려 받은 평가에 판정 원본의 입력 토큰을 붙인다 — 정본 · docs/specs/tag.md §4 · tag-R29 · tag-R35.
 * <p>
 * 평가를 {@link #BATCH} 건씩 모아 {@code evaluation} 의 판정 원본 조회({@link ArticleTagging#find})로 입력 토큰을 얻어
 * {@link EvaluationSample} 로 넘긴다 — 넘기는 순서는 받은 순서 그대로다. 판정 원본이 없는 평가(지운 기사 따르기가 두 모듈의 두
 * 트랜잭션이라 그 사이에 보일 수 있다)는 넘기지 않는다. 부르는 쪽 트랜잭션 안에서 읽는다 — 흘려 받기의 커서를 연 같은 트랜잭션 · 같은
 * 연결이다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Component
class JudgedSamples {

	/** 판정 원본 조회 한 번에 묻는 평가 수 — 측정 전 목표 */
	static final int BATCH = 1_000;

	private final ArticleTagging tagging;

	JudgedSamples(ArticleTagging tagging) {
		this.tagging = tagging;
	}

	/**
	 * @param source 평가를 흘려 주는 조회 — 넘겨받은 소비자에 평가를 차례로 넣는다
	 * @param sink 입력 토큰을 붙인 평가를 받는다
	 */
	void stream(Consumer<Consumer<SampleRow>> source, Consumer<EvaluationSample> sink) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(sink, "sink");
		List<SampleRow> pending = new ArrayList<>(BATCH);
		source.accept(row -> {
			pending.add(row);
			if (pending.size() == BATCH) {
				flush(pending, sink);
			}
		});
		flush(pending, sink);
	}

	private void flush(List<SampleRow> pending, Consumer<EvaluationSample> sink) {
		if (pending.isEmpty()) {
			return;
		}
		Map<TagJudgementId, TagJudgementView> views = this.tagging
			.find(pending.stream().map(SampleRow::judgementId).toList());
		for (SampleRow row : pending) {
			@Nullable TagJudgementView view = views.get(row.judgementId());
			if (view != null) {
				sink.accept(new EvaluationSample(row.evaluation(), row.formatProbability(), view.inputTokens()));
			}
		}
		pending.clear();
	}

}
