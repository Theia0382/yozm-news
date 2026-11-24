package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.springframework.stereotype.Component;

/**
 * 사용자 하나의 쓸 모델 정하기 — 쓰는 계정이면 고른 모델 · 허용 목록 · 키 상태로 쓸 모델을 정하고 설정 행의 추론 강도를 함께 낸다. 다이제스트 ·
 * 리포트의 쓸 모델 조회가 함께 쓴다. docs/specs/user.md §4 · user-R51 · user-R59 · user-R61
 * <p>
 * 설정 행이 없으면 계정을 만들 때의 값({@link InterestSettings#defaults})으로 본다. 판정은 {@link ModelResolutions} 와
 * {@code ModelChoice} 가 한다. 부르는 쪽의 트랜잭션에서 읽는다. 쓰지 않는다.
 */
@Component
class UsableModels {

	private final AccountDirectory accounts;

	private final InterestSettingsRepository settings;

	private final TagSetDirectory tagSets;

	private final ModelResolutions resolutions;

	UsableModels(AccountDirectory accounts, InterestSettingsRepository settings, TagSetDirectory tagSets,
			ModelResolutions resolutions) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.resolutions = Objects.requireNonNull(resolutions, "resolutions");
	}

	/** 그 사용자의 쓸 모델과 추론 강도 — 계정이 없거나 쓰는 계정이 아니거나 쓸 모델이 없으면 빈 값 */
	Optional<Usable> of(UserId user) {
		Objects.requireNonNull(user, "user");
		if (!this.accounts.active(user)) {
			return Optional.empty();
		}
		// 설정 행이 없으면 계정을 만들 때의 값 — InterestDefaultsWriter 와 같은 정본. 고친 시각은 읽지 않는다
		InterestSettings stored = this.settings.findByUser(user)
			.orElseGet(() -> InterestSettings.defaults(user, this.tagSets.activeTagSet().id(), Instant.EPOCH));
		return Optional.ofNullable(this.resolutions.materials(user).resolve(stored.model()).effective())
			.map(model -> new Usable(model, stored.effort()));
	}

	/**
	 * 쓸 모델과 추론 강도.
	 * @param model 쓸 모델
	 * @param effort 설정 행의 추론 강도
	 */
	record Usable(LlmModel model, ReasoningEffort effort) {
	}

}
