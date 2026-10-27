package net.yozmnews.source.domain.profile.text;

import java.util.Objects;

import net.yozmnews.source.domain.profile.ProfileProblem;

/**
 * 프로필 텍스트를 읽은 결과.
 */
public sealed interface ReadResult {

	/** 최상위가 매핑인 문서 하나를 읽었다 */
	record Read(ProfileTree.MapNode root) implements ReadResult {

		public Read {
			Objects.requireNonNull(root, "root");
		}

	}

	/** 트리로 옮길 수 없다 — 너무 길거나, 문법이 틀렸거나, 최상위가 매핑이 아니거나, 문서가 둘 이상이다 */
	record Unreadable(ProfileProblem problem) implements ReadResult {

		public Unreadable {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
