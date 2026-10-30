package net.yozmnews.source.domain.registry;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileProblem;

/**
 * 소스의 수집 프로필을 얻은 결과 — 프리셋은 배포로, 운영자 원문은 검증 규칙의 변경으로 가리킬 곳을 잃을 수 있다
 */
public sealed interface ProfileResolution {

	/** 프로필을 얻었다 */
	record Resolved(CollectionProfile profile) implements ProfileResolution {

		public Resolved {
			Objects.requireNonNull(profile, "profile");
		}

	}

	/** 소스가 참조하는 프리셋이 카탈로그에 없다 — 프리셋 파일이 배포에서 빠졌다 */
	record PresetMissing(PresetKey key) implements ProfileResolution {

		public PresetMissing {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 저장된 운영자 원문이 지금의 검증기를 통과하지 못한다 */
	record Invalid(List<ProfileProblem> problems) implements ProfileResolution {

		public Invalid {
			problems = List.copyOf(problems);
		}

	}

}
