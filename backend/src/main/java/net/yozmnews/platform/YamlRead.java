package net.yozmnews.platform;

import java.util.Objects;

/**
 * YAML 원문을 읽은 결과 — docs/specs/platform.md §3 · platform-R17
 */
public sealed interface YamlRead {

	/** 최상위가 매핑인 문서 하나를 읽었다 */
	record Read(YamlTree.MapNode root) implements YamlRead {

		public Read {
			Objects.requireNonNull(root, "root");
		}

	}

	/** 트리로 옮길 수 없다 — 너무 길거나, 문법이 틀렸거나, 최상위가 매핑이 아니거나, 문서가 둘 이상이다 */
	record Unreadable(YamlProblem problem) implements YamlRead {

		public Unreadable {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
