package net.yozmnews.user.domain.interest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import org.jspecify.annotations.Nullable;

/**
 * 쓸 모델 판정의 정본 — docs/specs/user.md §5 · user-R51
 * <p>
 * 고른 모델이 허용 목록에 있고 그 공급자의 키가 쓸 수 있으면({@code VALID}) 그것, 아니면 쓸 수 있는 키가 있는 공급자(선언 순)의 허용 목록 첫
 * 모델, 그것도 없으면 쓸 모델이 없다.
 */
public final class ModelChoice {

	private ModelChoice() {
	}

	/**
	 * @param chosen 고른 모델 — 고르지 않았으면 null
	 * @param allowed 허용 모델 목록 — 공급자 선언 순, 공급자 안에서는 파일에 적힌 순
	 * @param withKey 키가 있는 공급자(상태 무관)
	 * @param usable 쓸 수 있는 키({@code VALID})의 공급자
	 */
	public static ModelResolution resolve(@Nullable ModelRef chosen, List<LlmModel> allowed, Set<Provider> withKey,
			Set<Provider> usable) {
		Objects.requireNonNull(allowed, "allowed");
		Objects.requireNonNull(withKey, "withKey");
		Objects.requireNonNull(usable, "usable");
		if (chosen == null) {
			return new ModelResolution(null, fallback(allowed, usable), ModelReason.NOT_CHOSEN);
		}
		Optional<LlmModel> listed = allowed.stream()
			.filter(model -> model.provider() == chosen.provider() && model.id().equals(chosen.modelId()))
			.findFirst();
		if (listed.isPresent() && usable.contains(chosen.provider())) {
			return new ModelResolution(chosen, listed.get(), ModelReason.CHOSEN);
		}
		ModelReason reason;
		if (listed.isEmpty()) {
			reason = ModelReason.NOT_ALLOWED;
		}
		else if (!withKey.contains(chosen.provider())) {
			reason = ModelReason.NO_KEY;
		}
		else {
			reason = ModelReason.KEY_INVALID;
		}
		return new ModelResolution(chosen, fallback(allowed, usable), reason);
	}

	/** 쓸 수 있는 키가 있는 공급자(선언 순)의 허용 목록 첫 모델 */
	private static @Nullable LlmModel fallback(List<LlmModel> allowed, Set<Provider> usable) {
		for (Provider provider : Provider.values()) {
			if (!usable.contains(provider)) {
				continue;
			}
			for (LlmModel model : allowed) {
				if (model.provider() == provider) {
					return model;
				}
			}
		}
		return null;
	}

}
