package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;

import net.yozmnews.source.domain.feed.FeedDocument;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.feed.FeedNormalizer;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.gate.FeedGate;
import net.yozmnews.source.domain.gate.PreviewOutcome;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 프리셋 시험의 도우미 — 저장소의 프리셋을 적재 경로 그대로 읽고, 합성 피드({@code src/test/resources/source-presets-fixtures/})를 그
 * 프로필로 정규화해 관문에 넣는다.
 */
final class PresetFixtures {

	/** 픽스처의 발행 시각보다 뒤 — 미래 검사에 걸리지 않는다 */
	static final Instant FETCHED_AT = Instant.parse("2026-10-31T09:00:00Z");

	private PresetFixtures() {
	}

	/** 저장소 프리셋 디렉터리의 프리셋 하나 */
	static Preset preset(String key) {
		return PresetCatalogConfiguration
			.load(new PathMatchingResourcePatternResolver(), PresetCatalogConfiguration.LOCATION,
					new ProfileParser(new ProfileTextReader()))
			.find(new PresetKey(key))
			.orElseThrow(() -> new AssertionError("저장소에 프리셋이 없다: " + key));
	}

	static byte[] bytes(String key, String fixture) {
		String path = "/source-presets-fixtures/" + key + "/" + fixture + ".xml";
		try (InputStream in = PresetFixtures.class.getResourceAsStream(path)) {
			assertThat(in).as("픽스처 %s", path).isNotNull();
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 픽스처를 주어진 프로필로 정규화한다 — 받은 시각은 {@link #FETCHED_AT} */
	static FeedNormalization.Normalized normalized(byte[] body, CollectionProfile profile) {
		FeedNormalization result = new FeedNormalizer().normalize(new FeedDocument(body, null, FETCHED_AT), profile);
		assertThat(result).as("%s", result).isInstanceOf(FeedNormalization.Normalized.class);
		return (FeedNormalization.Normalized) result;
	}

	/** 픽스처를 그 프리셋의 프로필로 정규화한다 */
	static FeedNormalization.Normalized normalized(String key, String fixture) {
		return normalized(bytes(key, fixture), preset(key).profile());
	}

	/** 정규화 결과를 그 프로필로 관문에 넣는다 */
	static PreviewOutcome preview(FeedNormalization.Normalized normalized, CollectionProfile profile) {
		return FeedGate.preview(new FetchResult.Fetched(normalized, FetchState.NONE, FETCHED_AT, null), profile);
	}

	/** 픽스처를 그 프리셋으로 정규화해 관문에 넣는다 */
	static PreviewOutcome preview(String key, String fixture) {
		CollectionProfile profile = preset(key).profile();
		return preview(normalized(bytes(key, fixture), profile), profile);
	}

}
