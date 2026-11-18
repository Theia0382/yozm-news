package net.yozmnews.storyline.application;

import java.time.Instant;
import java.util.List;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventProfile;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.domain.linking.LinkingAskedRepository;
import net.yozmnews.storyline.domain.linking.LinkingScoring;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.relation.RelationMove;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.MergeBasis;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 스토리라인 하나를 다른 스토리라인에 합치는 실행 — 정본 · docs/specs/storyline.md §4 · storyline-R17 · R18 · R32.
 * <p>
 * 정돈의 합치기(구성 사건이 같음 · 사건 하나만 남음)와 조각 합치기(관계 판정이 같은 사안의 조각이라고 답함)가 함께 쓴다. 합칠지는
 * 부르는 쪽이 정한다. 부르는 쪽이 모듈 잠금 트랜잭션 안에서 부르고, 돌려받은 것을 {@code StorylinesRetired} 로 알리며, 남는
 * 스토리라인을 관계 대기에 넣는다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Component
class StorylineMerger {

	private final StorylineRepository storylines;

	private final RelationRepository relations;

	private final RelationQueueRepository relationQueue;

	private final LinkingAskedRepository asked;

	private final EventDirectory events;

	StorylineMerger(StorylineRepository storylines, RelationRepository relations, RelationQueueRepository relationQueue,
			LinkingAskedRepository asked, EventDirectory events) {
		this.storylines = storylines;
		this.relations = relations;
		this.relationQueue = relationQueue;
		this.asked = asked;
		this.events = events;
	}

	/**
	 * merged 를 survivor 에 합친다.
	 * <ol>
	 * <li>merged 의 소속 가운데 survivor 에 없는 사건을 survivor 로 옮긴다 — 근거는 그대로다. 옮겨 온 사건을 survivor 에 물었던 붙이기
	 * 기록은 지운다(이제 소속이다)</li>
	 * <li>merged 의 관계와 맺은 쌍을 survivor 로 옮기고, 그 넘김 · 물은 기록(관계 · 잇기)과 관계 대기 행을 지운다 — 합쳐진 행은 남으므로
	 * FK 연쇄가 돌지 않는다</li>
	 * <li>merged 의 행이 survivor 를 가리키게 한다</li>
	 * <li>옮긴 사건이 있으면 survivor 의 표현을 지금 구성 사건으로 다시 정한다</li>
	 * </ol>
	 * @param basis 조각 합치기면 그 근거 — 정돈의 합치기면 null
	 * @return 알릴 합쳐진 스토리라인
	 */
	RetiredStoryline merge(long merged, long survivor, @Nullable MergeBasis basis, Instant now) {
		List<Long> moved = this.storylines.moveMembers(merged, survivor);
		this.asked.forgetLinks(survivor, moved);
		this.relations.applyMove(RelationMove.plan(this.relations.all(), merged, survivor), now);
		// 옮긴 뒤의 고리로 맺은 쌍을 고친다 — 맺은 쌍에 든 합쳐진 스토리라인을 남는 쪽으로
		RelationMove.remark(this.relations.grouped(), merged, survivor)
			.forEach(remark -> this.relations.regroup(remark.relation(), remark.by()));
		this.relations.clearReferrals(merged);
		this.relations.forgetAsked(merged);
		this.relationQueue.delete(merged);
		this.asked.forgetStoryline(merged);
		this.storylines.merge(merged, survivor, basis, now);
		if (!moved.isEmpty()) {
			represent(survivor, now);
		}
		return new RetiredStoryline(new StorylineId(merged), null);
	}

	/** 구성 사건의 지금 표현으로 스토리라인 표현을 다시 정한다 — 읽을 구성 사건이 없으면 그대로 둔다(storyline-R7) */
	private void represent(long storyline, Instant now) {
		List<Long> members = this.storylines.members(List.of(storyline)).getOrDefault(storyline, List.of());
		List<EventProfile> readable = LinkingMaterials.readable(members,
				this.events.profiles(members.stream().map(EventId::new).toList(), LinkingScoring.KEY_TERMS));
		if (!readable.isEmpty()) {
			this.storylines.save(storyline, Representation.of(readable), now);
		}
	}

}
