package net.yozmnews.event.domain.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.lucene.analysis.ko.POS;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * 핵심 말 — Nori(분해 없음)의 고유명사 · 일반명사 · 외국어, 한 글자 한글 일반명사 제외, 외국어 소문자, 처음 나온 순서 · 같은 말 한 번.
 * <p>
 * 기대값은 Lucene 10.6.0 Nori 의 토큰 · 품사 실측에 규칙을 손으로 적용한 리터럴이다. 시험 문장은 지어낸 것이다.
 * <p>
 * 지키는 규칙: event-R2
 */
class KeyTermExtractorTests {

	private static final KeyTermExtractor extractor = new KeyTermExtractor();

	@AfterAll
	static void close() {
		extractor.close();
	}

	@Test
	void aCompoundProperNounStaysOneTermAndForeignWordsAreLowercased() {
		// 삼성전자/NNP(복합) 3/SN 분기/NNG 영업/NNG 이익/NNG 9/SN 조/NR 원/NNBC HBM/SL 판매/NNG 호조/NNG
		assertThat(extractor.extract("삼성전자, 3분기 영업이익 9조원…HBM 판매 호조"))
			.containsExactly("삼성전자", "분기", "영업", "이익", "hbm", "판매", "호조");
	}

	@Test
	void hanjaAbbreviationsAreKeptAndASingleHangulCommonNounIsDropped() {
		// 尹/NNG 대통령/NNG 美/NNG 국무/NNG 장관/NNG 과/JC 북/NNP 핵/NNG 대응/NNG 논의/NNG
		assertThat(extractor.extract("尹 대통령, 美 국무장관과 북핵 대응 논의"))
			.containsExactly("尹", "대통령", "美", "국무", "장관", "북", "대응", "논의");
	}

	@Test
	void aSingleHangulProperNounStaysWhileASingleHangulCommonNounGoes() {
		List<String> terms = extractor.extract("尹 대통령, 美 국무장관과 북핵 대응 논의");

		assertThat(terms).contains("북").doesNotContain("핵");
	}

	@Test
	void aTitleWithoutProperNounsKeepsItsCommonNouns() {
		// 오늘/NNG 날씨/NNG 맑/VA 음/ETN 전국/NNG 대체로/MAG 화창/NNG
		assertThat(extractor.extract("\"오늘 날씨 맑음\" 전국 대체로 화창")).containsExactly("오늘", "날씨", "전국", "화창");
	}

	@Test
	void aTextWithoutNounsHasNoTerms() {
		// 맑/VA 고/EC 화창/XR 하/XSA 다/EC
		assertThat(extractor.extract("맑고 화창하다")).isEmpty();
	}

	@Test
	void aRepeatedTermIsKeptOnceInFirstAppearanceOrder() {
		assertThat(extractor.extract("금리 동결, 금리 인하")).containsExactly("금리", "동결", "인하");
	}

	@Test
	void theKeptTagsCannotBeChanged() {
		assertThat(KeyTermExtractor.KEPT_TAGS).containsExactlyInAnyOrder(POS.Tag.NNP, POS.Tag.NNG, POS.Tag.SL);
		assertThatThrownBy(() -> KeyTermExtractor.KEPT_TAGS.add(POS.Tag.VV))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void threadsSharingOneExtractorGetTheSameTerms() throws Exception {
		String text = "삼성전자, 3분기 영업이익 9조원…HBM 판매 호조";
		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			List<Callable<List<String>>> tasks = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				tasks.add(() -> extractor.extract(text));
			}
			for (Future<List<String>> result : executor.invokeAll(tasks)) {
				assertThat(result.get()).containsExactly("삼성전자", "분기", "영업", "이익", "hbm", "판매", "호조");
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

}
