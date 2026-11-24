package net.yozmnews.user.application;

import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.StorylineStanding;
import net.yozmnews.user.StorylineStandingNow;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import net.yozmnews.user.domain.summary.SummaryAttempt;
import net.yozmnews.user.domain.summary.SummaryEvent;
import net.yozmnews.user.domain.summary.SummaryFailure;
import net.yozmnews.user.domain.summary.SummaryInput;
import net.yozmnews.user.domain.summary.SummaryOutcome;
import net.yozmnews.user.domain.summary.SummaryOutputReader;
import net.yozmnews.user.domain.summary.SummaryPlan;
import net.yozmnews.user.domain.summary.SummaryPlan.Action;
import net.yozmnews.user.domain.summary.SummaryPlan.Flow;
import net.yozmnews.user.domain.summary.SummaryPrompt;
import net.yozmnews.user.domain.summary.SummaryPrompts;
import net.yozmnews.user.domain.summary.SummaryRead;
import net.yozmnews.user.domain.summary.SummaryRequest;
import net.yozmnews.user.domain.summary.SummaryText;
import net.yozmnews.user.domain.userstoryline.StoredUserStoryline;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
import net.yozmnews.user.domain.userstoryline.UserStorylineRow;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사용자 하나의 정리 실행 — 사용자 스토리라인을 맞춰 저장하고, 흐름 판정으로 처분을 정해, 만들 흐름만 그 사용자의 LLM 키로 정리한다.
 * docs/specs/user.md §4 · §5
 * <p>
 * 트랜잭션 밖에서 부른다 — 생성 호출이 그렇다. 순서: 쓸 모델 → (트랜잭션) 설정 행 잠금 · 사용자 스토리라인 맞추기 · 정리와 시도 기록 읽기 →
 * 흐름 판정 → 재료 읽기 → 처분({@link SummaryPlan}) → (트랜잭션) 지울 정리 지우기 → 흐름마다 생성 호출 · 출력 읽기 → (트랜잭션) 견줌 ·
 * 저장 · 시도 기록(저장과 같은 트랜잭션 — 결과를 버릴 때도 남는다).
 * <p>
 * 저장 때 견줌: 그 사용자 스토리라인이 그대로 있고 저장한 추적 대상이 호출 때와 같고 정리의 판이 읽었을 때와 같을 때만 쓴다 — 아니면 결과를
 * 버린다. 실패한 호출과 버린 결과도 시도 기록에 든다. 로그에는 사용자 ID · 사용자 스토리라인 ID · 실패 코드만 남긴다 — 글과 프롬프트는 남기지
 * 않는다. 역할 애너테이션이 없다(부르는 작업이 작업 역할이다).
 */
@Service
public class StorylineSummaryRun {

	private static final Logger logger = LoggerFactory.getLogger(StorylineSummaryRun.class);

	private final DigestModelDirectory models;

	private final InterestSettingsLock lock;

	private final UserStorylineFollowups followups;

	private final UserStorylineRepository userStorylines;

	private final StorylineSummaryRepository summaries;

	private final StorylineStandingNow standing;

	private final SummaryMaterials materials;

	private final StorylineSummarizer summarizer;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	StorylineSummaryRun(DigestModelDirectory models, InterestSettingsLock lock, UserStorylineFollowups followups,
			UserStorylineRepository userStorylines, StorylineSummaryRepository summaries, StorylineStandingNow standing,
			SummaryMaterials materials, StorylineSummarizer summarizer, TransactionTemplate transaction,
			InstantSource clock) {
		this.models = Objects.requireNonNull(models, "models");
		this.lock = Objects.requireNonNull(lock, "lock");
		this.followups = Objects.requireNonNull(followups, "followups");
		this.userStorylines = Objects.requireNonNull(userStorylines, "userStorylines");
		this.summaries = Objects.requireNonNull(summaries, "summaries");
		this.standing = Objects.requireNonNull(standing, "standing");
		this.materials = Objects.requireNonNull(materials, "materials");
		this.summarizer = Objects.requireNonNull(summarizer, "summarizer");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * @return 이 실행이 한 일 — 쓸 모델이 없거나 계정을 쓸 수 없으면 아무 일도 하지 않는다
	 */
	public SummaryRunResult run(UserId user) {
		Objects.requireNonNull(user, "user");
		Optional<LlmModel> model = this.models.modelOf(user);
		if (model.isEmpty()) {
			return SummaryRunResult.NOTHING;
		}
		Instant now = this.clock.instant();
		Snapshot snapshot = Objects.requireNonNull(this.transaction.execute(status -> snapshot(user, now)));
		Optional<StorylineStanding> judged = this.standing.standingOf(user);
		if (judged.isEmpty()) {
			return SummaryRunResult.NOTHING;
		}
		Set<Long> followed = ids(judged.get().followed());
		Set<Long> kept = ids(judged.get().kept());
		Map<Long, Flow> flows = new LinkedHashMap<>();
		snapshot.flows().forEach(flow -> flows.put(flow.id(), flow));
		Set<Long> needing = SummaryPlan.needingMaterials(snapshot.flows(), followed);
		Set<Long> tops = needing.stream().flatMap(id -> flows.get(id).tops().stream()).collect(Collectors.toSet());
		Map<Long, List<SummaryEvent>> read = this.materials.read(tops);
		Map<Long, Map<Long, Map<Long, Instant>>> events = new HashMap<>();
		for (Long id : needing) {
			Map<Long, Map<Long, Instant>> ofFlow = new HashMap<>();
			for (Long top : flows.get(id).tops()) {
				Map<Long, Instant> ofTop = new HashMap<>();
				read.getOrDefault(top, List.of())
					.forEach(event -> ofTop.put(event.eventId(), event.firstArticleAt()));
				ofFlow.put(top, ofTop);
			}
			events.put(id, ofFlow);
		}
		List<Action> actions = SummaryPlan.decide(snapshot.flows(), followed, kept, events, now);
		int removed = remove(actions);
		Tally tally = new Tally();
		for (Action action : actions) {
			boolean goOn = switch (action) {
				case Action.Create create -> call(user, model.get(), snapshot.zone(), flows.get(create.id()), null,
						create.tops(), create.send(), create.covered(), read, tally);
				case Action.Rebuild rebuild -> call(user, model.get(), snapshot.zone(), flows.get(rebuild.id()), null,
						rebuild.tops(), rebuild.send(), rebuild.covered(), read, tally);
				case Action.Extend extend -> call(user, model.get(), snapshot.zone(), flows.get(extend.id()),
						flows.get(extend.id()).summary(), extend.tops(), extend.send(), extend.covered(), read, tally);
				case Action.Keep keep -> true;
				case Action.Remove remove -> true;
			};
			if (!goOn) {
				break;
			}
		}
		return new SummaryRunResult(tally.stored, removed, tally.failed, tally.discarded);
	}

	/** 설정 행을 잠그고 사용자 스토리라인을 맞춘 뒤의 상태 — 부르는 쪽의 트랜잭션 안에서 */
	private Snapshot snapshot(UserId user, Instant now) {
		InterestSettings settings = this.lock.lockFollowed(user, now);
		List<StoredUserStoryline> stored = this.followups.followLocked(user, Map.of(), now);
		Map<Long, StorylineSummary> summaries = new HashMap<>();
		this.summaries.load(user).forEach(summary -> summaries.put(summary.userStorylineId(), summary));
		Map<Long, SummaryAttempt> attempts = new HashMap<>();
		this.summaries.attempts(user).forEach(attempt -> attempts.put(attempt.userStorylineId(), attempt));
		List<Flow> flows = stored.stream()
			.map(one -> new Flow(one.id(), headOf(one), one.tops(), one.tracked(), summaries.get(one.id()),
					attempts.get(one.id())))
			.toList();
		return new Snapshot(flows, settings.schedule().timezone());
	}

	/** 맞춘 사용자 스토리라인의 헤드 — 저장하는 맞추기가 쓴 뒤라 비지 않는다 */
	private static long headOf(StoredUserStoryline storyline) {
		return Objects.requireNonNull(storyline.head(), "저장하는 맞추기 뒤의 헤드");
	}

	private int remove(List<Action> actions) {
		List<Long> removing = actions.stream()
			.filter(Action.Remove.class::isInstance)
			.map(Action::id)
			.toList();
		if (!removing.isEmpty()) {
			this.transaction.executeWithoutResult(status -> removing.forEach(this.summaries::delete));
		}
		return removing.size();
	}

	/**
	 * 흐름 하나를 정리한다.
	 * @param previous 고치는 호출이면 지난 정리, 처음부터면 null
	 * @return 다음 흐름으로 가도 되는가 — 저장한 키가 없거나 키 쪽의 실패면 false
	 */
	private boolean call(UserId user, LlmModel model, ZoneId zone, Flow flow, @Nullable StorylineSummary previous,
			Set<Long> tops, List<Long> send, Set<Long> covered, Map<Long, List<SummaryEvent>> read, Tally tally) {
		Set<Long> sending = new HashSet<>(send);
		Map<Long, List<SummaryEvent>> lines = new HashMap<>();
		Map<Long, SummaryText> previousTops = new HashMap<>();
		Map<Long, Long> articles = new HashMap<>();
		for (Long top : tops) {
			List<SummaryEvent> ofTop = read.getOrDefault(top, List.of());
			ofTop.stream()
				.filter(event -> covered.contains(event.eventId()))
				.forEach(event -> articles.put(event.eventId(), event.articleId()));
			List<SummaryEvent> sent = ofTop.stream().filter(event -> sending.contains(event.eventId())).toList();
			SummaryText before = (previous == null) ? null : previous.tops().get(top);
			boolean hasBefore = before != null && !before.none();
			if (sent.isEmpty() && !hasBefore) {
				// 넣을 사건도 지난 글도 없는 꼭대기 — 호출에 넣지 않고 글 없이 둔다
				continue;
			}
			lines.put(top, sent);
			if (hasBefore) {
				previousTops.put(top, before);
			}
		}
		if (lines.isEmpty()) {
			return true;
		}
		SummaryInput input = new SummaryInput(lines, (previous == null) ? null : previous.whole(), previousTops, zone);
		SummaryPrompt prompt = SummaryPrompts.prompt(input, model);
		SummaryOutcome outcome = this.summarizer.summarize(new SummaryRequest(user, model, input, prompt));
		return switch (outcome) {
			case SummaryOutcome.KeyMissing missing -> false;
			case SummaryOutcome.Failed failed -> {
				fail(user, flow.id(), 0, 0, failed.failure(), tally);
				yield !failed.failure().ofKey();
			}
			case SummaryOutcome.Generated generated -> {
				if (generated.stop() != GenerationStop.COMPLETE) {
					fail(user, flow.id(), generated.inputTokens(), generated.outputTokens(),
							(generated.stop() == GenerationStop.TRUNCATED) ? SummaryFailure.OUTPUT_TRUNCATED
									: SummaryFailure.OUTPUT_REFUSED,
							tally);
					yield true;
				}
				switch (SummaryOutputReader.read(generated.text(), prompt.aliases())) {
					case SummaryRead.Unreadable unreadable -> fail(user, flow.id(), generated.inputTokens(),
							generated.outputTokens(), SummaryFailure.UNREADABLE_OUTPUT, tally);
					case SummaryRead.Read done -> store(user, model, flow, tops, articles, input.topIds(), done,
							generated, tally);
				}
				yield true;
			}
		};
	}

	/**
	 * 견줘 저장하고 시도 기록에 더한다 — 그 사이 개체가 달라졌으면 결과를 버린다. 호출에 넣지 않은 꼭대기는 글 없이 둔다.
	 * @param topOrder 글을 받은 꼭대기 — 출력의 별칭 순
	 */
	private void store(UserId user, LlmModel model, Flow flow, Set<Long> tops, Map<Long, Long> articles,
			List<Long> topOrder, SummaryRead.Read read, SummaryOutcome.Generated generated, Tally tally) {
		Instant at = this.clock.instant();
		Map<Long, SummaryText> texts = new HashMap<>();
		tops.forEach(top -> texts.put(top, SummaryText.NONE));
		for (int i = 0; i < topOrder.size(); i++) {
			texts.put(topOrder.get(i), read.tops().get(i));
		}
		StorylineSummary existing = flow.summary();
		StorylineSummary next = new StorylineSummary(flow.id(), read.whole(), texts, articles, model.provider().key(),
				model.id(), generated.inputTokens(), generated.outputTokens(), (existing == null) ? at : existing.createdAt(),
				at, (existing == null) ? 0 : existing.version());
		Boolean stored = this.transaction.execute(status -> {
			this.lock.lockFollowed(user, at);
			Optional<UserStorylineRow> row = this.userStorylines.load(user)
				.stream()
				.filter(one -> one.id() == flow.id())
				.findFirst();
			if (row.isEmpty()) {
				// 사용자 스토리라인이 사라졌다 — 시도 기록을 둘 자리도 없다
				return false;
			}
			boolean written = row.get().members().equals(tops) && ((existing == null) ? this.summaries.create(next)
					: this.summaries.replace(next, existing.version()));
			if (written) {
				this.summaries.recordStored(flow.id(), generated.inputTokens(), generated.outputTokens());
			}
			else {
				this.summaries.recordDiscarded(flow.id(), generated.inputTokens(), generated.outputTokens());
			}
			return written;
		});
		if (Boolean.TRUE.equals(stored)) {
			tally.stored++;
		}
		else {
			tally.discarded++;
			logger.info("정리 결과를 버렸다 — 그 사이 사용자 스토리라인이나 정리가 달라졌다: user={} userStoryline={}", user.value(),
					flow.id());
		}
	}

	private void fail(UserId user, long id, int inputTokens, int outputTokens, SummaryFailure failure,
			Tally tally) {
		Instant at = this.clock.instant();
		this.transaction.executeWithoutResult(status -> {
			// 사용자 스토리라인이 사라졌으면 시도 기록을 둘 자리가 없다
			if (exists(user, id, at)) {
				this.summaries.recordFailed(id, inputTokens, outputTokens, failure.name(), at);
			}
		});
		tally.failed++;
		logger.warn("정리 호출이 실패했다: user={} userStoryline={} failure={}", user.value(), id, failure);
	}

	/** 설정 행을 잠근 뒤 그 사용자 스토리라인이 아직 있는가 — 부르는 쪽의 트랜잭션 안에서 */
	private boolean exists(UserId user, long id, Instant at) {
		this.lock.lockFollowed(user, at);
		return this.userStorylines.load(user).stream().mapToLong(UserStorylineRow::id).anyMatch(row -> row == id);
	}

	private static Set<Long> ids(Set<StorylineId> storylines) {
		return storylines.stream().map(StorylineId::value).collect(Collectors.toSet());
	}

	private record Snapshot(List<Flow> flows, ZoneId zone) {
	}

	private static final class Tally {

		private int stored;

		private int failed;

		private int discarded;

	}

}
