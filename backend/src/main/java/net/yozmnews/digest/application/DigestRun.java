package net.yozmnews.digest.application;

import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.domain.digest.Digest;
import net.yozmnews.digest.domain.digest.DigestFailure;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.DigestStatus;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.user.DigestModelDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 생성 작업의 유스케이스 — 다이제스트 행 하나를 읽어 쓸 모델을 정하고, 후보를 고르고, 생성한다. docs/specs/digest.md §4
 * <p>
 * 생성을 기다리는 다이제스트만 진행한다 — 끝났거나 생성 중이면 그 상태를 돌려준다. 쓸 모델이 없거나 계정이 없으면 생성기를 부르지 않고
 * {@link DigestFailure#NO_USABLE_MODEL} 로 끝낸다. 선별과 생성은 같은 모델을 쓴다. 트랜잭션 밖에서 부른다 — 생성이 그렇다. 역할
 * 애너테이션이 없다(부르는 작업이 작업 역할이다).
 */
@Service
public class DigestRun {

	/** 쓸 모델이 없을 때의 실패 메시지 */
	static final String NO_MODEL = "쓸 모델이 없다 — 유효한 키가 있는 공급자의 허용 모델이 없거나 계정을 쓸 수 없다";

	/** 후보를 고르지 못했을 때(계정이 없다)의 실패 메시지 */
	static final String NO_ACCOUNT = "계정이 없어 후보를 고르지 못했다";

	private final DigestRepository digests;

	private final DigestModelDirectory models;

	private final CandidateSelection selection;

	private final DigestGeneration generation;

	private final DigestEndings endings;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	DigestRun(DigestRepository digests, DigestModelDirectory models, CandidateSelection selection,
			DigestGeneration generation, DigestEndings endings, TransactionTemplate transaction) {
		this(digests, models, selection, generation, endings, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	DigestRun(DigestRepository digests, DigestModelDirectory models, CandidateSelection selection,
			DigestGeneration generation, DigestEndings endings, TransactionTemplate transaction, InstantSource clock) {
		this.digests = Objects.requireNonNull(digests, "digests");
		this.models = Objects.requireNonNull(models, "models");
		this.selection = Objects.requireNonNull(selection, "selection");
		this.generation = Objects.requireNonNull(generation, "generation");
		this.endings = Objects.requireNonNull(endings, "endings");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * @return 이 실행 뒤 그 다이제스트의 상태
	 * @throws IllegalArgumentException 없는 다이제스트일 때
	 */
	public DigestStatus run(DigestId id) {
		Objects.requireNonNull(id, "id");
		Digest digest = this.digests.findById(id.value())
			.orElseThrow(() -> new IllegalArgumentException("없는 다이제스트다: " + id.value()));
		if (digest.status() != DigestStatus.SCHEDULED) {
			return digest.status();
		}
		Optional<LlmModel> model = this.models.modelOf(digest.userId());
		if (model.isEmpty()) {
			return abandon(id, NO_MODEL);
		}
		Optional<DigestInput> input = this.selection.select(digest.userId(), digest.period(), model.get());
		if (input.isEmpty()) {
			return abandon(id, NO_ACCOUNT);
		}
		return this.generation.run(id, model.get(), input.get());
	}

	/** 생성을 기다리는 다이제스트면 생성기를 부르지 않고 실패로 끝낸다 — 그사이 다른 실행이 시작했거나 끝냈으면 그 상태 */
	private DigestStatus abandon(DigestId id, String message) {
		return Objects.requireNonNull(this.transaction.execute(status -> {
			Digest locked = this.digests.lock(id.value())
				.orElseThrow(() -> new IllegalArgumentException("없는 다이제스트다: " + id.value()));
			if (locked.status() != DigestStatus.SCHEDULED) {
				return locked.status();
			}
			return this.endings
				.save(locked.abandon(DigestFailure.NO_USABLE_MODEL, message, this.clock.instant()))
				.status();
		}));
	}

}
