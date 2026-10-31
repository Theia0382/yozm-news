package net.yozmnews.source;

import java.util.List;
import java.util.Objects;

/**
 * 소스 등록의 결과 — docs/specs/source.md §3
 */
public sealed interface SourceRegistrationResult {

	/** 등록했다 — 소스와 피드는 꺼진 채다 */
	record Registered(SourceId id) implements SourceRegistrationResult {

		public Registered {
			Objects.requireNonNull(id, "id");
		}

	}

	/** 카탈로그에 없는 프리셋이다(키 형식이 틀린 경우 포함) */
	record PresetNotFound(String key) implements SourceRegistrationResult {

		public PresetNotFound {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 그 프리셋을 참조하는 소스가 이미 있다 */
	record PresetAlreadyRegistered(String key) implements SourceRegistrationResult {

		public PresetAlreadyRegistered {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 운영자 원문이 검증을 통과하지 못했다 */
	record InvalidProfile(List<ProfileProblemView> problems) implements SourceRegistrationResult {

		public InvalidProfile {
			problems = List.copyOf(problems);
		}

	}

}
