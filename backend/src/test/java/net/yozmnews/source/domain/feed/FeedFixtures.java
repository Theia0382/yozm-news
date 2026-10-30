package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;

/**
 * 정규화 테스트의 합성 피드({@code src/test/resources/feeds/}) · 운영자 프로필 · 받은 시각.
 */
public final class FeedFixtures {

	/** 픽스처의 발행 시각보다 뒤 — 미래 검사에 걸리지 않는다 */
	public static final Instant FETCHED_AT = Instant.parse("2026-10-26T00:00:00Z");

	private static final String BASE_PROFILE = """
			source:
			  name: 알파일보
			  homepage: https://alpha.example
			  language: ko
			feeds:
			  - key: all
			    url: https://alpha.example/rss.xml
			    section: 전체
			""";

	private FeedFixtures() {
	}

	static byte[] bytes(String name) {
		try (InputStream in = FeedFixtures.class.getResourceAsStream("/feeds/" + name + ".xml")) {
			assertThat(in).as("픽스처 %s", name).isNotNull();
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** @param extra 기본 프로필 뒤에 붙일 최상위 항목(YAML) */
	public static CollectionProfile profile(String extra) {
		ProfileParse parse = new ProfileParser(new ProfileTextReader()).parseOperatorProfile(BASE_PROFILE + extra);
		assertThat(parse).as("%s", parse).isInstanceOf(ProfileParse.Parsed.class);
		return ((ProfileParse.Parsed) parse).profile();
	}

	public static FeedNormalization normalize(byte[] body, String profile) {
		return new FeedNormalizer().normalize(new FeedDocument(body, null, FETCHED_AT), profile(profile));
	}

	public static FeedNormalization.Normalized normalized(String name, String profile) {
		FeedNormalization result = normalize(bytes(name), profile);
		assertThat(result).as("%s", result).isInstanceOf(FeedNormalization.Normalized.class);
		return (FeedNormalization.Normalized) result;
	}

	static FeedNormalization.Unreadable unreadable(String name, String profile) {
		FeedNormalization result = normalize(bytes(name), profile);
		assertThat(result).as("%s", result).isInstanceOf(FeedNormalization.Unreadable.class);
		return (FeedNormalization.Unreadable) result;
	}

}
