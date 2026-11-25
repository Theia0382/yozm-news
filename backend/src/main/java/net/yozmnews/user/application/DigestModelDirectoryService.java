package net.yozmnews.user.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestModelDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DigestModelDirectory} 의 구현 — 쓸 모델은 {@link UsableModels} 가 정한다. docs/specs/user.md §4 · user-R51
 * <p>
 * 쓰지 않는다. 역할 애너테이션이 없다.
 */
@Service
class DigestModelDirectoryService implements DigestModelDirectory {

	private final UsableModels models;

	DigestModelDirectoryService(UsableModels models) {
		this.models = Objects.requireNonNull(models, "models");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LlmModel> modelOf(UserId user) {
		return this.models.of(user).map(UsableModels.Usable::model);
	}

}
