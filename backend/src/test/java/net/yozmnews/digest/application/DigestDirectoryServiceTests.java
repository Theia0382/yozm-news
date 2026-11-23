package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestAttempt;
import net.yozmnews.digest.DigestCitation;
import net.yozmnews.digest.DigestDateEntry;
import net.yozmnews.digest.DigestDates;
import net.yozmnews.digest.DigestDay;
import net.yozmnews.digest.DigestDayState;
import net.yozmnews.digest.DigestDayUsage;
import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestRequestOutcome;
import net.yozmnews.digest.DigestRequestStatus;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.DigestItemCursor;
import net.yozmnews.digest.DigestItemPage;
import net.yozmnews.digest.DigestItemRef;
import java.util.Set;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.DigestUnresolvedFailure;
import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.ShownDigest;
import net.yozmnews.digest.adapter.out.fake.FakeDigestGenerator;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.content.DigestSectionRepository;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestOwnerLock;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.view.DigestDayRepository;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.DigestFailureCause;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.UserDigestSchedules;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserTimezones;
import org.jobrunr.scheduling.JobScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다이제스트 조회 — 실제 PostgreSQL 로, 날짜의 보이는 다이제스트는 그 날짜의 READY 가운데 ID 가 가장 큰 것이고 가장 최근 시도 · 사용량 · 이웃
 * 날짜가 함께 오는지, 날짜 목록이 날짜마다 상태를 늦은 순으로 쪽에 담는지, 수동 생성 상태가 사용자 시간대의 오늘 횟수 · 대상과 오늘 일정
 * 생성 시각 · 해결되지 않은 마지막 실패를 내는지 본다. 다른 사용자의 행은 보이지 않는다. 행은 SQL 로 넣는다.
 * <p>
 * 수동 생성 상태의 사용자는 `America/Los_Angeles`(PST, UTC−8)이다. 기대값은 손으로 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: digest-R13 · digest-R38 · digest-R39 · digest-R40
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestDirectoryServiceTests {

	/** 2026-11-18 12:00 PST */
	private static final Instant NOW = Instant.parse("2026-11-18T20:00:00Z");

	private static final ZoneId LA = ZoneId.of("America/Los_Angeles");

	@Autowired
	JobScheduler digestJobs;

	@Autowired
	DigestLedger ledger;

	@Autowired
	DigestDayRepository days;

	@Autowired
	UserTimezones timezones;

	@Autowired
	UserDigestSchedules schedules;

	@Autowired
	DigestModelDirectory models;

	@Autowired
	DigestOwnerLock owners;

	@Autowired
	DigestRepository digests;

	@Autowired
	DigestSectionRepository sections;

	@Autowired
	DigestCandidateRepository candidates;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	DigestEndings endings;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	StorylineDirectory storylines;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
		this.jdbc.sql("delete from storyline_relations").update();
		this.jdbc.sql("delete from storylines").update();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'digest-view-%')")
			.update();
		this.jdbc.sql("delete from users where email like 'digest-view-%'").update();
	}

	private DigestDirectoryService directory() {
		return directory(NOW);
	}

	private DigestDirectoryService directory(Instant now) {
		return new DigestDirectoryService(this.ledger, this.days, this.schedules, this.models, this.storylines,
				this.transaction,
				() -> now);
	}

	private DigestRequestsService requests() {
		DigestGeneration generation = new DigestGeneration(this.digests, this.sections, this.candidates, this.articles,
				new FakeDigestGenerator(), this.endings, this.transaction, TestClocks.fixed(NOW));
		return new DigestRequestsService(this.timezones, this.models, this.ledger, this.owners, generation,
				new DigestDispatch((id, token) -> {
				}), this.transaction, () -> NOW);
	}

	@Test
	void aSectionCarriesTheCurrentTopOfTheFlowThatGroupedItsItem() {
		UserId user = user("digest-view-flow@example.com", "VALID");
		Instant at = Instant.parse("2026-11-17T00:00:00Z");
		long kept = UserStorylineFixtures.storyline(this.jdbc, at);
		long merged = UserStorylineFixtures.storyline(this.jdbc, at);
		long survivor = UserStorylineFixtures.storyline(this.jdbc, at);
		long parent = UserStorylineFixtures.storyline(this.jdbc, at);
		long removed = UserStorylineFixtures.storyline(this.jdbc, at);
		long ready = row(user, "2026-11-18", "READY", 100, 20, null);
		section(ready, "OVERVIEW", 0, null, "오늘의 흐름");
		long[] items = { section(ready, "TOP_STORY", 1, "그대로", "s"), section(ready, "TOP_STORY", 2, "합쳐짐", "s"),
				section(ready, "HEADLINE", 3, "지워짐", null), section(ready, "HEADLINE", 4, "혼자", null) };
		for (int i = 0; i < items.length; i++) {
			item(items[i], 0, 7 + i);
			sectionEvent(items[i], 0, 70 + i, "SUBSCRIBED");
		}
		storylineOf(items[0], kept);
		storylineOf(items[1], merged);
		storylineOf(items[2], removed);
		// 대조 — 생성 때의 꼭대기가 그대로면 그 값이다
		assertThat(flows(user)).containsExactly(null, new StorylineId(kept), new StorylineId(merged),
				new StorylineId(removed), null);

		// 저장한 뒤 — 하나는 다른 스토리라인에 합쳐지고 그 남는 쪽이 더 큰 나무에 들었다, 하나는 지워졌다
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);
		UserStorylineFixtures.contains(this.jdbc, parent, survivor);
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		assertThat(flows(user)).as("합쳐졌으면 남는 쪽의 지금 꼭대기, 지워졌으면 없다")
			.containsExactly(null, new StorylineId(kept), new StorylineId(parent), null, null);
	}

	@Test
	void itemsOfAFlowAreFoundAcrossDatesByTheCurrentTopOfTheStoredOneLatestFirst() {
		UserId user = user("digest-view-items@example.com", "VALID");
		UserId other = user("digest-view-items-other@example.com", "VALID");
		Instant at = Instant.parse("2026-11-15T00:00:00Z");
		long flow = UserStorylineFixtures.storyline(this.jdbc, at);
		long merged = UserStorylineFixtures.storyline(this.jdbc, at);
		long elsewhere = UserStorylineFixtures.storyline(this.jdbc, at);
		long removed = UserStorylineFixtures.storyline(this.jdbc, at);
		long first = row(user, "2026-11-16", "READY", 1, 1, null);
		storylineOf(itemSection(first, "TOP_STORY", 1, "16일 주요", 161), flow);
		storylineOf(itemSection(first, "HEADLINE", 2, "16일 다른 흐름", 162), elsewhere);
		long covered = row(user, "2026-11-17", "READY", 1, 1, null);
		storylineOf(itemSection(covered, "TOP_STORY", 1, "17일 덮인 것", 170), flow);
		long second = row(user, "2026-11-17", "READY", 1, 1, null);
		storylineOf(itemSection(second, "TOP_STORY", 1, "17일 합쳐진 것", 171), merged);
		itemSection(second, "HEADLINE", 2, "17일 흐름 없음", 172);
		storylineOf(itemSection(second, "HEADLINE", 3, "17일 헤드라인", 173, 174), flow);
		storylineOf(itemSection(second, "HEADLINE", 4, "17일 지워진 것", 175), removed);
		long failed = row(user, "2026-11-18", "FAILED", null, null, "PROVIDER_UNAVAILABLE");
		storylineOf(itemSection(failed, "TOP_STORY", 1, "18일 실패", 180), flow);
		long others = row(other, "2026-11-18", "READY", 1, 1, null);
		storylineOf(itemSection(others, "TOP_STORY", 1, "다른 사용자", 190), flow);
		// 저장한 뒤 — 하나는 흐름에 합쳐지고 하나는 지워졌다
		UserStorylineFixtures.merge(this.jdbc, merged, flow);
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		DigestItemPage page = directory().itemsOfStorylines(user, Set.of(new StorylineId(flow)), null, 50);

		assertThat(page).isEqualTo(new DigestItemPage(List.of(
				new DigestItemRef(LocalDate.parse("2026-11-17"), 3, SectionKind.HEADLINE, "17일 헤드라인",
						List.of(new EventId(173), new EventId(174))),
				new DigestItemRef(LocalDate.parse("2026-11-17"), 1, SectionKind.TOP_STORY, "17일 합쳐진 것",
						List.of(new EventId(171))),
				new DigestItemRef(LocalDate.parse("2026-11-16"), 1, SectionKind.TOP_STORY, "16일 주요",
						List.of(new EventId(161)))),
				null));
		// 대조 — 다른 흐름은 그 흐름의 항목만, 다른 사용자는 자기 것만
		assertThat(directory().itemsOfStorylines(user, Set.of(new StorylineId(elsewhere)), null, 50).items())
			.extracting(DigestItemRef::title)
			.containsExactly("16일 다른 흐름");
		assertThat(directory().itemsOfStorylines(other, Set.of(new StorylineId(flow)), null, 50).items())
			.extracting(DigestItemRef::title)
			.containsExactly("다른 사용자");
		assertThat(directory().itemsOfStorylines(user, Set.of(new StorylineId(removed)), null, 50)).isEqualTo(DigestItemPage.EMPTY);
	}

	@Test
	void itemsOfAFlowArePagedByDateAndSectionPosition() {
		UserId user = user("digest-view-items-page@example.com", "VALID");
		long flow = UserStorylineFixtures.storyline(this.jdbc, Instant.parse("2026-11-15T00:00:00Z"));
		long first = row(user, "2026-11-16", "READY", 1, 1, null);
		storylineOf(itemSection(first, "TOP_STORY", 1, "16일 1", 161), flow);
		long second = row(user, "2026-11-17", "READY", 1, 1, null);
		storylineOf(itemSection(second, "TOP_STORY", 1, "17일 1", 171), flow);
		storylineOf(itemSection(second, "HEADLINE", 2, "17일 2", 172), flow);
		Set<StorylineId> tops = Set.of(new StorylineId(flow));

		DigestItemPage firstPage = directory().itemsOfStorylines(user, tops, null, 2);
		DigestItemPage lastPage = directory().itemsOfStorylines(user, tops, firstPage.next(), 2);

		assertThat(firstPage.items()).extracting(DigestItemRef::title).containsExactly("17일 2", "17일 1");
		assertThat(firstPage.next()).isEqualTo(new DigestItemCursor(LocalDate.parse("2026-11-17"), 1));
		assertThat(lastPage).isEqualTo(new DigestItemPage(List.of(new DigestItemRef(LocalDate.parse("2026-11-16"), 1,
				SectionKind.TOP_STORY, "16일 1", List.of(new EventId(161)))), null));
		// 한 쪽에 꼭 맞으면 다음 쪽이 없다
		assertThat(directory().itemsOfStorylines(user, tops, null, 3).next()).isNull();
	}

	@Test
	void itemsOfNoTopsAreEmptyAndThePageSizeIsBounded() {
		UserId user = user("digest-view-items-size@example.com", "VALID");

		assertThat(directory().itemsOfStorylines(user, Set.of(), null, 50)).isEqualTo(DigestItemPage.EMPTY);
		assertThat(directory().itemsOfStorylines(user, Set.of(new StorylineId(1)), null, 1)).isEqualTo(DigestItemPage.EMPTY);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> directory().itemsOfStorylines(user, Set.of(new StorylineId(1)), null, 0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> directory().itemsOfStorylines(user, Set.of(new StorylineId(1)), null, 51));
	}

	/** 항목 섹션 하나와 그 사건들 */
	private long itemSection(long digest, String kind, int position, String title, long... events) {
		long section = section(digest, kind, position, title, null);
		for (int i = 0; i < events.length; i++) {
			sectionEvent(section, i, events[i], "SUBSCRIBED");
		}
		return section;
	}

	/** 2026-11-18 의 보이는 다이제스트 섹션마다의 흐름 — 위치 순 */
	private List<@Nullable StorylineId> flows(UserId user) {
		return directory().on(user, LocalDate.parse("2026-11-18"))
			.orElseThrow()
			.shown()
			.sections()
			.stream()
			.map(DigestSectionView::storyline)
			.toList();
	}

	private void storylineOf(long section, long storyline) {
		this.jdbc.sql("update digest_sections set storyline_id = :storyline where id = :id")
			.param("storyline", storyline)
			.param("id", section)
			.update();
	}

	@Test
	void theShownDigestIsTheLatestReadyOfTheDateAndTheLatestAttemptComesWithIt() {
		UserId user = user("digest-view-shown@example.com", "VALID");
		long ready = row(user, "2026-11-18", "READY", 100, 20, null);
		section(ready, "OVERVIEW", 0, null, "오늘의 흐름");
		long story = section(ready, "TOP_STORY", 1, "주요 뉴스 제목", "주요 뉴스 요약");
		item(story, 0, 7);
		item(story, 1, 9);
		sectionEvent(story, 0, 70, "MAJOR_STORYLINE");
		sectionEvent(story, 1, 90, "SUBSCRIBED");
		long more = section(ready, "HEADLINE", 2, "헤드라인 제목", "한 줄 코멘트");
		item(more, 0, 8);
		sectionEvent(more, 0, 80, "INTEREST");
		long plain = section(ready, "HEADLINE", 3, "구독 헤드라인", null);
		item(plain, 0, 6);
		sectionEvent(plain, 0, 60, "SUBSCRIBED");
		// 관심도 사건의 표본이 모두 지워져 인용은 구독 사건의 기사뿐이어도 항목의 이유는 섹션 사건 전부로 정한다
		long followed = section(ready, "HEADLINE", 4, "남은 인용 헤드라인", null);
		item(followed, 0, 5);
		// 위치 순으로 읽는다 — 뒤 위치를 먼저 넣는다
		sectionEvent(followed, 1, 51, "INTEREST");
		sectionEvent(followed, 0, 50, "SUBSCRIBED");
		// 공급자가 답하지 않은 실패 — 시도 수에는 들고 토큰 합에는 들지 않는다
		row(user, "2026-11-18", "FAILED", null, null, "PROVIDER_UNAVAILABLE");
		long failed = row(user, "2026-11-18", "FAILED", 50, 0, "NO_CITATION");

		DigestDay day = directory().on(user, LocalDate.parse("2026-11-18")).orElseThrow();

		assertThat(day.shown()).isEqualTo(new ShownDigest(ready, Instant.parse("2026-11-17T15:00:00Z"),
				Instant.parse("2026-11-18T15:00:00Z"), "openai", "m", 30, 100, 20, Instant.parse("2026-11-18T15:05:00Z"),
				List.of(new DigestSectionView(SectionKind.OVERVIEW, null, "오늘의 흐름", null, List.of(), List.of(), null),
						// 항목의 이유 하나 — 큰 슬롯 사건이 있으면 큰 화제, 관심도 사건이 있으면 관심 반영, 아니면 구독 주제.
						// 항목의 사건은 위치 순이다(첫 사건이 항목 키)
						new DigestSectionView(SectionKind.TOP_STORY, "주요 뉴스 제목", "주요 뉴스 요약",
								SelectionReason.MAJOR_STORYLINE,
								List.of(new DigestCitation(new ArticleId(7)), new DigestCitation(new ArticleId(9))),
								List.of(new EventId(70), new EventId(90)), null),
						new DigestSectionView(SectionKind.HEADLINE, "헤드라인 제목", "한 줄 코멘트", SelectionReason.INTEREST,
								List.of(new DigestCitation(new ArticleId(8))), List.of(new EventId(80)), null),
						new DigestSectionView(SectionKind.HEADLINE, "구독 헤드라인", null, SelectionReason.SUBSCRIBED,
								List.of(new DigestCitation(new ArticleId(6))), List.of(new EventId(60)), null),
						new DigestSectionView(SectionKind.HEADLINE, "남은 인용 헤드라인", null, SelectionReason.INTEREST,
								List.of(new DigestCitation(new ArticleId(5))), List.of(new EventId(50), new EventId(51)), null))));
		assertThat(day.latest()).isEqualTo(new DigestAttempt(failed, DigestStatus.FAILED, DigestFailure.NO_CITATION,
				DigestFailureCause.OUTPUT, Instant.parse("2026-11-18T15:05:00Z")));
		assertThat(day.usage()).isEqualTo(new DigestDayUsage(3, 150, 20));
	}

	@Test
	void aLaterReadyOfTheSameDateIsShownInsteadOfTheEarlierOne() {
		UserId user = user("digest-view-later@example.com", "VALID");
		row(user, "2026-11-18", "READY", 100, 20, null);
		long later = row(user, "2026-11-18", "READY", 90, 30, null);

		DigestDay day = directory().on(user, LocalDate.parse("2026-11-18")).orElseThrow();

		assertThat(day.shown()).isNotNull();
		assertThat(day.shown().digestId()).isEqualTo(later);
		assertThat(day.latest().digestId()).isEqualTo(later);
		assertThat(day.usage()).isEqualTo(new DigestDayUsage(2, 190, 50));
	}

	@Test
	void aDateWithoutReadyHasNoShownDigestAndAReadyWithoutSectionsStaysShown() {
		UserId user = user("digest-view-empty@example.com", "VALID");
		long empty = row(user, "2026-11-17", "EMPTY", null, null, null);
		long bare = row(user, "2026-11-18", "READY", 100, 20, null);

		DigestDay emptyDay = directory().on(user, LocalDate.parse("2026-11-17")).orElseThrow();
		DigestDay bareDay = directory().on(user, LocalDate.parse("2026-11-18")).orElseThrow();

		assertThat(emptyDay.shown()).isNull();
		assertThat(emptyDay.latest())
			.isEqualTo(new DigestAttempt(empty, DigestStatus.EMPTY, null, null, Instant.parse("2026-11-18T15:05:00Z")));
		assertThat(emptyDay.usage()).isEqualTo(new DigestDayUsage(1, 0, 0));
		assertThat(bareDay.shown()).isNotNull();
		assertThat(bareDay.shown().digestId()).isEqualTo(bare);
		assertThat(bareDay.shown().sections()).isEmpty();
	}

	@Test
	void theLatestDateAndTheNeighbouringDatesAreTheUsersOwn() {
		UserId user = user("digest-view-dates@example.com", "VALID");
		UserId other = user("digest-view-other@example.com", "VALID");
		row(user, "2026-11-16", "EMPTY", null, null, null);
		row(user, "2026-11-17", "READY", 100, 20, null);
		row(user, "2026-11-18", "RUNNING", null, null, null);
		row(other, "2026-11-19", "READY", 100, 20, null);
		row(other, "2026-11-15", "READY", 100, 20, null);

		DigestDay latest = directory().latest(user).orElseThrow();
		DigestDay middle = directory().on(user, LocalDate.parse("2026-11-17")).orElseThrow();
		DigestDay first = directory().on(user, LocalDate.parse("2026-11-16")).orElseThrow();

		assertThat(latest.date()).isEqualTo(LocalDate.parse("2026-11-18"));
		assertThat(latest.shown()).isNull();
		assertThat(latest.latest().status()).isEqualTo(DigestStatus.RUNNING);
		assertThat(latest.previousDate()).isEqualTo(LocalDate.parse("2026-11-17"));
		assertThat(latest.nextDate()).isNull();
		assertThat(middle.previousDate()).isEqualTo(LocalDate.parse("2026-11-16"));
		assertThat(middle.nextDate()).isEqualTo(LocalDate.parse("2026-11-18"));
		assertThat(first.previousDate()).isNull();
		assertThat(directory().on(user, LocalDate.parse("2026-11-19"))).isEmpty();
		assertThat(directory().latest(user("digest-view-none@example.com", "VALID"))).isEmpty();
	}

	@Test
	void theDateListCarriesEachDatesStateLatestFirstInPages() {
		UserId user = user("digest-view-list@example.com", "VALID");
		UserId other = user("digest-view-list-other@example.com", "VALID");
		row(user, "2026-11-18", "READY", 100, 20, null);
		row(user, "2026-11-18", "RUNNING", null, null, null);
		row(user, "2026-11-17", "FAILED", null, null, "KEY_INVALID");
		row(user, "2026-11-16", "EMPTY", null, null, null);
		row(user, "2026-11-15", "SCHEDULED", null, null, null);
		row(other, "2026-11-19", "READY", 100, 20, null);

		DigestDates first = directory().dates(user, null, 2);
		DigestDates second = directory().dates(user, first.next(), 2);

		assertThat(first).isEqualTo(new DigestDates(
				List.of(new DigestDateEntry(LocalDate.parse("2026-11-18"), DigestDayState.CONTENT),
						new DigestDateEntry(LocalDate.parse("2026-11-17"), DigestDayState.FAILED)),
				LocalDate.parse("2026-11-17")));
		assertThat(second).isEqualTo(new DigestDates(
				List.of(new DigestDateEntry(LocalDate.parse("2026-11-16"), DigestDayState.EMPTY),
						new DigestDateEntry(LocalDate.parse("2026-11-15"), DigestDayState.IN_PROGRESS)),
				null));
		assertThat(directory().dates(user("digest-view-list-none@example.com", "VALID"), null, 2))
			.isEqualTo(new DigestDates(List.of(), null));
	}

	@Test
	void aDateListPageOutsideItsRangeIsRefused() {
		UserId user = user("digest-view-range@example.com", "VALID");

		assertThatIllegalArgumentException().isThrownBy(() -> directory().dates(user, null, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> directory().dates(user, null, 101));
		assertThat(directory().dates(user, null, 100).dates()).isEmpty();
	}

	@Test
	void theRequestStatusCountsTodaysManualRowsInTheUsersZone() {
		UserId user = user("digest-view-count@example.com", "VALID");
		// 2026-11-17 23:00 PST — 그 시간대의 어제
		manualRow(user, "2026-11-17", Instant.parse("2026-11-18T07:00:00Z"));
		// 2026-11-18 01:00 PST · 11:00 PST — 오늘
		manualRow(user, "2026-11-18", Instant.parse("2026-11-18T09:00:00Z"));
		manualRow(user, "2026-11-18", Instant.parse("2026-11-18T19:00:00Z"));

		assertThat(directory().requestStatus(user)).contains(new DigestRequestStatus(LocalDate.parse("2026-11-18"), LA,
				true, false, 1, Instant.parse("2026-11-19T08:00:00Z"), LocalDate.parse("2026-11-18"), true, null, null));
	}

	@Test
	void withoutATodayRowARequestMakesTodayAnew() {
		UserId user = user("digest-view-new@example.com", "VALID");

		assertThat(directory().requestStatus(user)).contains(new DigestRequestStatus(LocalDate.parse("2026-11-18"), LA,
				true, false, 3, Instant.parse("2026-11-19T08:00:00Z"), LocalDate.parse("2026-11-18"), false, null, null));
		// 같은 정본을 부르는 수동 생성도 같은 대상을 만든다
		assertThat(requests().request(user))
			.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-18"), false));
	}

	@Test
	void withATodayRowARequestMakesTodayAgain() {
		UserId user = user("digest-view-again@example.com", "VALID");
		row(user, "2026-11-18", "EMPTY", null, null, null);

		assertThat(directory().requestStatus(user)).contains(new DigestRequestStatus(LocalDate.parse("2026-11-18"), LA,
				true, false, 3, Instant.parse("2026-11-19T08:00:00Z"), LocalDate.parse("2026-11-18"), true, null, null));
		assertThat(requests().request(user))
			.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-18"), true));
	}

	@Test
	void withALaterDateThanTodayARequestMakesThatDateAgain() {
		UserId user = user("digest-view-later-date@example.com", "VALID");
		row(user, "2026-11-19", "READY", 100, 20, null);

		assertThat(directory().requestStatus(user)).contains(new DigestRequestStatus(LocalDate.parse("2026-11-18"), LA,
				true, false, 3, Instant.parse("2026-11-19T08:00:00Z"), LocalDate.parse("2026-11-19"), true, null, null));
		assertThat(requests().request(user))
			.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-19"), true));
	}

	@Test
	void aDigestInProgressAndAnInvalidKeyShowInTheStatus() {
		UserId running = user("digest-view-running@example.com", "VALID");
		row(running, "2026-11-18", "RUNNING", null, null, null);
		UserId invalid = user("digest-view-invalid@example.com", "INVALID");

		Optional<DigestRequestStatus> runningStatus = directory().requestStatus(running);
		Optional<DigestRequestStatus> invalidStatus = directory().requestStatus(invalid);

		assertThat(runningStatus).map(DigestRequestStatus::inProgress).contains(true);
		assertThat(runningStatus).map(DigestRequestStatus::usableModel).contains(true);
		assertThat(invalidStatus).map(DigestRequestStatus::usableModel).contains(false);
		assertThat(invalidStatus).map(DigestRequestStatus::inProgress).contains(false);
		assertThat(directory().requestStatus(new UserId(Long.MAX_VALUE))).isEmpty();
	}

	@Test
	void beforeTodaysGenerationTimeTheStatusCarriesIt() {
		UserId user = user("digest-view-upcoming@example.com", "VALID");
		row(user, "2026-11-17", "READY", 100, 20, null);

		// 2026-11-18 03:16 PST — 생성 시각 03:17 PST(11:17Z) 전
		assertThat(directory(Instant.parse("2026-11-18T11:16:00Z")).requestStatus(user))
			.map(DigestRequestStatus::scheduledAt)
			.contains(Instant.parse("2026-11-18T11:17:00Z"));
		// 생성 시각과 같은 시각은 전이 아니다
		assertThat(directory(Instant.parse("2026-11-18T11:17:00Z")).requestStatus(user)).hasValueSatisfying(status -> {
			assertThat(status.scheduledAt()).isNull();
			assertThat(status.timezone()).isEqualTo(LA);
		});
	}

	@Test
	void withTodaysRowOrWithoutAUsableModelNoGenerationTimeIsCarried() {
		UserId today = user("digest-view-upcoming-today@example.com", "VALID");
		row(today, "2026-11-18", "READY", 100, 20, null);
		UserId invalid = user("digest-view-upcoming-invalid@example.com", "INVALID");
		Instant before = Instant.parse("2026-11-18T11:16:00Z");

		assertThat(directory(before).requestStatus(today)).hasValueSatisfying(
				status -> assertThat(status.scheduledAt()).isNull());
		assertThat(directory(before).requestStatus(invalid)).hasValueSatisfying(
				status -> assertThat(status.scheduledAt()).isNull());
	}

	@Test
	void theLatestFailureWithoutACoveringRowAfterItIsUnresolved() {
		UserId user = user("digest-view-unresolved@example.com", "VALID");
		UserId other = user("digest-view-unresolved-other@example.com", "VALID");
		row(user, "2026-11-16", "READY", 100, 20, null);
		row(user, "2026-11-17", "FAILED", null, null, "PROVIDER_UNAVAILABLE");
		// 진행 중은 끝나지 않아 해결로 치지 않는다
		row(user, "2026-11-18", "RUNNING", null, null, null);
		// 다른 사용자의 더 큰 ID 는 보지 않는다
		row(other, "2026-11-18", "READY", 100, 20, null);

		assertThat(directory().requestStatus(user)).map(DigestRequestStatus::unresolvedFailure)
			.contains(new DigestUnresolvedFailure(LocalDate.parse("2026-11-17"), Instant.parse("2026-11-18T15:05:00Z")));
		assertThat(directory().requestStatus(other)).hasValueSatisfying(
				status -> assertThat(status.unresolvedFailure()).isNull());
	}

	@Test
	void anEmptyAfterTheLatestFailureResolvesIt() {
		UserId user = user("digest-view-resolved@example.com", "VALID");
		row(user, "2026-11-17", "FAILED", null, null, "PROVIDER_UNAVAILABLE");
		row(user, "2026-11-18", "EMPTY", null, null, null);

		assertThat(directory().requestStatus(user)).hasValueSatisfying(
				status -> assertThat(status.unresolvedFailure()).isNull());
	}

	/** 쓰는 계정 · 설정 행(LA · 03:17) · 그 상태의 OpenAI 키 */
	private UserId user(String email, String keyStatus) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, interest_text, weekly_report,
				    weekly_report_day, monthly_report, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'America/Los_Angeles', '03:17', '', true, 1, true, 'NORMAL', 3, 'NORMAL', 'MEDIUM',
				    now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', :status, now(), now())
				""").param("user", user.value()).param("status", keyStatus).update();
		return user;
	}

	/**
	 * 다이제스트 행 하나 — 수동 계기, 기간은 그 날짜 전날 15:00Z ~ 그 날짜 15:00Z. 시작한 상태면 공급자 openai · 모델 m · 후보 30, 끝난 상태면
	 * 2026-11-18T15:05:00Z 에 끝냈다.
	 * @return 다이제스트 ID
	 */
	private long row(UserId user, String date, String status, @Nullable Integer input, @Nullable Integer output,
			@Nullable String failure) {
		boolean started = !status.equals("SCHEDULED");
		boolean finished = List.of("READY", "EMPTY", "FAILED").contains(status);
		return this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, provider, model,
				    candidate_count, input_tokens, output_tokens, error_code, created_at, dispatch_token, dispatched_at,
				    started_at, completed_at, version)
				values (:user, cast(:date as date), :periodStart, :periodEnd, :status, 'MANUAL', :provider, :model, :candidates,
				    :input, :output, :failure, timestamptz '2026-11-17T00:00:00Z', gen_random_uuid(),
				    timestamptz '2026-11-17T00:00:00Z', :started, :completed, 0)
				returning id
				""")
			.param("user", user.value())
			.param("date", date)
			.param("periodStart", Timestamp.from(Instant.parse(LocalDate.parse(date).minusDays(1) + "T15:00:00Z")))
			.param("periodEnd", Timestamp.from(Instant.parse(date + "T15:00:00Z")))
			.param("status", status)
			.param("provider", started ? "openai" : null)
			.param("model", started ? "m" : null)
			.param("candidates", started ? 30 : null)
			.param("input", input)
			.param("output", output)
			.param("failure", failure)
			.param("started", started ? Timestamp.from(Instant.parse("2026-11-18T15:00:00Z")) : null)
			.param("completed", finished ? Timestamp.from(Instant.parse("2026-11-18T15:05:00Z")) : null)
			.query(Long.class)
			.single();
	}

	/** 끝난 수동 다이제스트(EMPTY) — 그 날짜 · 만든 시각 */
	private void manualRow(UserId user, String date, Instant createdAt) {
		this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    dispatch_token, dispatched_at, started_at, completed_at, provider, model, version)
				values (:user, cast(:date as date), cast(:created as timestamptz) - interval '1 day', :created, 'EMPTY',
				    'MANUAL', :created, gen_random_uuid(), :created, :created, :created, 'openai', 'm', 0)
				""")
			.param("user", user.value())
			.param("date", date)
			.param("created", Timestamp.from(createdAt))
			.update();
	}

	private long section(long digest, String kind, int position, @Nullable String title, @Nullable String body) {
		return this.jdbc.sql("""
				insert into digest_sections (digest_id, kind, position, title, body)
				values (:digest, :kind, :position, :title, :body) returning id
				""")
			.param("digest", digest)
			.param("kind", kind)
			.param("position", position)
			.param("title", title)
			.param("body", body)
			.query(Long.class)
			.single();
	}

	private void item(long section, int position, long article) {
		this.jdbc.sql("""
				insert into digest_items (digest_section_id, position, article_id)
				values (:section, :position, :article)
				""")
			.param("section", section)
			.param("position", position)
			.param("article", article)
			.update();
	}

	private void sectionEvent(long section, int position, long event, String reason) {
		this.jdbc.sql("""
				insert into digest_section_events (digest_section_id, position, event_id, selection_reason)
				values (:section, :position, :event, :reason)
				""")
			.param("section", section)
			.param("position", position)
			.param("event", event)
			.param("reason", reason)
			.update();
	}

}
