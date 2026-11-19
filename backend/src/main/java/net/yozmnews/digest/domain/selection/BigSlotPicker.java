package net.yozmnews.digest.domain.selection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 큰 슬롯 고르기 — 정본 — docs/specs/digest.md §5
 * <p>
 * 큰 판정 행을 앞에서부터 사용자의 큰 슬롯 수만큼 쓴다 — 후보 사건을 하나도 내지 못한 행은 세지 않고 다음 행으로 간다. 행마다 사건을 기간 안
 * 중요도 합 내림차순(같으면 사건 ID 오름차순)으로 {@link SelectionRules#BIG_EVENTS_PER_STORYLINE} 개까지 — 후보가 없거나 앞 행이 쓴
 * 사건은 세지 않고 다음 사건으로 간다. 큰 슬롯 사건 수가 상한을 넘으면 행 순서로 돌며 하나씩 담아 행마다 고르게 줄인다. 결과는 행 순서, 행
 * 안에서는 사건 순서다.
 */
public final class BigSlotPicker {

	private static final Comparator<BigEvent> EVENT_ORDER = Comparator
		.comparing(BigEvent::importanceSum, Comparator.reverseOrder())
		.thenComparingLong(event -> event.id().value());

	private BigSlotPicker() {
	}

	/**
	 * @param rows 큰 판정 행 — 큰 판정 목록의 순서 그대로
	 * @param bigSlots 사용자의 큰 슬롯 수 — 0 이면 빈 결과
	 * @param cap 큰 슬롯 사건 수의 상한({@link SelectionRules#bigSeats})
	 */
	public static BigSlots pick(List<BigRow> rows, int bigSlots, int cap) {
		Objects.requireNonNull(rows, "rows");
		if (bigSlots <= 0 || cap <= 0) {
			return BigSlots.NONE;
		}
		Set<EventId> usedEvents = new HashSet<>();
		List<List<CandidateEvent>> chosen = new ArrayList<>();
		List<Integer> chosenRows = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			BigRow row = rows.get(index);
			if (chosen.size() == bigSlots) {
				break;
			}
			List<CandidateEvent> picked = new ArrayList<>();
			for (BigEvent event : row.events().stream().sorted(EVENT_ORDER).toList()) {
				if (picked.size() == SelectionRules.BIG_EVENTS_PER_STORYLINE) {
					break;
				}
				CandidateEvent candidate = event.candidate();
				if (candidate == null || !usedEvents.add(event.id())) {
					continue;
				}
				picked.add(candidate);
			}
			if (!picked.isEmpty()) {
				chosen.add(picked);
				chosenRows.add(index);
			}
		}
		return trim(chosen, chosenRows, cap);
	}

	/**
	 * 상한을 넘으면 행 순서로 돌며 하나씩 담는다 — 결과는 행 순서 · 행 안 사건 순서
	 * @param chosenRows {@code chosen} 의 행마다 입력 행의 위치
	 */
	private static BigSlots trim(List<List<CandidateEvent>> chosen, List<Integer> chosenRows, int cap) {
		int[] keep = new int[chosen.size()];
		int kept = 0;
		boolean more = true;
		while (more && kept < cap) {
			more = false;
			for (int i = 0; i < chosen.size() && kept < cap; i++) {
				if (keep[i] < chosen.get(i).size()) {
					keep[i]++;
					kept++;
					more = true;
				}
			}
		}
		List<CandidateEvent> events = new ArrayList<>(kept);
		List<Integer> rows = new ArrayList<>(kept);
		for (int i = 0; i < chosen.size(); i++) {
			for (CandidateEvent event : chosen.get(i).subList(0, keep[i])) {
				events.add(event);
				rows.add(chosenRows.get(i));
			}
		}
		return new BigSlots(events, rows);
	}

	/**
	 * 큰 판정 행 하나 — 그 스토리라인의 기간 안 사건
	 * @param events 기간 안 기사가 있는 사건 — 순서 무관
	 */
	public record BigRow(List<BigEvent> events) {

		public BigRow {
			events = List.copyOf(Objects.requireNonNull(events, "events"));
		}

	}

	/**
	 * 큰 판정 행의 사건 하나
	 * @param id 사건
	 * @param importanceSum 기간 안 기사의 중요도 합
	 * @param candidate 그 사용자의 후보 사건({@link CandidateEvents#big}) — 기사 제외를 지난 기간 안 기사가 없거나 사건의 지역이 제외와
	 *     겹치면 null
	 */
	public record BigEvent(EventId id, BigDecimal importanceSum, @Nullable CandidateEvent candidate) {

		/**
		 * @throws IllegalArgumentException 후보 사건이 이 사건이 아닐 때
		 */
		public BigEvent {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(importanceSum, "importanceSum");
			if (candidate != null && !candidate.facts().id().equals(id)) {
				throw new IllegalArgumentException(
						"후보 사건 " + candidate.facts().id().value() + " 이 큰 판정 행의 사건 " + id.value() + " 이 아니다");
			}
		}

	}

	/**
	 * 고른 큰 슬롯
	 * @param events 큰 슬롯 사건 — 행 순서, 행 안에서는 사건 순서
	 * @param rows {@code events} 와 같은 순서로 각 사건이 나온 큰 판정 행의 위치({@link #pick} 의 입력 {@code rows} 의 위치)
	 */
	public record BigSlots(List<CandidateEvent> events, List<Integer> rows) {

		/** 큰 슬롯 없음 */
		public static final BigSlots NONE = new BigSlots(List.of(), List.of());

		/**
		 * @throws IllegalArgumentException 행 위치가 사건마다 하나가 아닐 때
		 */
		public BigSlots {
			events = List.copyOf(Objects.requireNonNull(events, "events"));
			rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
			if (rows.size() != events.size()) {
				throw new IllegalArgumentException("행 위치가 사건마다 하나가 아니다: " + events.size() + " · " + rows.size());
			}
		}

	}

}
