package net.yozmnews.user.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.ReportModel;
import net.yozmnews.user.ReportModelDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ReportModelDirectory} 의 구현 — 쓸 모델과 추론 강도는 {@link UsableModels} 가 정한다. docs/specs/user.md §4 · user-R51 ·
 * user-R59
 * <p>
 * 쓰지 않는다. 역할 애너테이션이 없다.
 */
@Service
class ReportModelDirectoryService implements ReportModelDirectory {

	private final UsableModels models;

	ReportModelDirectoryService(UsableModels models) {
		this.models = Objects.requireNonNull(models, "models");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ReportModel> modelOf(UserId user) {
		return this.models.of(user).map(usable -> new ReportModel(usable.model(), usable.effort()));
	}

}
