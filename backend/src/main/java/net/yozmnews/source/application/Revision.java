package net.yozmnews.source.application;

import java.util.List;

import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.ProfileProblem;

/** 운영자 원문 수정의 결과 */
sealed interface Revision {

	/**
	 * 고쳤다.
	 * @param disabledFeeds 읽기 설정이 바뀌어 끈 피드의 식별자 — 피드 ID 순
	 * @param sourceDisabled 이용조건 검토일이 사라져 소스를 껐다
	 * @param addedFeeds 원문에 새로 생겨 꺼진 채 더한 피드의 식별자 — 원문의 피드 순
	 */
	record Revised(List<FeedKey> disabledFeeds, boolean sourceDisabled, List<FeedKey> addedFeeds) implements Revision {

		public Revised {
			disabledFeeds = List.copyOf(disabledFeeds);
			addedFeeds = List.copyOf(addedFeeds);
		}

	}

	/** 새 원문이 검증을 통과하지 못해 아무것도 바꾸지 않았다 */
	record InvalidProfile(List<ProfileProblem> problems) implements Revision {

		public InvalidProfile {
			problems = List.copyOf(problems);
		}

	}

	/** 프리셋을 참조하는 소스라 원문이 없다 */
	record NotOperatorProfile() implements Revision {
	}

}
