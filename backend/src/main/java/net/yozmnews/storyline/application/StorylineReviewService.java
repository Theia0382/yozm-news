package net.yozmnews.storyline.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.LinkScore;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineLinkingCriteria;
import net.yozmnews.storyline.StorylineMembership;
import net.yozmnews.storyline.StorylineRelationAsked;
import net.yozmnews.storyline.StorylineRelationCriteria;
import net.yozmnews.storyline.StorylineRelationDetail;
import net.yozmnews.storyline.StorylineReview;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationGraph;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.relation.StoredAsked;
import net.yozmnews.storyline.domain.relation.StoredRelation;
import net.yozmnews.storyline.domain.storyline.ClosedMembers;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.StoredStoryline;
import net.yozmnews.storyline.domain.storyline.StorylineQueryRepository;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 운영자 조회 {@link StorylineReview} 의 구현 — docs/specs/storyline.md §4 · storyline-R34.
 * <p>
 * 합쳐진 스토리라인이면 남는 스토리라인을 찾은 뒤 읽는다. 소속 근거의 가족(자기와 모든 하위)은 닫은 사건 집합과 같은 정본
 * {@link ClosedMembers#families} 로 정하고, 관계를 공개 값으로 옮기는 것은 공개 조회와 같은 {@link RelationViews} 다. 기준 이력 ID 는
 * 그 값으로 바꿔 낸다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class StorylineReviewService implements StorylineReview {

	private final StorylineQueryRepository storylines;

	private final StorylineRepository memberships;

	private final RelationRepository relations;

	private final LinkingCriteriaRepository linking;

	private final LinkingCriteria currentLinking;

	private final RelationCriteria currentRelation;

	StorylineReviewService(StorylineQueryRepository storylines, StorylineRepository memberships,
			RelationRepository relations, LinkingCriteriaRepository linking, LinkingCriteria currentLinking,
			RelationCriteria currentRelation) {
		this.storylines = storylines;
		this.memberships = memberships;
		this.relations = relations;
		this.linking = linking;
		this.currentLinking = currentLinking;
		this.currentRelation = currentRelation;
	}

	@Override
	public List<StorylineMembership> memberships(StorylineId id) {
		Optional<StoredStoryline> stored = this.storylines.find(id.value());
		if (stored.isEmpty()) {
			return List.of();
		}
		Set<Long> family = ClosedMembers.everyone(
				ClosedMembers.families(List.of(stored.get().id()), new RelationGraph(this.relations.all())));
		Map<Long, Map<Long, LinkJudgement>> judgements = this.memberships.judgements(family);
		Set<Long> criteriaIds = new TreeSet<>();
		judgements.values()
			.forEach(byEvent -> byEvent.values()
				.stream()
				.map(LinkJudgement::criteriaId)
				.filter(criteria -> criteria != null)
				.forEach(criteriaIds::add));
		Map<Long, LinkingCriteria> criteria = this.linking.findAll(criteriaIds);
		List<StorylineMembership> found = new ArrayList<>();
		judgements.forEach((storyline, byEvent) -> byEvent.forEach((event, judgement) -> found
			.add(new StorylineMembership(new StorylineId(storyline), new EventId(event), judgement.linkedBy(),
					score(judgement.score()), linkingCriteria(criteria, judgement.criteriaId()), judgement.linkedAt()))));
		return List.copyOf(found);
	}

	@Override
	public List<StorylineRelationDetail> relations(StorylineId id) {
		Optional<StoredStoryline> stored = this.storylines.find(id.value());
		if (stored.isEmpty()) {
			return List.of();
		}
		List<StoredRelation> rows = this.relations.of(stored.get().id());
		Map<Long, RelationCriteria> criteria = this.relations
			.criteria(rows.stream().map(StoredRelation::criteriaId).distinct().toList());
		return rows.stream()
			.map(row -> new StorylineRelationDetail(RelationViews.of(row), row.probability(),
					new RelationJudgementId(row.judgementId()), relationCriteria(criteria.get(row.criteriaId()))))
			.toList();
	}

	@Override
	public List<StorylineRelationAsked> asked(StorylineId id) {
		Optional<StoredStoryline> stored = this.storylines.find(id.value());
		if (stored.isEmpty()) {
			return List.of();
		}
		long self = stored.get().id();
		List<StoredAsked> rows = this.relations.askedOf(self);
		Map<Long, RelationCriteria> criteria = this.relations
			.criteria(rows.stream().map(StoredAsked::criteriaId).distinct().toList());
		return rows.stream()
			.map(row -> new StorylineRelationAsked(new StorylineId((row.lowId() == self) ? row.highId() : row.lowId()),
					row.smallerEvents(), row.outcome(),
					(row.judgementId() == null) ? null : new RelationJudgementId(row.judgementId()),
					relationCriteria(criteria.get(row.criteriaId())), row.askedAt()))
			.toList();
	}

	@Override
	public StorylineLinkingCriteria linkingCriteria() {
		return new StorylineLinkingCriteria(this.currentLinking.window(), this.currentLinking.threshold());
	}

	@Override
	public StorylineRelationCriteria relationCriteria() {
		return relationCriteria(this.currentRelation);
	}

	private static @Nullable LinkScore score(@Nullable Score score) {
		return (score == null) ? null : new LinkScore(score.score(), score.cosine(), score.overlap());
	}

	private static @Nullable StorylineLinkingCriteria linkingCriteria(Map<Long, LinkingCriteria> criteria,
			@Nullable Long criteriaId) {
		if (criteriaId == null) {
			return null;
		}
		LinkingCriteria found = criteria.get(criteriaId);
		if (found == null) {
			throw new IllegalStateException("소속이 가리키는 잇기 기준 이력이 없다: " + criteriaId);
		}
		return new StorylineLinkingCriteria(found.window(), found.threshold());
	}

	private static StorylineRelationCriteria relationCriteria(@Nullable RelationCriteria criteria) {
		if (criteria == null) {
			throw new IllegalStateException("관계 · 물은 기록이 가리키는 관계 기준 이력이 없다");
		}
		return new StorylineRelationCriteria(criteria.probability(), criteria.oneMatter(), criteria.minEvents(),
				criteria.candidateScore(), criteria.window());
	}

}
