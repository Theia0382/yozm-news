package net.yozmnews.report.domain.material;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotUserStoryline;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import org.jspecify.annotations.Nullable;

/**
 * 직전 스냅숏의 값을 지금 대상에 1:1 로 잇는 정본 · docs/specs/report.md §5 · report-R18
 * <p>
 * 1:1 로 풀리지 않는 대상(갈라짐 · 합침 · 지움)은 잇지 않는다 — 그 대상의 직전 값이 없다.
 */
public final class PreviousMatch {

	private PreviousMatch() {
	}

	/**
	 * 주제 — 직전 키 → 기사 수를 활성 키 → 기사 수로.
	 * @param lineage 직전 스냅숏의 버전 → 활성 버전 계보 표 — 같은 버전이면 null(같은 키)
	 * @return 이은 활성 키 → 직전 기사 수
	 */
	public static Map<String, Long> topics(Map<String, Long> previous, @Nullable TagKeyMapping lineage) {
		Objects.requireNonNull(previous, "previous");
		if (lineage == null) {
			return Map.copyOf(previous);
		}
		Map<String, Set<String>> table = lineage.keys().get(TagKind.TOPIC);
		// 활성 키마다 그리로 가는 직전 버전의 키 수 — 스냅숏에 없는 키도 센다
		Map<String, Integer> inbound = new HashMap<>();
		table.values().forEach(targets -> targets.forEach(target -> inbound.merge(target, 1, Integer::sum)));
		Map<String, Long> carried = new LinkedHashMap<>();
		previous.forEach((key, articles) -> {
			Set<String> targets = table.get(key);
			if (targets == null || targets.size() != 1) {
				return;
			}
			String target = targets.iterator().next();
			if (inbound.getOrDefault(target, 0) == 1) {
				carried.put(target, articles);
			}
		});
		return Map.copyOf(carried);
	}

	/**
	 * 사용자 스토리라인 — 직전 사용자 스토리라인의 꼭대기를 지금 꼭대기로 푼 집합이 지금 사용자 스토리라인 하나의 꼭대기와만 겹치고, 그 지금 사용자 스토리라인과 겹치는 직전 사용자 스토리라인이 하나일 때만
	 * 잇는다.
	 * @param resolvedTops 직전 꼭대기 → 지금 포함 나무의 꼭대기(합쳐진 것은 남는 것) — 없는(지운) 것은 빠져 있다
	 * @param currentUserStorylines 지금 사용자 스토리라인의 헤드 → 꼭대기들
	 * @return 지금 사용자 스토리라인의 헤드 → 직전 기사 수
	 */
	public static Map<StorylineId, Long> userStorylines(List<SnapshotUserStoryline> previous, Map<StorylineId, StorylineId> resolvedTops,
			Map<StorylineId, Set<StorylineId>> currentUserStorylines) {
		Objects.requireNonNull(previous, "previous");
		Objects.requireNonNull(resolvedTops, "resolvedTops");
		Objects.requireNonNull(currentUserStorylines, "currentUserStorylines");
		Map<SnapshotUserStoryline, Set<StorylineId>> overlaps = new LinkedHashMap<>();
		Map<StorylineId, Integer> touched = new HashMap<>();
		for (SnapshotUserStoryline userStoryline : previous) {
			Set<StorylineId> resolved = new HashSet<>();
			userStoryline.tops().forEach(top -> {
				StorylineId now = resolvedTops.get(top);
				if (now != null) {
					resolved.add(now);
				}
			});
			Set<StorylineId> heads = new HashSet<>();
			currentUserStorylines.forEach((head, tops) -> {
				if (tops.stream().anyMatch(resolved::contains)) {
					heads.add(head);
				}
			});
			overlaps.put(userStoryline, heads);
			heads.forEach(head -> touched.merge(head, 1, Integer::sum));
		}
		Map<StorylineId, Long> carried = new LinkedHashMap<>();
		overlaps.forEach((userStoryline, heads) -> {
			if (heads.size() == 1) {
				StorylineId head = heads.iterator().next();
				if (touched.get(head) == 1) {
					carried.put(head, userStoryline.articles());
				}
			}
		});
		return Map.copyOf(carried);
	}

}
