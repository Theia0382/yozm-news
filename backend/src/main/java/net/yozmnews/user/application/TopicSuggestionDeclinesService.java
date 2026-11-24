package net.yozmnews.user.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.AccountDirectory;
import net.yozmnews.user.TopicSuggestionDecline;
import net.yozmnews.user.TopicSuggestionDeclineList;
import net.yozmnews.user.TopicSuggestionDeclines;
import net.yozmnews.user.domain.suggestion.RecordedDecline;
import net.yozmnews.user.domain.suggestion.TopicSuggestionDeclineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TopicSuggestionDeclines} 의 구현 — 거절 행을 기록 당시 버전의 계보 표로 활성 버전의 주제 중분류로 푼다. 같은 중분류에 닿는 행이
 * 여럿이면 {@link RecordedDecline#later} 로 하나만 남긴다. 쓰지 않는다. docs/specs/user.md §4 · user-R65
 * <p>
 * 읽기 전용 {@code REPEATABLE_READ} 트랜잭션 하나에서 거절 행 · 활성 체계 · 계보 표를 함께 읽는다 — {@code TagSetDirectory} ·
 * {@code TagKeyLineage} 는 트랜잭션을 따로 열지 않아 같은 스냅숏이다. 그래서 계보 표의 기준 버전은 언제나 읽은 활성 버전이고, 다르면 불변식
 * 위반이다. 조회가 끝난 뒤의 활성 전환은 부르는 쪽이 {@link TopicSuggestionDeclineList#tagSet()} 으로 알아챈다.
 */
@Service
class TopicSuggestionDeclinesService implements TopicSuggestionDeclines {

	private final AccountDirectory accounts;

	private final TopicSuggestionDeclineRepository declines;

	private final TagSetDirectory tagSets;

	private final TagKeyLineage lineage;

	TopicSuggestionDeclinesService(AccountDirectory accounts, TopicSuggestionDeclineRepository declines,
			TagSetDirectory tagSets, TagKeyLineage lineage) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.declines = Objects.requireNonNull(declines, "declines");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.lineage = Objects.requireNonNull(lineage, "lineage");
	}

	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public TopicSuggestionDeclineList declinesOf(UserId user) {
		Objects.requireNonNull(user, "user");
		TagSetId active = this.tagSets.activeTagSet().id();
		if (!this.accounts.exists(user)) {
			return new TopicSuggestionDeclineList(active, List.of());
		}
		Map<TagSetId, TagKeyMapping> mappings = new HashMap<>();
		Map<String, RecordedDecline> kept = new HashMap<>();
		for (RecordedDecline row : this.declines.load(user)) {
			TagKeyMapping mapping = mappings.computeIfAbsent(row.version(), this.lineage::toActive);
			if (!mapping.to().equals(active)) {
				throw new IllegalStateException(
						"한 스냅숏의 계보 표가 활성 버전 " + active.value() + " 이 아닌 " + mapping.to().value() + " 을 가리킨다");
			}
			for (String subtopic : mapping.resolve(TagKind.TOPIC, row.key())) {
				kept.merge(subtopic, row, RecordedDecline::later);
			}
		}
		List<TopicSuggestionDecline> resolved = kept.entrySet()
			.stream()
			.map(entry -> new TopicSuggestionDecline(entry.getKey(), entry.getValue().level(),
					entry.getValue().declinedAt()))
			.toList();
		return new TopicSuggestionDeclineList(active, resolved);
	}

}
