package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.DocumentProblemKind;
import net.yozmnews.source.domain.profile.PreCorrection;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 문자 인코딩 판정과 디코딩 — 바이트를 직접 만들어 본다.
 * <p>
 * 지키는 규칙: source-R38 · source-R42
 */
class DocumentDecoderTests {

	private static final Instant FETCHED_AT = Instant.parse("2026-10-25T00:00:00Z");

	/** "똠방각하" 의 windows-949 바이트 — 똠(8C 63)은 KS X 1001 밖이고, UTF-8 로는 읽을 수 없다 */
	private static final byte[] DDOM_BANG_GAK_HA_949 = bytes(0x8C, 0x63, 0xB9, 0xE6, 0xB0, 0xA2, 0xC7, 0xCF);

	/** "똠" 의 UTF-8 바이트 */
	private static final byte[] DDOM_UTF_8 = bytes(0xEB, 0x98, 0xA0);

	/** " 뷁" 의 windows-949 바이트 */
	private static final byte[] SPACE_BWEOLG_949 = bytes(0x20, 0x94, 0xEE);

	@Test
	void forcedEncodingWinsOverTheDeclaration() {
		byte[] body = concat(ascii("<?xml version=\"1.0\" encoding=\"utf-8\"?><r>"), DDOM_BANG_GAK_HA_949, ascii("</r>"));

		assertThat(text(body, null, new PreCorrection.ForceEncoding(Charset.forName("EUC-KR"))))
			.isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?><r>똠방각하</r>");
	}

	@Test
	void byteOrderMarkWinsOverTheDeclarationAndIsDropped() {
		byte[] body = concat(bytes(0xEF, 0xBB, 0xBF), ascii("<?xml version=\"1.0\" encoding=\"euc-kr\"?><r>"), DDOM_UTF_8,
				ascii("</r>"));

		assertThat(text(body, null)).isEqualTo("<?xml version=\"1.0\" encoding=\"euc-kr\"?><r>똠</r>");
	}

	@Test
	void readsUtf16ByItsByteOrderMark() {
		byte[] body = concat(bytes(0xFF, 0xFE), "<r>똠</r>".getBytes(StandardCharsets.UTF_16LE));

		assertThat(text(body, null)).isEqualTo("<r>똠</r>");
	}

	@Test
	void declarationWinsOverTheTransportCharset() {
		byte[] body = concat(ascii("<?xml version='1.0' encoding='euc-kr'?><r>"), DDOM_BANG_GAK_HA_949, ascii("</r>"));

		assertThat(text(body, "utf-8")).isEqualTo("<?xml version='1.0' encoding='euc-kr'?><r>똠방각하</r>");
	}

	@Test
	void transportCharsetFillsAMissingDeclaration() {
		byte[] body = concat(ascii("<r>"), DDOM_BANG_GAK_HA_949, ascii("</r>"));

		assertThat(text(body, "euc-kr")).isEqualTo("<r>똠방각하</r>");
		// 대조: 헤더가 없으면 UTF-8 로 읽어 같은 바이트가 깨진다 — 8C · B9 · C7 · CF 가 각각 한 자리(E6 B0 A2 는 U+6C22)
		assertThat(decoded(body, null).replacedCharacters()).isEqualTo(4);
	}

	@Test
	void defaultsToUtf8() {
		assertThat(text(concat(ascii("<r>"), DDOM_UTF_8, ascii("</r>")), null)).isEqualTo("<r>똠</r>");
	}

	@Test
	void failsOnAnUnsupportedDeclaredEncoding() {
		DocumentDecoder.Decoded decoded = DocumentDecoder.decode(
				new FeedDocument(ascii("<?xml version=\"1.0\" encoding=\"x-unknown\"?><r/>"), "utf-8", FETCHED_AT),
				List.of());

		assertThat(decoded).isEqualTo(new DocumentDecoder.Decoded.Failed(new DocumentProblem(
				DocumentProblemKind.UNSUPPORTED_ENCODING, "문서가 선언한 문자 인코딩을 지원하지 않는다: x-unknown", null, null)));
	}

	@Test
	void ignoresAnUnsupportedTransportCharset() {
		assertThat(text(concat(ascii("<r>"), DDOM_UTF_8, ascii("</r>")), "x-unknown")).isEqualTo("<r>똠</r>");
	}

	/**
	 * Java 가 EUC-KR · IBM949 의 별칭으로 아는 레이블(euc-kr · KS_C_5601-1987 · cp949)과 Java 가 모르는 WHATWG 레이블(korean ·
	 * iso-ir-149 · csksc56011987 · ks_c_5601-1989)
	 */
	@ParameterizedTest
	@ValueSource(strings = { "euc-kr", "KS_C_5601-1987", "cp949", "korean", "iso-ir-149", "csksc56011987",
			"ks_c_5601-1989" })
	void readsKoreanLabelsAsWindows949(String label) {
		byte[] body = concat(ascii("<?xml version=\"1.0\" encoding=\"" + label + "\"?><r>"), DDOM_BANG_GAK_HA_949,
				SPACE_BWEOLG_949, ascii("</r>"));

		assertThat(text(body, null)).endsWith("<r>똠방각하 뷁</r>");
	}

	@Test
	void plainEucKrWouldBreakTheExtendedHangul() {
		// 대조 — Java 의 EUC-KR 로 그대로 읽으면 KS X 1001 밖의 한글이 깨진다(windows-949 로 읽는 이유)
		String plain = new String(concat(DDOM_BANG_GAK_HA_949, SPACE_BWEOLG_949), Charset.forName("EUC-KR"));

		assertThat(plain).isEqualTo("�c방각하 ��");
	}

	@Test
	void forcedEucKrAndCp949AlsoReadAsWindows949() {
		byte[] body = concat(ascii("<r>"), DDOM_BANG_GAK_HA_949, ascii("</r>"));

		assertThat(text(body, null, new PreCorrection.ForceEncoding(Charset.forName("CP949")))).isEqualTo("<r>똠방각하</r>");
	}

	@Test
	void replacesUndecodableBytesAndCountsThem() {
		DocumentDecoder.Decoded.Text decoded = decoded(concat(ascii("<r>A"), bytes(0xFF), ascii("B"), bytes(0xFF),
				ascii("C</r>")), null);

		assertThat(decoded.text()).isEqualTo("<r>A�B�C</r>");
		assertThat(decoded.replacedCharacters()).isEqualTo(2);
	}

	@Test
	void countsNothingOnAValidDocument() {
		assertThat(decoded(concat(ascii("<r>"), DDOM_UTF_8, ascii("</r>")), null).replacedCharacters()).isZero();
	}

	private static String text(byte[] body, @Nullable String transportCharset, PreCorrection... corrections) {
		return decoded(body, transportCharset, corrections).text();
	}

	private static DocumentDecoder.Decoded.Text decoded(byte[] body, @Nullable String transportCharset,
			PreCorrection... corrections) {
		DocumentDecoder.Decoded decoded = DocumentDecoder
			.decode(new FeedDocument(body, transportCharset, FETCHED_AT), List.of(corrections));
		assertThat(decoded).isInstanceOf(DocumentDecoder.Decoded.Text.class);
		return (DocumentDecoder.Decoded.Text) decoded;
	}

	static byte[] bytes(int... values) {
		byte[] bytes = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			bytes[i] = (byte) values[i];
		}
		return bytes;
	}

	static byte[] ascii(String text) {
		return text.getBytes(StandardCharsets.US_ASCII);
	}

	static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] part : parts) {
			out.writeBytes(part);
		}
		return out.toByteArray();
	}

}
