package net.yozmnews.evaluation;

import java.util.List;

/**
 * 기준 버전 대비 변경 목록 — docs/specs/evaluation.md §3 · evaluation-R24.
 *
 * @param added 새 버전에만 있고 계보의 새 키가 아닌 키 — 태그 종류 순, 새 버전의 체계 순
 * @param removed 계보 없이 지운 키 — 기준 버전에만 있고 계보의 이전 키가 아닌 키. 태그 종류 순, 기준 버전의 체계 순
 * @param revised 두 버전에 다 있고 이름 · 설명 · 기본 제외 가운데 하나가 다른 키 — 태그 종류 순, 새 버전의 체계 순
 * @param lineage 계보 항목 — 원문 순서
 * @param questionsRevised 질문 틀 · 질문이 바뀌었는가
 * @param importanceRevised 중요도 기준이 바뀌었는가
 * @param thresholds 원문의 태그 기준 · 저장 기준이 기준 버전의 지금 값과 크기가 다른 태그 종류 — 주제 · 지역 순
 */
public record TagSetChanges(List<TagRef> added, List<TagRef> removed, List<TagRef> revised,
		List<TagLineageView> lineage, boolean questionsRevised, boolean importanceRevised,
		List<ThresholdDifference> thresholds) {

	public TagSetChanges {
		added = List.copyOf(added);
		removed = List.copyOf(removed);
		revised = List.copyOf(revised);
		lineage = List.copyOf(lineage);
		thresholds = List.copyOf(thresholds);
	}

}
