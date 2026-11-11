package net.yozmnews.evaluation.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.EvaluationCursor;
import net.yozmnews.evaluation.EvaluationDetailView;
import net.yozmnews.evaluation.EvaluationFilter;
import net.yozmnews.evaluation.EvaluationId;
import net.yozmnews.evaluation.EvaluationListResult;
import net.yozmnews.evaluation.EvaluationListing;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.EvaluationReview;
import net.yozmnews.evaluation.EvaluationRow;
import net.yozmnews.evaluation.EvaluationSampleResult;
import net.yozmnews.evaluation.EvaluationSampleView;
import net.yozmnews.evaluation.TagRef;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.ListedEvaluation;
import net.yozmnews.evaluation.domain.result.TagJudgement;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;
import net.yozmnews.evaluation.domain.review.EvaluationSampler;
import net.yozmnews.evaluation.domain.review.ReviewFilter;
import net.yozmnews.evaluation.domain.review.StoredKeys;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 운영 API {@link EvaluationReview} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R33 · evaluation-R34. 역할 애너테이션이
 * 없다(두 역할 공통).
 * <p>
 * 세 연산 모두 읽기 전용 {@code REPEATABLE READ} 트랜잭션 하나에서 읽는다 — 체계 · 기준 · 평가 행이 한 스냅숏이다. 판정은
 * {@link TagJudgement}, 거르기는 {@link ReviewFilter}, 표집은 {@link EvaluationSampler} 가 한다.
 */
@Service
class EvaluationReviewService implements EvaluationReview {

	private final EvaluationResultRepository results;

	private final TagSetRepository tagSets;

	private final TagSetViews views;

	private final ThresholdRepository thresholds;

	private final ArticleTagging tagging;

	private final TransactionTemplate snapshot;

	EvaluationReviewService(EvaluationResultRepository results, TagSetRepository tagSets, TagSetViews views,
			ThresholdRepository thresholds, ArticleTagging tagging, PlatformTransactionManager transactions) {
		this.results = results;
		this.tagSets = tagSets;
		this.views = views;
		this.thresholds = thresholds;
		this.tagging = tagging;
		this.snapshot = new TransactionTemplate(transactions);
		this.snapshot.setReadOnly(true);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
	}

	@Override
	public EvaluationListResult list(TagSetId version, EvaluationPeriod period, EvaluationFilter filter,
			@Nullable EvaluationCursor after, int limit) {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(filter, "filter");
		if (limit < 1 || limit > MAX_PAGE_SIZE) {
			return new EvaluationListResult.LimitOutOfRange();
		}
		return Objects.requireNonNull(this.snapshot.execute(status -> {
			if (!this.tagSets.existsById(version.value())) {
				return new EvaluationListResult.UnknownVersion();
			}
			TagSetView tagSet = this.views.of(version);
			Optional<TagRef> unknown = ReviewFilter.unknown(filter, tagSet);
			if (unknown.isPresent()) {
				return new EvaluationListResult.UnknownTag(unknown.get());
			}
			TagSetThresholds current = this.thresholds.current(version);
			StoredKeys required = ReviewFilter.storedKeys(filter, tagSet).orElse(null);
			String formatKey = ReviewFilter.formatKey(filter, tagSet).orElse(null);
			List<EvaluationRow> rows = new ArrayList<>(limit);
			boolean[] more = { false };
			this.results.scanNewestFirst(version, period.from(), period.to(), after, required, formatKey, listed -> {
				Judged judged = judge(listed, tagSet, current);
				if (!ReviewFilter.matches(filter, tagSet, judged, listed.sample().evaluation().formatKey())) {
					return true;
				}
				if (rows.size() == limit) {
					// 맞는 평가가 하나 더 있다 — 쪽이 찼으므로 멈춘다
					more[0] = true;
					return false;
				}
				rows.add(EvaluationReviewViews.row(listed, tagSet, judged));
				return true;
			});
			EvaluationCursor next = null;
			if (more[0]) {
				EvaluationRow last = rows.getLast();
				next = new EvaluationCursor(last.evaluatedAt(), last.id());
			}
			return new EvaluationListResult.Listed(
					new EvaluationListing(version, period.from(), period.to(), rows, next));
		}));
	}

	@Override
	public EvaluationSampleResult sample(TagSetId version, EvaluationPeriod period, long seed, int size) {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(period, "period");
		if (size < 1 || size > MAX_SAMPLE_SIZE) {
			return new EvaluationSampleResult.SizeOutOfRange();
		}
		if (seed < 0 || seed > MAX_SEED) {
			return new EvaluationSampleResult.SeedOutOfRange();
		}
		return Objects.requireNonNull(this.snapshot.execute(status -> {
			if (!this.tagSets.existsById(version.value())) {
				return new EvaluationSampleResult.UnknownVersion();
			}
			TagSetView tagSet = this.views.of(version);
			TagSetThresholds current = this.thresholds.current(version);
			EvaluationSampler sampler = new EvaluationSampler(seed, size);
			this.results.streamIds(version, period.from(), period.to(), sampler::add);
			Map<Long, ListedEvaluation> listed = new HashMap<>();
			List<Long> chosen = sampler.chosen();
			for (ListedEvaluation evaluation : this.results
				.findListed(chosen.stream().map(EvaluationId::new).toList())) {
				listed.put(evaluation.id().value(), evaluation);
			}
			List<EvaluationRow> rows = new ArrayList<>(chosen.size());
			for (long id : chosen) {
				// 고른 ID 는 같은 스냅숏의 평가 행이다 — 없으면 불변식이 깨진 것이다
				ListedEvaluation evaluation = Objects.requireNonNull(listed.get(id), () -> "고른 평가가 없다: " + id);
				rows.add(EvaluationReviewViews.row(evaluation, tagSet, judge(evaluation, tagSet, current)));
			}
			return new EvaluationSampleResult.Sampled(new EvaluationSampleView(version, period.from(), period.to(),
					seed, sampler.population(), rows));
		}));
	}

	@Override
	public Optional<EvaluationDetailView> detail(EvaluationId id) {
		Objects.requireNonNull(id, "id");
		// 판정 원본이 없는 평가(지운 기사 따르기의 두 트랜잭션 사이)는 빈 값이다(tag-R21)
		return Objects.requireNonNull(this.snapshot.execute(status -> this.results.findDetail(id).flatMap(detail -> {
			TagSetId version = detail.evaluation().tagSetId();
			TagSetView tagSet = this.views.of(version);
			TagSetThresholds current = this.thresholds.current(version);
			Judged judged = TagJudgement.judge(detail.evaluation().tags(), tagSet, current);
			return Optional.ofNullable(this.tagging.find(List.of(detail.judgementId())).get(detail.judgementId()))
				.map(judgement -> EvaluationReviewViews.detail(detail, judgement, tagSet, current, judged));
		})));
	}

	private static Judged judge(ListedEvaluation listed, TagSetView tagSet, TagSetThresholds current) {
		return TagJudgement.judge(listed.sample().evaluation().tags(), tagSet, current);
	}

}
