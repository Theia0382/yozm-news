package net.yozmnews.source.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 운영자 원문 수정의 판정 — 읽기 설정이 바뀐 피드와 검토일이 사라진 소스만 끈다.
 * <p>
 * 지키는 규칙: source-R4 · source-R9 · source-R50
 */
class ProfileRevisionTests {

	private static final FeedKey MAIN = new FeedKey("main");

	private static final String BEFORE = """
			source:
			  name: 운영자일보
			  homepage: https://operator.example
			  language: ko
			termsReviewedAt: 2026-10-01
			pollIntervalMinutes: 30
			feeds:
			  - key: main
			    url: https://operator.example/main.xml
			    section: 전체
			    pollIntervalMinutes: 20
			preCorrections:
			  - rule: replace-undefined-entities
			items:
			  author: ["ex:writer"]
			namespaces:
			  ex: http://example.com/ns/
			dates:
			  formats: ["yyyy-MM-dd HH:mm:ss"]
			  timeZone: Asia/Seoul
			""";

	private static final ProfileResolution RESOLVED = new ProfileResolution.Resolved(RegistryFixtures.profile(BEFORE));

	@ParameterizedTest
	@ValueSource(strings = { "url: https://operator.example/main.xml|url: https://operator.example/main-v2.xml",
			"language: ko|language: en",
			"rule: replace-undefined-entities|rule: strip-invalid-control-chars",
			"author: [\"ex:writer\"]|author: [\"ex:byline\"]",
			"ex: http://example.com/ns/|ex: http://example.com/other/",
			"yyyy-MM-dd HH:mm:ss|yyyy.MM.dd HH:mm",
			"timeZone: Asia/Seoul|timeZone: Asia/Tokyo" })
	void aChangedReadingTurnsTheFeedOff(String change) {
		assertThat(ProfileRevision.keepsEnabled(RESOLVED, revised(change), MAIN)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "name: 운영자일보|name: 새운영자일보",
			"homepage: https://operator.example|homepage: https://operator.example/home",
			"section: 전체|section: 종합", "pollIntervalMinutes: 20|pollIntervalMinutes: 45",
			"pollIntervalMinutes: 30|pollIntervalMinutes: 60",
			"termsReviewedAt: 2026-10-01|termsReviewedAt: 2026-10-30" })
	void aChangeOutsideTheReadingKeepsTheFeedOn(String change) {
		assertThat(ProfileRevision.keepsEnabled(RESOLVED, revised(change), MAIN)).isTrue();
	}

	@Test
	void renamingANamespacePrefixWithTheSameUriKeepsTheFeedOn() {
		CollectionProfile renamed = RegistryFixtures
			.profile(BEFORE.replace("[\"ex:writer\"]", "[\"by:writer\"]").replace("ex: http://example.com/ns/",
					"by: http://example.com/ns/"));

		assertThat(ProfileRevision.keepsEnabled(RESOLVED, renamed, MAIN)).isTrue();
	}

	@Test
	void theSameTextValidatedTwiceKeepsTheFeedOnEvenWithDateFormats() {
		assertThat(ProfileRevision.keepsEnabled(new ProfileResolution.Resolved(RegistryFixtures.profile(BEFORE)),
				RegistryFixtures.profile(BEFORE), MAIN))
			.isTrue();
	}

	@Test
	void theSameTextValidatedTwiceKeepsTheFeedOnWithoutDateFormats() {
		String withoutDates = BEFORE.replace("""
				dates:
				  formats: ["yyyy-MM-dd HH:mm:ss"]
				  timeZone: Asia/Seoul
				""", "");
		assertThat(withoutDates).doesNotContain("dates:");

		assertThat(ProfileRevision.keepsEnabled(new ProfileResolution.Resolved(RegistryFixtures.profile(withoutDates)),
				RegistryFixtures.profile(withoutDates), MAIN))
			.isTrue();
	}

	@Test
	void aFeedDroppedFromTheTextTurnsOff() {
		CollectionProfile withoutMain = RegistryFixtures.profile(BEFORE.replace("key: main", "key: other"));

		assertThat(ProfileRevision.keepsEnabled(RESOLVED, withoutMain, MAIN)).isFalse();
	}

	@Test
	void aFeedNewInTheTextHadNoReadingBeforeAndTurnsOff() {
		CollectionProfile renamedKey = RegistryFixtures.profile(BEFORE.replace("key: main", "key: other"));

		assertThat(ProfileRevision.keepsEnabled(new ProfileResolution.Resolved(renamedKey),
				RegistryFixtures.profile(BEFORE), MAIN))
			.isFalse();
	}

	@Test
	void anUnresolvableBeforeTurnsEveryFeedOff() {
		CollectionProfile after = RegistryFixtures.profile(BEFORE);

		assertThat(ProfileRevision.keepsEnabled(new ProfileResolution.Invalid(List.of()), after, MAIN)).isFalse();
		assertThat(ProfileRevision.keepsEnabled(new ProfileResolution.PresetMissing(new PresetKey("gone")), after,
				MAIN))
			.isFalse();
	}

	@Test
	void theSourceStaysOnOnlyWithATermsDate() {
		assertThat(ProfileRevision.keepsSourceEnabled(RegistryFixtures.profile(BEFORE))).isTrue();
		assertThat(ProfileRevision
			.keepsSourceEnabled(RegistryFixtures.profile(BEFORE.replace("termsReviewedAt: 2026-10-01\n", ""))))
			.isFalse();
	}

	/** "옛 문구|새 문구" 하나를 바꾼 원문의 프로필 */
	private static CollectionProfile revised(String change) {
		String[] parts = change.split("\\|", 2);
		assertThat(BEFORE).contains(parts[0]);
		return RegistryFixtures.profile(BEFORE.replace(parts[0], parts[1]));
	}

}
