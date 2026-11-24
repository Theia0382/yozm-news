package net.yozmnews.user.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.SummaryUsage;
import net.yozmnews.user.TopSummary;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import net.yozmnews.user.UserStorylineSummaryRef;
import net.yozmnews.user.UserStorylineSummaryView;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import net.yozmnews.user.domain.summary.SummaryAttempt;
import net.yozmnews.user.domain.summary.SummaryText;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
import net.yozmnews.user.domain.userstoryline.UserStorylineRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserStorylineSummaries} 의 구현 — 저장한 정리 · 시도 기록 · 사용자 스토리라인 행을 읽어 엮는다. docs/specs/user.md §4
 * <p>
 * 읽기 전용 트랜잭션 하나에서 읽는다 — 한 시점의 정리다. 쓰지 않고, 사용자 스토리라인을 맞추지 않으며 설정 행을 잠그지 않는다. 추적 대상의
 * 정본은 저장한 추적 대상({@code user_storyline_members})이다 — 꼭대기마다의 글은 그 꼭대기만 낸다(정리 때의 꼭대기가 추적 대상에서 빠졌으면
 * 그 글은 내지 않고, 정리 뒤에 든 꼭대기는 글 없이 낸다).
 */
@Service
class UserStorylineSummariesService implements UserStorylineSummaries {

	private final StorylineSummaryRepository summaries;

	private final UserStorylineRepository userStorylines;

	UserStorylineSummariesService(StorylineSummaryRepository summaries, UserStorylineRepository userStorylines) {
		this.summaries = Objects.requireNonNull(summaries, "summaries");
		this.userStorylines = Objects.requireNonNull(userStorylines, "userStorylines");
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserStorylineSummaryView> shown(UserId user) {
		Objects.requireNonNull(user, "user");
		return views(user, this.summaries.load(user));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UserStorylineSummaryView> find(UserId user, UserStorylineId id) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(id, "id");
		return this.summaries.find(user, id.value()).flatMap(summary -> views(user, List.of(summary)).stream().findFirst());
	}

	@Override
	@Transactional(readOnly = true)
	public Map<StorylineId, List<UserStorylineSummaryRef>> ofTops(UserId user, Collection<StorylineId> tops) {
		Objects.requireNonNull(user, "user");
		Set<StorylineId> asked = new HashSet<>(Objects.requireNonNull(tops, "tops"));
		if (asked.size() > MAX_TOPS) {
			throw new IllegalArgumentException("꼭대기가 상한보다 많다: " + asked.size());
		}
		if (asked.isEmpty()) {
			return Map.of();
		}
		Map<StorylineId, List<UserStorylineSummaryRef>> found = new HashMap<>();
		List<UserStorylineSummaryView> byId = shown(user).stream()
			.sorted(Comparator.comparingLong(view -> view.id().value()))
			.toList();
		for (UserStorylineSummaryView view : byId) {
			UserStorylineSummaryRef ref = new UserStorylineSummaryRef(view.id(), view.title());
			view.members()
				.stream()
				.filter(asked::contains)
				.forEach(top -> found.computeIfAbsent(top, ignored -> new ArrayList<>()).add(ref));
		}
		Map<StorylineId, List<UserStorylineSummaryRef>> copied = new HashMap<>();
		found.forEach((top, refs) -> copied.put(top, List.copyOf(refs)));
		return Map.copyOf(copied);
	}

	/** 정리를 받은 순서대로 엮는다 — 사용자 스토리라인 행이 없는 정리는 뺀다(읽는 사이 지워진 것) */
	private List<UserStorylineSummaryView> views(UserId user, List<StorylineSummary> summaries) {
		if (summaries.isEmpty()) {
			return List.of();
		}
		Map<Long, UserStorylineRow> rows = this.userStorylines.load(user)
			.stream()
			.collect(Collectors.toMap(UserStorylineRow::id, row -> row));
		Map<Long, SummaryAttempt> attempts = this.summaries.attempts(user)
			.stream()
			.collect(Collectors.toMap(SummaryAttempt::userStorylineId, attempt -> attempt));
		List<UserStorylineSummaryView> views = new ArrayList<>();
		for (StorylineSummary summary : summaries) {
			UserStorylineRow row = rows.get(summary.userStorylineId());
			if (row == null) {
				continue;
			}
			List<TopSummary> tops = new ArrayList<>();
			for (Long member : new TreeSet<>(row.members())) {
				SummaryText text = summary.tops().get(member);
				tops.add((text == null || text.none()) ? TopSummary.without(new StorylineId(member))
						: new TopSummary(new StorylineId(member), text.title(), text.summary(), text.recent()));
			}
			SummaryAttempt attempt = attempts.get(summary.userStorylineId());
			views.add(new UserStorylineSummaryView(new UserStorylineId(summary.userStorylineId()), handle(row),
					summary.whole().title(),
					summary.whole().summary(), summary.whole().recent(), row.subscribedAt() != null,
					row.members().stream().map(StorylineId::new).collect(Collectors.toUnmodifiableSet()), tops,
					new SummaryUsage(summary.provider(), summary.model(), summary.inputTokens(), summary.outputTokens(),
							(attempt == null) ? 0 : attempt.calls(), (attempt == null) ? 0 : attempt.totalInputTokens(),
							(attempt == null) ? 0 : attempt.totalOutputTokens()),
					summary.createdAt(), summary.updatedAt()));
		}
		return List.copyOf(views);
	}

	/**
	 * 저장한 헤드 — 헤드 열이 생기기 전에 저장된 개체(그 사용자의 다음 저장하는 맞추기 전까지)는 저장한 추적 대상 가운데 가장 작은 꼭대기. 이 대체는
	 * 여기 한 곳뿐이다 — 맞춘 값을 쓰는 다른 자리는 헤드가 비지 않는다
	 */
	private static StorylineId handle(UserStorylineRow row) {
		return new StorylineId((row.head() != null) ? row.head() : new TreeSet<>(row.members()).first());
	}

}
