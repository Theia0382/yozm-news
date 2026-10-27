package net.yozmnews.source.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.junit.jupiter.api.Test;

/**
 * 프리셋 — 운영자 프로필과 같은 검증에 이용조건 검토일이 반드시 있다는 것만 더한다. 프리셋 원문은 그대로 운영자 프로필로 쓸 수 있다.
 */
class PresetTests {

	private static final String PRESET = """
			# 합성 프리셋 — 날짜가 RFC 822 가 아닌 피드라 형식을 더한다
			source:
			  name: 베타뉴스
			  homepage: https://beta.example
			  language: ko
			termsReviewedAt: 2026-09-30
			feeds:
			  - key: politics
			    url: https://beta.example/rss/politics.xml
			    section: 정치
			dates:
			  formats: ["yyyy-MM-dd HH:mm:ss"]
			""";

	private static final String WITHOUT_TERMS = PRESET.replace("termsReviewedAt: 2026-09-30\n", "");

	private final ProfileParser parser = new ProfileParser(new ProfileTextReader());

	private final PresetKey key = new PresetKey("beta-news");

	@Test
	void parsesACompletePreset() {
		Preset preset = parsed(PRESET);

		assertThat(preset.key()).isEqualTo(this.key);
		assertThat(preset.termsReviewedAt()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(preset.text()).isEqualTo(PRESET);
		assertBetaNews(preset.profile());
	}

	@Test
	void requiresATermsReviewDate() {
		assertThat(problems(WITHOUT_TERMS)).containsExactly("termsReviewedAt MISSING_REQUIRED");
		assertThat(problems(PRESET.replace("2026-09-30", "2026-13-01")))
			.containsExactly("termsReviewedAt INVALID_TERMS_REVIEWED_AT");
	}

	@Test
	void validatesAPresetLikeAnOperatorProfile() {
		assertThat(problems(PRESET.replace("  name: 베타뉴스\n", ""))).containsExactly("source.name MISSING_REQUIRED");
		assertThat(problems(PRESET + "preCorrections:\n  - rule: run-script\n"))
			.containsExactly("preCorrections[0].rule UNKNOWN_PRE_CORRECTION");
		assertThat(problems(PRESET + "script: x\n")).containsExactly("script UNKNOWN_KEY");
	}

	@Test
	void letsAnOperatorUseThePresetTextAsItIs() {
		ProfileParse copy = this.parser.parseOperatorProfile(parsed(PRESET).text());

		assertThat(copy).isInstanceOf(ProfileParse.Parsed.class);
		CollectionProfile profile = ((ProfileParse.Parsed) copy).profile();
		assertBetaNews(profile);
		assertThat(profile.termsReviewedAt()).contains(LocalDate.of(2026, 9, 30));
	}

	@Test
	void acceptsAnOperatorProfileWithoutTheTermsReviewDateThatAPresetRequires() {
		assertThat(this.parser.parseOperatorProfile(WITHOUT_TERMS)).isInstanceOf(ProfileParse.Parsed.class);
		assertThat(problems(WITHOUT_TERMS)).containsExactly("termsReviewedAt MISSING_REQUIRED");
	}

	@Test
	void rejectsAPresetBuiltFromAProfileWithoutATermsReviewDate() {
		CollectionProfile withoutTerms = ((ProfileParse.Parsed) this.parser.parseOperatorProfile(WITHOUT_TERMS))
			.profile();

		assertThatIllegalArgumentException().isThrownBy(() -> new Preset(this.key, withoutTerms, WITHOUT_TERMS));
	}

	@Test
	void rejectsAPresetKeyOutsideTheSlugForm() {
		assertThatIllegalArgumentException().isThrownBy(() -> new PresetKey("Bad_Name"));
		assertThatIllegalArgumentException().isThrownBy(() -> new PresetKey(""));
		assertThat(new PresetKey("beta-news").value()).isEqualTo("beta-news");
	}

	/** 날짜 형식은 DateTimeFormatter 끼리 비교할 수 없어(같은 패턴이어도 equals 가 false) 패턴 문자열로 본다 */
	private static void assertBetaNews(CollectionProfile profile) {
		assertThat(profile.source()).isEqualTo(new SourceInfo("베타뉴스", URI.create("https://beta.example"), Locale.KOREAN));
		assertThat(profile.feeds()).containsExactly(
				new FeedSpec(new FeedKey("politics"), URI.create("https://beta.example/rss/politics.xml"), "정치", null));
		assertThat(profile.dates().patterns()).extracting(DatePattern::pattern).containsExactly("yyyy-MM-dd HH:mm:ss");
	}

	private Preset parsed(String text) {
		PresetParse parse = Preset.parse(this.key, text, this.parser);
		assertThat(parse).as("%s", parse).isInstanceOf(PresetParse.Parsed.class);
		return ((PresetParse.Parsed) parse).preset();
	}

	private List<String> problems(String text) {
		PresetParse parse = Preset.parse(this.key, text, this.parser);
		assertThat(parse).isInstanceOf(PresetParse.Invalid.class);
		return ((PresetParse.Invalid) parse).problems().stream().map(problem -> problem.path() + " " + problem.code())
			.toList();
	}

}
