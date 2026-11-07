package net.yozmnews.event.application;

import java.util.Objects;

import net.yozmnews.event.AssignmentCriteria;

/**
 * 검증된 사건 기준 · 저장 기준을 회차와 운영자 조회에 운반하는 빈 — docs/specs/event.md §4 · event-R17.
 * <p>
 * 공개 타입 {@link AssignmentCriteria} 를 그대로 빈으로 두면 모듈 관측이 공개 타입의 빈을 프록시로 감싸려다 실패한다(레코드는 final 이다) —
 * 그래서 이 내부 타입으로 감싼다.
 */
record AssignmentSettings(AssignmentCriteria criteria) {

	AssignmentSettings {
		Objects.requireNonNull(criteria, "criteria");
	}

}
