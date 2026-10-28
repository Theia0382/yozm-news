package net.yozmnews.source.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileProblem;

/** 소스 등록의 결과. */
sealed interface Registration {

	/** 등록했다 — 소스와 프로필의 피드는 모두 꺼진 채다 */
	record Registered(SourceId id) implements Registration {

		public Registered {
			Objects.requireNonNull(id, "id");
		}

	}

	/** 카탈로그에 없는 프리셋이다 */
	record PresetNotFound(PresetKey key) implements Registration {

		public PresetNotFound {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 그 프리셋을 참조하는 소스가 이미 있다 */
	record PresetAlreadyRegistered(PresetKey key) implements Registration {

		public PresetAlreadyRegistered {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 운영자 프로필 원문이 검증을 통과하지 못했다 */
	record InvalidProfile(List<ProfileProblem> problems) implements Registration {

		public InvalidProfile {
			problems = List.copyOf(problems);
		}

	}

}
