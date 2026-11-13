package net.yozmnews.storyline.application;

import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRelationKind;
import net.yozmnews.storyline.StorylineRelationView;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.StoredRelation;

/**
 * 읽은 관계를 공개 값으로 옮긴다 — 공개 조회와 운영자 조회가 함께 쓰는 정본 · docs/specs/storyline.md §4.
 */
final class RelationViews {

	private RelationViews() {
	}

	static StorylineRelationView of(StoredRelation stored) {
		return new StorylineRelationView(kind(stored.relation().kind()), new StorylineId(stored.relation().sourceId()),
				new StorylineId(stored.relation().targetId()), stored.linkedAt());
	}

	private static StorylineRelationKind kind(RelationKind kind) {
		return switch (kind) {
			case CONTAINS -> StorylineRelationKind.CONTAINS;
			case FOLLOWS -> StorylineRelationKind.FOLLOWS;
		};
	}

}
