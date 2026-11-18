package net.yozmnews.storyline.domain.big;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.domain.relation.RelationGraph;

/**
 * 큰 스토리라인을 포함 나무마다 한 행으로 모으는 규칙의 정본 — docs/specs/storyline.md §5 storyline-R19.
 * <p>
 * 포함의 상위는 하위의 사건까지 세므로 상위와 하위가 함께 크면 같은 사안이 두 번 나온다. 큰 스토리라인마다 조상 가운데 큰 것의 가장
 * 위를 머리로 삼고(없으면 자기 자신), 머리마다 한 행을 내고 나머지를 그 행의 {@code branches} 에 싣는다. 이어짐은 나무가 아니다.
 * 큰 판정은 바꾸지 않는다 — 무엇이 큰가는 {@link BigStorylineRule} 이 정한다.
 */
public final class BigTrees {

	private BigTrees() {
	}

	/**
	 * @param big 큰 스토리라인 — 아래 행이 없는 것
	 * @param graph 지금 관계
	 * @return 머리마다 한 행 — 행과 그 {@code branches} 모두 {@link BigStorylineRule#ORDER}
	 */
	public static List<BigStoryline> gather(List<BigStoryline> big, RelationGraph graph) {
		Set<Long> ids = big.stream().map(one -> one.storyline().id().value()).collect(Collectors.toSet());
		Map<Long, List<BigStoryline>> under = new LinkedHashMap<>();
		List<BigStoryline> heads = new ArrayList<>();
		for (BigStoryline one : big) {
			long id = one.storyline().id().value();
			// 조상은 가까운 순이다 — 큰 조상 가운데 마지막이 가장 위다
			List<Long> bigAncestors = graph.ancestors(id).stream().filter(ids::contains).toList();
			if (bigAncestors.isEmpty()) {
				heads.add(one);
			}
			else {
				under.computeIfAbsent(bigAncestors.getLast(), head -> new ArrayList<>()).add(one);
			}
		}
		List<BigStoryline> rows = new ArrayList<>();
		for (BigStoryline head : heads) {
			List<BigStoryline> branches = new ArrayList<>(under.getOrDefault(head.storyline().id().value(), List.of()));
			branches.sort(BigStorylineRule.ORDER);
			rows.add(new BigStoryline(head.storyline(), head.tally(), head.intensity(), branches));
		}
		rows.sort(BigStorylineRule.ORDER);
		return List.copyOf(rows);
	}

}
