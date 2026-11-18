package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountStatus;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.interest.ModelRef;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DigestModelDirectory} 의 구현 — 쓰는 계정이면 고른 모델 · 허용 목록 · 키 상태로 쓸 모델을 정한다. docs/specs/user.md §4 ·
 * user-R51
 * <p>
 * 쓰지 않는다. 설정 행이 없으면 계정을 만들 때의 값({@link InterestSettings#defaults})의 고른 모델로 본다. 역할 애너테이션이 없다.
 */
@Service
class DigestModelDirectoryService implements DigestModelDirectory {

	private final AccountRepository accounts;

	private final InterestSettingsRepository settings;

	private final TagSetDirectory tagSets;

	private final ModelResolutions resolutions;

	DigestModelDirectoryService(AccountRepository accounts, InterestSettingsRepository settings,
			TagSetDirectory tagSets, ModelResolutions resolutions) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.resolutions = Objects.requireNonNull(resolutions, "resolutions");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LlmModel> modelOf(UserId user) {
		Objects.requireNonNull(user, "user");
		boolean active = this.accounts.findById(user.value())
			.map(account -> account.status() == AccountStatus.ACTIVE)
			.orElse(false);
		if (!active) {
			return Optional.empty();
		}
		// 설정 행이 없으면 계정을 만들 때의 값 — InterestDefaultsWriter 와 같은 정본. 고친 시각은 읽지 않는다
		InterestSettings stored = this.settings.findByUser(user)
			.orElseGet(() -> InterestSettings.defaults(user, this.tagSets.activeTagSet().id(), Instant.EPOCH));
		@Nullable ModelRef chosen = stored.model();
		return Optional.ofNullable(this.resolutions.materials(user).resolve(chosen).effective());
	}

}
