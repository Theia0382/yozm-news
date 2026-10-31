package net.yozmnews.source;

import java.util.List;

/**
 * 운영자 원문 수정의 결과 — docs/specs/source.md §3
 */
public sealed interface ProfileRevisionResult {

	/** 고쳤다 — 읽기 설정이 바뀌어 끈 피드의 식별자(피드 ID 순), 검토일이 사라져 소스를 껐는지, 새로 더한 꺼진 피드의 식별자(원문 순) */
	record Revised(List<String> disabledFeedKeys, boolean sourceDisabled,
			List<String> addedFeedKeys) implements ProfileRevisionResult {

		public Revised {
			disabledFeedKeys = List.copyOf(disabledFeedKeys);
			addedFeedKeys = List.copyOf(addedFeedKeys);
		}

	}

	/** 새 원문이 검증을 통과하지 못해 아무것도 바꾸지 않았다 */
	record InvalidProfile(List<ProfileProblemView> problems) implements ProfileRevisionResult {

		public InvalidProfile {
			problems = List.copyOf(problems);
		}

	}

	/** 프리셋을 참조하는 소스라 원문이 없다 */
	record NotOperatorProfile() implements ProfileRevisionResult {
	}

}
