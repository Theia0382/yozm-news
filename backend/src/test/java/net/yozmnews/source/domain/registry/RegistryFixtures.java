package net.yozmnews.source.domain.registry;

import java.time.Instant;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.PresetParse;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;

/** 등록 · 켜기 테스트의 프로필 */
final class RegistryFixtures {

	static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");

	static final ProfileParser PARSER = new ProfileParser(new ProfileTextReader());

	private static final String SOURCE = """
			source:
			  name: 합성일보
			  homepage: https://synthetic.example
			  language: ko
			""";

	private static final String FEEDS = """
			feeds:
			  - key: one
			    url: https://synthetic.example/rss/one.xml
			    section: 하나
			  - key: two
			    url: https://synthetic.example/rss/two.xml
			    section: 둘
			    pollIntervalMinutes: 30
			""";

	/** 이용조건 검토일이 없는 운영자 프로필 원문 — 피드 one · two */
	static final String UNREVIEWED_TEXT = SOURCE + FEEDS;

	/** 이용조건 검토일이 있는 운영자 프로필 원문 — 피드 one · two */
	static final String REVIEWED_TEXT = SOURCE + "termsReviewedAt: 2026-10-01\n" + FEEDS;

	/** 검증에 걸리는 원문 — 필수 항목이 없다 */
	static final String INVALID_TEXT = "source:\n  name: 이름만\n";

	private RegistryFixtures() {
	}

	static CollectionProfile profile(String text) {
		return ((ProfileParse.Parsed) PARSER.parseOperatorProfile(text)).profile();
	}

	static Preset preset(String key, String text) {
		return ((PresetParse.Parsed) Preset.parse(new PresetKey(key), text, PARSER)).preset();
	}

}
