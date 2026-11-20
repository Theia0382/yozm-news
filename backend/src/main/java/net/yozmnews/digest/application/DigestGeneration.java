package net.yozmnews.digest.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.content.DigestSection;
import net.yozmnews.digest.domain.content.DigestSectionRepository;
import net.yozmnews.digest.domain.digest.Completion;
import net.yozmnews.digest.domain.digest.Digest;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.digest.domain.digest.ScheduledDigest;
import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GenerationOutcome;
import net.yozmnews.digest.domain.generator.GenerationVerdict;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Retry;
import net.yozmnews.digest.domain.generator.GenerationVerdict.RetryMissing;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Settled;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.output.CitationCheck;
import net.yozmnews.digest.domain.output.CitationResult;
import net.yozmnews.digest.domain.output.OutputRead;
import net.yozmnews.digest.domain.prompt.DigestPrompt;
import net.yozmnews.digest.domain.prompt.DigestPrompts;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다이제스트 생성 유스케이스 — docs/specs/digest.md §4 · digest-R2 · digest-R8 · digest-R9
 * <p>
 * 시작(트랜잭션 ①) · 프롬프트 구성 · 생성기 호출과 읽기 · 다시 묻기(트랜잭션 밖) · 항목 대조 · 저장(트랜잭션 ②) 셋으로 나눈다. 판단(읽을지 ·
 * 다시 물을지 · 실패 코드)은 {@link GenerationVerdict} 가 한다. 두 트랜잭션은 다이제스트 행을 먼저, 후보 행을 다음에 잠근다. 트랜잭션 ① 은
 * 생성을 기다리는 다이제스트만 시작한다 — 생성 중인 다이제스트를 다시 시작하지 않아 같은 다이제스트의 생성기 호출은 하나다. 트랜잭션 ② 는
 * 다이제스트의 버전이 트랜잭션 ① 뒤의 값과 다르거나 이미 끝났으면 아무것도 쓰지 않는다. 끝낸 행은 {@link DigestEndings} 로 저장한다. 생성기는 어느 프로세스에나 하나다 — {@code local}
 * 은 로컬 대체물, 그 밖은 실제 생성기(ADR-0187). 트랜잭션 안에서 부르면 거부한다.
 */
@Service
public class DigestGeneration {

	/** 인용이 남지 않았을 때의 실패 메시지 */
	static final String NO_CITATION = "검증 뒤 인용이 하나도 남지 않았다";

	/** 트랜잭션 안에서 불렀을 때의 예외 메시지 */
	static final String IN_TRANSACTION = "다이제스트 생성을 트랜잭션 안에서 불렀다 — 생성기 호출은 트랜잭션 밖에서 한다";

	private static final Logger logger = LoggerFactory.getLogger(DigestGeneration.class);

	private final DigestRepository digests;

	private final DigestSectionRepository sections;

	private final DigestCandidateRepository candidates;

	private final ArticleDirectory articles;

	private final DigestGenerator generator;

	private final DigestEndings endings;

	private final TransactionTemplate transaction;

	private final AppClock clock;

	DigestGeneration(DigestRepository digests, DigestSectionRepository sections, DigestCandidateRepository candidates,
			ArticleDirectory articles, DigestGenerator generator, DigestEndings endings,
			TransactionTemplate transaction, AppClock clock) {
		this.digests = digests;
		this.sections = sections;
		this.candidates = candidates;
		this.articles = articles;
		this.generator = Objects.requireNonNull(generator, "generator");
		this.endings = Objects.requireNonNull(endings, "endings");
		this.transaction = transaction;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 생성을 기다리는 다이제스트를 새 등록 토큰과 함께 만든다 — 생성 작업의 등록은 커밋 뒤 부르는 쪽이 한다. 같은 사용자 · 날짜의 일정
	 * 다이제스트가 이미 있으면 저장소의 유일 제약이 거부한다.
	 */
	public ScheduledDigest schedule(UserId user, LocalDate digestDate, DigestPeriod period, DigestTrigger trigger) {
		UUID token = UUID.randomUUID();
		Digest saved = this.digests.save(Digest.schedule(user, digestDate, period, trigger, token, this.clock.instant()));
		return new ScheduledDigest(saved.id(), token);
	}

	/**
	 * 시작 → 후보가 있으면 프롬프트 구성 · 생성 · 읽기(끝까지 쓴 응답을 읽지 못하거나 칸이 빠진 항목이 있으면 한 번 다시 묻기) → 항목 대조 → 저장. 끝났거나 생성 중인
	 * 다이제스트면 아무것도 하지 않는다 — 생성 중인 다이제스트를 다시 시작하지 않는다. 생성과 다시 묻기는 트랜잭션 밖이다. 토큰은 공급자가 답한 호출의 합이고, 소요 시간은 첫 호출 시작부터 마지막 호출 끝까지다.
	 * <p>
	 * 후보 선별과 생성은 같은 모델을 쓴다 — 쓸 모델은 그 입력을 고른 선별에 넘긴 모델이어야 하고, 입력 토큰 예산은 선별이 그 모델의 예산으로 자른 것이고 여기서 다시 자르지 않는다.
	 * @param input 후보 선별이 낸 생성의 입력
	 * @return 이 실행 뒤 그 다이제스트의 상태 — 다른 실행이 생성 중이면 {@link DigestStatus#RUNNING}
	 * @throws IllegalArgumentException 없는 다이제스트일 때
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 아무것도 쓰지 않는다
	 */
	public DigestStatus run(DigestId id, LlmModel model, DigestInput input) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(input, "input");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		CandidateSet set = CandidateSet.of(input);
		Started started = Objects.requireNonNull(this.transaction.execute(status -> start(id, model, set)));
		if (started.finished() != null) {
			return started.finished();
		}
		DigestPrompt prompt = DigestPrompts.compose(input, set, model);
		ReasoningEffort effort = input.profile().effort();
		Instant asked = this.clock.instant();
		GenerationOutcome first = this.generator.generate(new DigestRequest(started.user(), model, effort, set, prompt));
		GenerationOutcome second = null;
		Settled settled = switch (GenerationVerdict.judge(first, set)) {
			case Settled done -> done;
			case Retry retry -> {
				second = this.generator.generate(new DigestRequest(started.user(), model, effort, set,
						DigestPrompts.retry(prompt, retry.diagnosis())));
				yield GenerationVerdict.settle(second, set);
			}
			case RetryMissing retry -> {
				second = this.generator.generate(new DigestRequest(started.user(), model, effort, set,
						DigestPrompts.retry(prompt, retry.diagnosis())));
				yield GenerationVerdict.settle(second, set, retry.first());
			}
		};
		TokenUsage usage = sum(usage(first), (second == null) ? null : usage(second));
		Duration took = this.clock.elapsedSince(asked);
		return finish(id, started.version(), () -> complete(id, set, settled, usage, took));
	}

	/** 공급자가 센 토큰 — 공급자가 답하지 못한 호출은 없다 */
	private static @Nullable TokenUsage usage(GenerationOutcome outcome) {
		return switch (outcome) {
			case GenerationOutcome.Generated generated -> generated.usage();
			case GenerationOutcome.Failed failed -> null;
		};
	}

	/** 두 호출의 토큰 합 — 한쪽만 있으면 그쪽, 둘 다 없으면 없음 */
	private static @Nullable TokenUsage sum(@Nullable TokenUsage a, @Nullable TokenUsage b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return new TokenUsage(a.inputTokens() + b.inputTokens(), a.outputTokens() + b.outputTokens());
	}

	private Started start(DigestId id, LlmModel model, CandidateSet set) {
		Digest digest = lock(id);
		if (digest.status() != DigestStatus.SCHEDULED) {
			// 끝났거나 다른 실행이 생성 중이다 — 다시 시작하지 않는다
			return new Started(digest.status(), digest.userId(), 0);
		}
		Digest running = this.digests.save(digest.start(model, set.eventCount(), this.clock.instant()));
		this.candidates.replace(id, set);
		if (set.isEmpty()) {
			Digest empty = this.endings.save(running.complete(new Completion.Empty(), this.clock.instant()));
			return new Started(empty.status(), empty.userId(), 0);
		}
		return new Started(null, running.userId(), running.version().orElseThrow());
	}

	/** 다이제스트 행을 잠그고, 시작 뒤 그대로면 끝낸다 — 끝내는 방법은 잠근 뒤에 정한다 */
	private DigestStatus finish(DigestId id, long version, Supplier<Completion> completion) {
		return Objects.requireNonNull(this.transaction.execute(status -> {
			Digest digest = lock(id);
			if (digest.status().finished() || !digest.version().equals(Optional.of(version))) {
				return digest.status();
			}
			return this.endings.save(digest.complete(completion.get(), this.clock.instant())).status();
		}));
	}

	/**
	 * 트랜잭션 ② 안에서 — 후보 행 잠금 · 지운 기사 거르기 · 판정이 읽음이면 항목 대조 · 내용 저장, 실패면 그 코드
	 * @param usage 공급자가 답한 호출의 토큰 합 — 읽음이면 언제나 있다(끝까지 쓴 응답에서만 읽는다)
	 */
	private Completion complete(DigestId id, CandidateSet set, Settled settled, @Nullable TokenUsage usage,
			Duration took) {
		Set<ArticleId> kept = this.candidates.lockArticles(id);
		Map<ArticleId, ArticleContent> found = this.articles.findAll(kept);
		Set<ArticleId> present = new HashSet<>(found.keySet());
		present.retainAll(kept);
		Set<ArticleId> gone = new HashSet<>(kept);
		gone.removeAll(present);
		this.candidates.delete(id, gone);
		return switch (settled) {
			case GenerationVerdict.Fail fail -> new Completion.Failed(fail.failure(), fail.message(), usage, took);
			case GenerationVerdict.Read verdict -> {
				OutputRead.Read read = verdict.output();
				TokenUsage spent = Objects.requireNonNull(usage, "읽은 응답은 공급자가 센 토큰이 있다");
				Map<ArticleId, String> titles = new HashMap<>();
				present.forEach(article -> titles.put(article, found.get(article).title()));
				CitationResult cited = CitationCheck.check(read.draft(), set.retain(present), titles);
				int substituted = cited.drops().substituted();
				if (verdict.firstMissing() > 0 || substituted > 0) {
					logger.warn("다이제스트 출력에 항목이 빠졌다 — 다이제스트 {}, 처음 읽은 출력에 없던 항목 {}, 대체한 항목 {}", id.value(),
							verdict.firstMissing(), substituted);
				}
				else if (read.overLimit() > 0) {
					logger.info("다이제스트 출력에서 상한으로 지운 것이 있다 — 다이제스트 {}, 상한으로 버린 수 {}", id.value(), read.overLimit());
				}
				yield switch (cited) {
					case CitationResult.NoCitation none -> new Completion.Failed(DigestFailure.NO_CITATION,
							NO_CITATION, spent, took);
					case CitationResult.Cited ok -> {
						this.sections.saveAll(DigestSection.of(id, ok.digest()));
						yield new Completion.Ready(spent, took, verdict.firstMissing(), substituted);
					}
				};
			}
		};
	}

	private Digest lock(DigestId id) {
		return this.digests.lock(id.value())
			.orElseThrow(() -> new IllegalArgumentException("없는 다이제스트다: " + id.value()));
	}

	/** 트랜잭션 ① 의 결과 — 끝났거나 다른 실행이 생성 중이면 그 상태, 아니면 생성기를 부를 사용자와 시작 뒤 버전 */
	private record Started(@Nullable DigestStatus finished, UserId user, long version) {
	}

}
