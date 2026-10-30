package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import net.yozmnews.source.domain.profile.PreCorrection;
import org.junit.jupiter.api.Test;

/**
 * 사전 보정 — 원문 문자열을 직접 넣어 본다.
 * <p>
 * 지키는 규칙: source-R38
 */
class PreCorrectorTests {

	private static final PreCorrection REPLACE = new PreCorrection.ReplaceUndefinedEntities();

	private static final PreCorrection STRIP = new PreCorrection.StripInvalidControlChars();

	@Test
	void replacesHtmlEntitiesAndEscapesTheRest() {
		assertThat(PreCorrector.apply("<t>A&nbsp;B &copy; &foo; R&D &amp; &#169; &#xA9; &lt; &NotEqualTilde;</t>",
				List.of(REPLACE)))
			.isEqualTo("<t>A&#xA0;B &#xA9; &amp;foo; R&amp;D &amp; &#169; &#xA9; &lt; &#x2242;&#x338;</t>");
	}

	@Test
	void repairsAttributeValuesToo() {
		assertThat(PreCorrector.apply("<a href=\"https://a.example/?x=1&y=2&nbsp;\"/>", List.of(REPLACE)))
			.isEqualTo("<a href=\"https://a.example/?x=1&amp;y=2&#xA0;\"/>");
	}

	@Test
	void leavesCdataCommentsAndProcessingInstructionsAlone() {
		String text = "<?xml-stylesheet href=\"a?x=1&y=2\"?><t><![CDATA[A&nbsp;B & C]]><!-- &nbsp; & --></t>";

		assertThat(PreCorrector.apply(text, List.of(REPLACE))).isEqualTo(text);
	}

	@Test
	void stripsInvalidCharactersAndReferences() {
		assertThat(PreCorrector.apply("<t>A\u0001B&#1;C&#x9;D&#xFFFE;E&#xD800;F&#99999999;G\uD800H￿I\t\n</t>",
				List.of(STRIP)))
			.isEqualTo("<t>ABC&#x9;DEFGHI\t\n</t>");
	}

	@Test
	void keepsValidReferencesAndSupplementaryCharacters() {
		assertThat(PreCorrector.apply("<t>&#65;&#x1F600;&#x10FFFF;&#x0041;😀</t>", List.of(STRIP)))
			.isEqualTo("<t>&#65;&#x1F600;&#x10FFFF;&#x0041;😀</t>");
	}

	@Test
	void stripsOnlyRawCharactersInsideCdata() {
		assertThat(PreCorrector.apply("<t><![CDATA[A\u0001&#1;B]]></t>", List.of(STRIP)))
			.isEqualTo("<t><![CDATA[A&#1;B]]></t>");
	}

	@Test
	void appliesTheRulesInProfileOrder() {
		String text = "<t>&nbsp\u0001;</t>";

		assertThat(PreCorrector.apply(text, List.of(STRIP, REPLACE))).isEqualTo("<t>&#xA0;</t>");
		assertThat(PreCorrector.apply(text, List.of(REPLACE, STRIP))).isEqualTo("<t>&amp;nbsp;</t>");
	}

	@Test
	void changesNothingWithoutTextRules() {
		String text = "<t>A&nbsp;B\u0001</t>";

		assertThat(PreCorrector.apply(text, List.of())).isEqualTo(text);
		assertThat(PreCorrector.apply(text, List.of(new PreCorrection.ForceEncoding(StandardCharsets.UTF_8))))
			.isEqualTo(text);
	}

}
