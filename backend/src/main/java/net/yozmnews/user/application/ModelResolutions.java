package net.yozmnews.user.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.credential.LlmKeys;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.LlmModels;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.interest.ModelChoice;
import net.yozmnews.user.domain.interest.ModelRef;
import net.yozmnews.user.domain.interest.ModelResolution;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 쓸 모델 판정의 재료 읽기 — 허용 모델 목록과 그 사용자의 키가 있는 공급자 · 쓸 수 있는 키의 공급자. docs/specs/user.md §4 · user-R51
 * <p>
 * 관심 설정 화면의 모델 조회({@link ModelSelection})와 다이제스트 · 리포트가 읽는 쓸 모델({@link UsableModels})이 함께 쓴다 — 판정은
 * {@link ModelChoice} 하나다.
 */
@Component
class ModelResolutions {

	private final LlmModels models;

	private final LlmKeys keys;

	ModelResolutions(LlmModels models, LlmKeys keys) {
		this.models = Objects.requireNonNull(models, "models");
		this.keys = Objects.requireNonNull(keys, "keys");
	}

	/** 그 사용자의 판정 재료를 읽는다 */
	Materials materials(UserId user) {
		Objects.requireNonNull(user, "user");
		return new Materials(this.models.all(), this.keys.providers(user), this.keys.usable(user));
	}

	/**
	 * 쓸 모델 판정의 재료.
	 * @param allowed 허용 모델 목록 — 공급자 선언 순, 공급자 안에서는 파일에 적힌 순
	 * @param withKey 키가 있는 공급자(상태 무관)
	 * @param usable 쓸 수 있는 키({@code VALID})의 공급자
	 */
	record Materials(List<LlmModel> allowed, Set<Provider> withKey, Set<Provider> usable) {

		Materials {
			allowed = List.copyOf(allowed);
			withKey = Set.copyOf(withKey);
			usable = Set.copyOf(usable);
		}

		/** 고른 모델로 쓸 모델을 정한다 */
		ModelResolution resolve(@Nullable ModelRef chosen) {
			return ModelChoice.resolve(chosen, this.allowed, this.withKey, this.usable);
		}

		/** 고를 수 있는 모델 — 키가 있는 공급자의 허용 모델, 목록 순 */
		List<LlmModel> choosable() {
			return this.allowed.stream().filter(model -> this.withKey.contains(model.provider())).toList();
		}

	}

}
