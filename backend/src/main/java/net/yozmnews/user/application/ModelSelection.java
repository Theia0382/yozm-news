package net.yozmnews.user.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.credential.LlmKeys;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.LlmModels;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.interest.ModelChoice;
import net.yozmnews.user.domain.interest.ModelRef;
import net.yozmnews.user.domain.interest.ModelResolution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공급자 · 모델 고르기와 쓸 모델 조회, 추론 강도 — docs/specs/user.md §4 · user-R51 · user-R59
 * <p>
 * 고르기는 키가 있는(상태 무관) 공급자의 허용 모델만 받는다. 쓸 모델은 조회 때 {@link ModelChoice} 로 정한다. 추론 강도는 고른 모델 ·
 * 키와 무관하게 저장한다.
 */
@Service
public class ModelSelection {

	private final InterestSettingsLock lock;

	private final InterestSettingsRepository settings;

	private final LlmModels models;

	private final LlmKeys keys;

	ModelSelection(InterestSettingsLock lock, InterestSettingsRepository settings, LlmModels models, LlmKeys keys) {
		this.lock = Objects.requireNonNull(lock, "lock");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.models = Objects.requireNonNull(models, "models");
		this.keys = Objects.requireNonNull(keys, "keys");
	}

	@Transactional
	public ModelView view(UserId user, Instant now) {
		return view(user, this.lock.lockFollowed(user, now));
	}

	@Transactional
	public ModelSelectionOutcome choose(UserId user, Provider provider, String modelId, Instant now) {
		InterestSettings locked = this.lock.lockFollowed(user, now);
		if (!this.keys.providers(user).contains(provider)) {
			return new ModelSelectionOutcome.ProviderWithoutKey();
		}
		if (this.models.find(provider, modelId).isEmpty()) {
			return new ModelSelectionOutcome.ModelNotAllowed();
		}
		InterestSettings changed = locked.withModel(new ModelRef(provider, modelId), now);
		this.settings.update(changed);
		return new ModelSelectionOutcome.Saved(view(user, changed));
	}

	/** 추론 강도를 바꾼다 — 통째로 바꾸고 이력이 없다 */
	@Transactional
	public ModelView saveEffort(UserId user, ReasoningEffort effort, Instant now) {
		Objects.requireNonNull(effort, "effort");
		InterestSettings changed = this.lock.lockFollowed(user, now).withEffort(effort, now);
		this.settings.update(changed);
		return view(user, changed);
	}

	private ModelView view(UserId user, InterestSettings settings) {
		Set<Provider> withKey = this.keys.providers(user);
		Set<Provider> usable = this.keys.usable(user);
		List<LlmModel> allowed = this.models.all();
		List<LlmModel> choosable = allowed.stream().filter(model -> withKey.contains(model.provider())).toList();
		return new ModelView(choosable, ModelChoice.resolve(settings.model(), allowed, withKey, usable),
				settings.effort());
	}

	/**
	 * 고를 수 있는 모델(키가 있는 공급자의 허용 모델 — 목록 순)과 쓸 모델의 판정, 추론 강도.
	 */
	public record ModelView(List<LlmModel> choosable, ModelResolution resolution, ReasoningEffort effort) {
	}

	/** 고르기의 결과 */
	public sealed interface ModelSelectionOutcome {

		/** 골랐다 */
		record Saved(ModelView model) implements ModelSelectionOutcome {
		}

		/** 그 공급자에 키가 없다 — 바꾸지 않았다 */
		record ProviderWithoutKey() implements ModelSelectionOutcome {
		}

		/** 허용 목록에 없는 모델이다 — 바꾸지 않았다 */
		record ModelNotAllowed() implements ModelSelectionOutcome {
		}

	}

}
