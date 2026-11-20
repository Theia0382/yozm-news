package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.event.EventId;
import net.yozmnews.feedback.DigestItems;
import net.yozmnews.platform.UserId;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 항목 확인 — 주어진 다이제스트가 그 사용자의 것이고 그 사건이 든 항목 섹션이 하나면 그 항목의 사건들(위치 순). 다이제스트의 상태 · 날짜를 보지
 * 않는다.
 * <p>
 * 지키는 규칙: digest-R14
 * <p>
 * 실제 PostgreSQL 위에서 다이제스트 · 섹션 · 섹션 사건 행을 직접 넣는다({@link DigestRows}) — 같은 날짜의 덮인 다이제스트와 한 사건이 든 섹션
 * 둘은 생성 흐름이 만들지 않는 입력이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestItemsTests {

	@Autowired
	JobScheduler digestJobs;

	private static final UserId USER = new UserId(42);

	private static final UserId OTHER = new UserId(43);

	@Autowired
	DigestItems items;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
	}

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@Test
	void anItemGivesItsEventsInPositionOrderWhicheverOfThemIsAsked() {
		long digest = digest(USER, "2026-11-01");
		DigestRows.item(this.jdbc, digest, 903, 901, 902);
		DigestRows.section(this.jdbc, digest, "TOP_STORY", 904);

		assertThat(this.items.itemOf(USER, digest, new EventId(901))).containsExactly(ids(903, 901, 902));
		assertThat(this.items.itemOf(USER, digest, new EventId(903))).containsExactly(ids(903, 901, 902));
		// 주요 뉴스 항목도 항목이다
		assertThat(this.items.itemOf(USER, digest, new EventId(904))).containsExactly(ids(904));
		// 그 다이제스트가 보이지 않은 사건
		assertThat(this.items.itemOf(USER, digest, new EventId(905))).isEmpty();
	}

	@Test
	void anotherUsersDigestIsNotMine() {
		long mine = digest(USER, "2026-11-01");
		DigestRows.item(this.jdbc, mine, 901);
		long theirs = digest(OTHER, "2026-11-01");
		DigestRows.item(this.jdbc, theirs, 901);

		assertThat(this.items.itemOf(USER, theirs, new EventId(901))).isEmpty();
		assertThat(this.items.itemOf(OTHER, mine, new EventId(901))).isEmpty();
		// 대조 — 자기 다이제스트로는 둘 다 항목이 있다
		assertThat(this.items.itemOf(USER, mine, new EventId(901))).containsExactly(ids(901));
		assertThat(this.items.itemOf(OTHER, theirs, new EventId(901))).containsExactly(ids(901));
	}

	@Test
	void aMissingDigestHasNoItem() {
		long digest = digest(USER, "2026-11-01");
		DigestRows.item(this.jdbc, digest, 901);

		// 대조 — 넣은 다이제스트로는 항목이 있다(아래 빈 목록이 섹션 사건 행이 없어서가 아니다)
		assertThat(this.items.itemOf(USER, digest, new EventId(901))).containsExactly(ids(901));
		assertThat(this.items.itemOf(USER, digest + 1000, new EventId(901))).isEmpty();
		assertThat(this.items.itemOf(USER, 0, new EventId(901))).isEmpty();
		assertThat(this.items.itemOf(USER, -1, new EventId(901))).isEmpty();
	}

	@Test
	void anOverviewIsNotAnItem() {
		long digest = digest(USER, "2026-11-01");
		DigestRows.section(this.jdbc, digest, "OVERVIEW", 901);

		assertThat(this.items.itemOf(USER, digest, new EventId(901))).isEmpty();
	}

	@Test
	void anEventInTwoSectionsHasNoItem() {
		long digest = digest(USER, "2026-11-01");
		DigestRows.item(this.jdbc, digest, 901, 902);
		DigestRows.item(this.jdbc, digest, 903, 901);

		assertThat(this.items.itemOf(USER, digest, new EventId(901))).isEmpty();
	}

	@Test
	void anEventInOneSectionNextToAnEventInTwoHasItsItem() {
		// 대조 — 같은 다이제스트에서 섹션 하나에만 든 사건은 그 항목을 찾는다
		long digest = digest(USER, "2026-11-01");
		DigestRows.item(this.jdbc, digest, 901, 902);
		DigestRows.item(this.jdbc, digest, 903, 901);

		assertThat(this.items.itemOf(USER, digest, new EventId(902))).containsExactly(ids(901, 902));
	}

	@Test
	void aDigestCoveredByALaterOneOfTheSameDateStillHasItsItems() {
		// 같은 날짜의 READY 둘 — 작은 ID 는 날짜별로 보이는 다이제스트에 덮였다
		long covered = digest(USER, "2026-11-01");
		DigestRows.item(this.jdbc, covered, 901);
		long shown = digest(USER, "2026-11-01");
		DigestRows.item(this.jdbc, shown, 902);

		assertThat(this.items.itemOf(USER, covered, new EventId(901))).containsExactly(ids(901));
		// 덮인 다이제스트의 항목은 보이는 다이제스트의 항목이 아니다 — 다이제스트 하나만 본다
		assertThat(this.items.itemOf(USER, shown, new EventId(901))).isEmpty();
	}

	private long digest(UserId user, String date) {
		return DigestRows.digest(this.jdbc, user.value(), date);
	}

	private static EventId[] ids(long... values) {
		return Arrays.stream(values).mapToObj(EventId::new).toArray(EventId[]::new);
	}

}
