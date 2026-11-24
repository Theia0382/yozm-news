package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.TagSetActivationResult;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.application.TagSetRows;
import net.yozmnews.user.UserTables;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 픽스처 — 기본 체계(버전 1)에서 버전 2 를 켜고 처음 모양으로 되돌린다. 버전 2 는 지역 japan → nippon(계보), china 계보 없이 지움, 형식
 * listicle(기본 제외 · 이름 "목록형")을 새로 들인다.
 */
final class SecondTagSet {

	/** 따라가기 리스너의 이벤트 발행 행 */
	static final String LISTENER = "%TagSetActivationListener%";

	private final TagSetAdministration administration;

	private final JdbcClient jdbc;

	SecondTagSet(TagSetAdministration administration, JdbcClient jdbc) {
		this.administration = administration;
		this.jdbc = jdbc;
	}

	/** 버전 2 를 켠다 — 그 ID */
	TagSetId activate(TagSetId first) {
		TagSetActivationResult result = this.administration.activate(text(first), 0, ChangeActor.OPERATOR);
		assertThat(result).isInstanceOf(TagSetActivationResult.Activated.class);
		return ((TagSetActivationResult.Activated) result).version().id();
	}

	/** 버전 1 의 원문에서 japan → nippon(계보), china 지움, 형식 listicle(기본 제외) 더함 */
	String text(TagSetId first) {
		String text = this.administration.versionText(first).orElseThrow();
		text = text.replaceFirst("  - key: \"china\"\n(    .*\n)+?(?=  - key: )", "");
		text = text.replace("key: \"japan\"", "key: \"nippon\"");
		text = text.replace("importance:\n", """
				  - key: "listicle"
				    name: "목록형"
				    description: "항목을 나열한 기사"
				    excludedByDefault: true
				importance:
				""");
		return text + "lineage:\n  - { kind: region, change: rename, from: [japan], to: [nippon] }\n";
	}

	/** 따라가기 리스너가 끝날 때까지(마치거나 실패할 때까지) 기다린다 — 시험 뒤 버전을 지우기 전에 */
	void awaitListener() {
		long deadline = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < deadline) {
			Long open = this.jdbc.sql("""
					select count(*) from event_publication
					where listener_id like :listener and completion_date is null and coalesce(status, '') <> 'FAILED'
					""").param("listener", LISTENER).query(Long.class).single();
			if (open == 0) {
				return;
			}
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** 계정 · 리스너 발행 · 버전 2 이상을 지우고 버전 1 을 처음 모양으로 */
	void clean() {
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from event_publication where listener_id like :listener")
			.param("listener", LISTENER)
			.update();
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

}
