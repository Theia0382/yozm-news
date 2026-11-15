package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.SourceAdministration;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceName;
import net.yozmnews.source.SourceNames;
import net.yozmnews.source.SourceRegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 공개 조회 — 사용자가 고를 소스 목록을 실제 PostgreSQL 과 합성 프리셋 solo(솔로뉴스) · multi(멀티일보)로 본다. 숨기지 않은 소스만, 소스 ID
 * 순이고, 이름은 운영 목록과 같은 해석이다(프로필 → 기록된 출처 이름). 피드 · 수집 상태는 읽지 않는다.
 * <p>
 * 지키는 규칙: source-R51 · source-R53 · source-R57
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
class SourceNamesTests {

	@Autowired
	SourceNames names;

	@Autowired
	SourceAdministration administration;

	@Autowired
	JdbcClient jdbc;

	RegistryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new RegistryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void visibleSourcesInIdOrderWithTheirNamesRegardlessOfEnabled() {
		SourceId solo = registered(this.administration.registerPreset("solo"));
		SourceId multi = registered(this.administration.registerPreset("multi"));
		// 등록한 소스는 꺼져 있다 — 하나만 켜도 둘 다 나온다
		this.jdbc.sql("update sources set enabled = true where id = :id").param("id", solo.value()).update();

		assertThat(this.names.visible())
			.containsExactly(new SourceName(solo, "솔로뉴스"), new SourceName(multi, "멀티일보"));
	}

	@Test
	void aHiddenSourceIsLeftOut() {
		SourceId solo = registered(this.administration.registerPreset("solo"));
		SourceId multi = registered(this.administration.registerPreset("multi"));
		this.rows.hide("sources", multi.value());

		assertThat(this.names.visible()).containsExactly(new SourceName(solo, "솔로뉴스"));
	}

	@Test
	void aSourceThatLostItsPresetGivesTheRecordedNameAndOneWithoutEitherGivesNull() {
		long vanished = this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values ('vanished', true, 0, now()) "
					+ "returning id")
			.query(Long.class)
			.single();
		this.jdbc.sql("insert into source_attributions (source_id, name, recorded_at) values (:source, '사라진일보', now())")
			.param("source", vanished)
			.update();
		long nameless = this.jdbc
			.sql("insert into sources (profile, enabled, version, created_at) values ('source:\n  name: 이름만\n', false, 0, "
					+ "now()) returning id")
			.query(Long.class)
			.single();

		assertThat(this.names.visible()).containsExactly(new SourceName(new SourceId(vanished), "사라진일보"),
				new SourceName(new SourceId(nameless), null));
	}

	@Test
	void aFeedWithoutItsPollStateRowDoesNotStopTheNames() {
		SourceId solo = registered(this.administration.registerPreset("solo"));
		// 수집 상태 행이 없는 피드 — 운영 목록은 이 불변식 위반에서 멈추지만 고를 소스 목록은 피드를 읽지 않는다
		this.jdbc.sql("delete from feed_poll_states where feed_id in (select id from feeds where source_id = :source)")
			.param("source", solo.value())
			.update();

		assertThat(this.names.visible()).containsExactly(new SourceName(solo, "솔로뉴스"));
	}

	@Test
	void noSourcesGiveAnEmptyList() {
		assertThat(this.names.visible()).isEmpty();
	}

	private static SourceId registered(SourceRegistrationResult result) {
		assertThat(result).isInstanceOf(SourceRegistrationResult.Registered.class);
		return ((SourceRegistrationResult.Registered) result).id();
	}

}
