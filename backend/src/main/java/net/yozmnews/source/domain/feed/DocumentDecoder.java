package net.yozmnews.source.domain.feed;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yozmnews.source.domain.profile.PreCorrection;
import org.jspecify.annotations.Nullable;

/**
 * 피드 바이트 → 문자열. 문자 인코딩은 프로필의 {@code force-encoding} → BOM → XML 선언 → 전송 계층 charset → UTF-8 순으로
 * 정한다. 읽을 수 없는 바이트는 U+FFFD 로 바꾸고 그 수를 센다.
 */
final class DocumentDecoder {

	/** XML 선언을 찾는 앞부분 */
	private static final int DECLARATION_WINDOW = 1_024;

	private static final Pattern DECLARATION = Pattern
		.compile("^<\\?xml\\s[^>]*?\\bencoding\\s*=\\s*([\"'])([^\"']*)\\1");

	private static final char REPLACEMENT = '\uFFFD';

	private static final char BYTE_ORDER_MARK = '\uFEFF';

	sealed interface Decoded {

		/** @param replacedCharacters U+FFFD 로 바꾼 자리 수 */
		record Text(String text, int replacedCharacters) implements Decoded {

			public Text {
				Objects.requireNonNull(text, "text");
			}

		}

		record Failed(DocumentProblem problem) implements Decoded {

			public Failed {
				Objects.requireNonNull(problem, "problem");
			}

		}

	}

	private DocumentDecoder() {
	}

	static Decoded decode(FeedDocument document, List<PreCorrection> corrections) {
		byte[] bytes = document.body();
		Optional<Charset> forced = forced(corrections);
		if (forced.isPresent()) {
			return textOf(bytes, 0, forced.get());
		}
		Bom bom = Bom.of(bytes);
		if (bom != null) {
			return textOf(bytes, bom.length, bom.charset);
		}
		String declared = declaredEncoding(bytes);
		if (declared != null) {
			Optional<Charset> charset = KoreanCharsets.forLabel(declared);
			if (charset.isEmpty()) {
				return new Decoded.Failed(DocumentProblem.of(DocumentProblemKind.UNSUPPORTED_ENCODING,
						"문서가 선언한 문자 인코딩을 지원하지 않는다: " + declared));
			}
			return textOf(bytes, 0, charset.get());
		}
		// 전송 계층 값은 선택 정보라, 읽을 수 없는 레이블이면 버리고 기본값으로 간다
		Charset charset = document.transportCharset()
			.flatMap(KoreanCharsets::forLabel)
			.orElse(StandardCharsets.UTF_8);
		return textOf(bytes, 0, charset);
	}

	/** 사전 보정 규칙은 프로필 안에서 한 번씩만 나온다 */
	private static Optional<Charset> forced(List<PreCorrection> corrections) {
		for (PreCorrection correction : corrections) {
			Optional<Charset> charset = switch (correction) {
				case PreCorrection.ForceEncoding force -> Optional.of(KoreanCharsets.effective(force.charset()));
				case PreCorrection.ReplaceUndefinedEntities replace -> Optional.empty();
				case PreCorrection.StripInvalidControlChars strip -> Optional.empty();
			};
			if (charset.isPresent()) {
				return charset;
			}
		}
		return Optional.empty();
	}

	private static @Nullable String declaredEncoding(byte[] bytes) {
		String head = new String(bytes, 0, Math.min(bytes.length, DECLARATION_WINDOW), StandardCharsets.ISO_8859_1);
		Matcher matcher = DECLARATION.matcher(head);
		return matcher.find() ? matcher.group(2) : null;
	}

	private static Decoded.Text textOf(byte[] bytes, int offset, Charset charset) {
		CharsetDecoder decoder = charset.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
		ByteBuffer in = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
		CharBuffer out = CharBuffer.allocate(Math.max(16, (int) (in.remaining() * decoder.averageCharsPerByte()) + 16));
		StringBuilder text = new StringBuilder(out.capacity());
		int replaced = 0;
		while (true) {
			CoderResult result = decoder.decode(in, out, true);
			if (result.isError()) {
				drain(out, text);
				text.append(REPLACEMENT);
				replaced++;
				in.position(in.position() + result.length());
			}
			else if (result.isOverflow()) {
				drain(out, text);
			}
			else {
				break;
			}
		}
		while (decoder.flush(out).isOverflow()) {
			drain(out, text);
		}
		drain(out, text);
		if (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) {
			text.deleteCharAt(0);
		}
		return new Decoded.Text(text.toString(), replaced);
	}

	private static void drain(CharBuffer out, StringBuilder text) {
		out.flip();
		text.append(out);
		out.clear();
	}

	private enum Bom {

		UTF_8(StandardCharsets.UTF_8, 0xEF, 0xBB, 0xBF),
		UTF_16BE(StandardCharsets.UTF_16BE, 0xFE, 0xFF),
		UTF_16LE(StandardCharsets.UTF_16LE, 0xFF, 0xFE);

		private final Charset charset;

		private final int[] marker;

		private final int length;

		Bom(Charset charset, int... marker) {
			this.charset = charset;
			this.marker = marker;
			this.length = marker.length;
		}

		static @Nullable Bom of(byte[] bytes) {
			for (Bom bom : values()) {
				if (bom.matches(bytes)) {
					return bom;
				}
			}
			return null;
		}

		private boolean matches(byte[] bytes) {
			if (bytes.length < this.marker.length) {
				return false;
			}
			for (int i = 0; i < this.marker.length; i++) {
				if ((bytes[i] & 0xFF) != this.marker[i]) {
					return false;
				}
			}
			return true;
		}

	}

}
