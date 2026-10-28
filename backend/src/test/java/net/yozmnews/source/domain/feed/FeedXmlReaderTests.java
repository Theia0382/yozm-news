package net.yozmnews.source.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import net.yozmnews.source.domain.profile.PreCorrection;
import org.jdom2.Document;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 피드 XML 읽기 — 외부 엔티티 · DTD 를 해석하지 않는다(AC3). 디코딩 · 사전 보정을 거친 원문을 읽는다.
 */
class FeedXmlReaderTests {

	private static final String HEAD = "<rss version=\"2.0\"><channel><title>t</title><item><title>";

	private static final String TAIL = "</title></item></channel></rss>";

	private static final String NETSCAPE_DOCTYPE = "<!DOCTYPE rss PUBLIC \"-//Netscape Communications//DTD RSS 0.91//EN\" "
			+ "\"http://my.netscape.com/publish/formats/rss-0.91.dtd\">";

	private static final String RSS_091 = "<?xml version=\"1.0\"?>\n" + NETSCAPE_DOCTYPE
			+ "<rss version=\"0.91\"><channel><title>t</title><item><title>A</title></item></channel></rss>";

	@Test
	void readsAPlainDocument() {
		assertThat(itemTitle(parsed(HEAD + "A &amp; B" + TAIL))).isEqualTo("A & B");
	}

	@Test
	void rejectsAnExternalFileEntity() {
		assertThat(failed("<?xml version=\"1.0\"?>\n<!DOCTYPE rss [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>" + HEAD
				+ "X&xxe;Y" + TAIL))
			.isEqualTo(dtd("외부 엔티티 선언 xxe", 2, 59));
	}

	@Test
	void rejectsAnInternalEntity() {
		assertThat(failed("<?xml version=\"1.0\"?>\n<!DOCTYPE rss [<!ENTITY a \"AAAA\">]>" + HEAD + "X&a;Y" + TAIL))
			.isEqualTo(dtd("엔티티 선언 a", 2, 34));
	}

	@Test
	void rejectsAnEntityExpansionBomb() {
		assertThat(failed("<?xml version=\"1.0\"?>\n<!DOCTYPE rss [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;\">]>"
				+ HEAD + "&b;" + TAIL))
			.isEqualTo(dtd("엔티티 선언 a", 2, 40));
	}

	@Test
	void rejectsAnExternalParameterEntity() {
		assertThat(failed("<?xml version=\"1.0\"?>\n<!DOCTYPE rss [<!ENTITY % p SYSTEM \"http://127.0.0.1:9/x.dtd\"> %p;]>"
				+ HEAD + "A" + TAIL))
			.isEqualTo(dtd("외부 엔티티 선언 %p", 2, 63));
	}

	@Test
	void rejectsElementAndAttributeDeclarations() {
		assertThat(failed("<!DOCTYPE rss [<!ELEMENT rss ANY>]>" + HEAD + "A" + TAIL).message())
			.isEqualTo("DTD 선언과 선언되지 않은 엔티티는 받지 않는다: 요소 선언 rss");
		assertThat(failed("<!DOCTYPE rss [<!ATTLIST rss foo CDATA \"bar\">]>" + HEAD + "A" + TAIL).message())
			.isEqualTo("DTD 선언과 선언되지 않은 엔티티는 받지 않는다: 속성 선언 foo");
	}

	@Test
	void doesNotReadAnExternalDtd(@TempDir Path directory) throws IOException {
		Path dtd = directory.resolve("x.dtd");
		Files.writeString(dtd, "<!ENTITY x \"SECRET-18\">", StandardCharsets.UTF_8);
		String text = "<?xml version=\"1.0\"?>\n<!DOCTYPE rss SYSTEM \"" + dtd.toUri() + "\">\n<rss><t>A&x;B</t></rss>";

		DocumentProblem problem = failed(text);

		assertThat(problem).isEqualTo(dtd("선언되지 않은 엔티티 x", 3, 13));
		assertThat(problem.message()).doesNotContain("SECRET-18");
		// 픽스처 판별력 — 외부 DTD 를 읽는 JDK 기본 파서는 같은 문서에서 비밀 문자열을 펼친다
		assertThat(readWithDefaultParser(text)).isEqualTo("ASECRET-18B");
	}

	@Test
	void doesNotTryAMissingExternalDtd(@TempDir Path directory) {
		String text = "<!DOCTYPE rss SYSTEM \"" + directory.resolve("missing.dtd").toUri() + "\"><rss><t>A&x;B</t></rss>";

		// 위치는 참조가 끝난 바로 다음 열이다
		assertThat(failed(text)).isEqualTo(dtd("선언되지 않은 엔티티 x", 1, text.indexOf("&x;") + 4));
	}

	@Test
	void readsADoctypeWithoutDeclarations() {
		assertThat(itemTitle(parsed("<!DOCTYPE rss []>" + HEAD + "A" + TAIL))).isEqualTo("A");
		assertThat(itemTitle(parsed("<!DOCTYPE rss [<!-- 주석 -->]>" + HEAD + "A" + TAIL))).isEqualTo("A");
		Document document = parsed(RSS_091);
		assertThat(itemTitle(document)).isEqualTo("A");
		assertThat(document.getDocType().getPublicID()).isEqualTo("-//Netscape Communications//DTD RSS 0.91//EN");
	}

	@Test
	void isNotAffectedByTheSharedJdomParserFactory() {
		SAXBuilder shared = new SAXBuilder(XMLReaders.NONVALIDATING);
		shared.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		// 대조 — 공유 팩토리로 만든 빌더는 이 설정 뒤 DOCTYPE 을 거부한다
		assertThatThrownBy(() -> shared.build(new StringReader(RSS_091))).isInstanceOf(JDOMParseException.class);

		assertThat(itemTitle(parsed(RSS_091))).isEqualTo("A");
	}

	@Test
	void undefinedEntityStopsTheDocumentWithOrWithoutADoctype() {
		assertThat(failed(HEAD + "A&nbsp;B" + TAIL))
			.isEqualTo(new DocumentProblem(DocumentProblemKind.MALFORMED_XML, "XML 형식이 틀렸다", 1, 65));
		assertThat(failed(NETSCAPE_DOCTYPE + "\n" + HEAD + "A&nbsp;B" + TAIL)).isEqualTo(dtd("선언되지 않은 엔티티 nbsp", 2, 65));
	}

	@Test
	void replaceUndefinedEntitiesFixesTheUndefinedEntity() {
		String text = NETSCAPE_DOCTYPE + HEAD + "A&nbsp;B" + TAIL;

		assertThat(itemTitle(parsed(PreCorrector.apply(text, List.of(new PreCorrection.ReplaceUndefinedEntities())))))
			.isEqualTo("A B");
	}

	@Test
	void reportsMalformedXmlWithItsPosition() {
		assertThat(failed("<rss>\n<channel>\n</rss>"))
			.isEqualTo(new DocumentProblem(DocumentProblemKind.MALFORMED_XML, "XML 형식이 틀렸다", 3, 3));
	}

	@Test
	void readsTheDecodedDocument() {
		byte[] body = DocumentDecoderTests.concat(DocumentDecoderTests.ascii("<?xml version=\"1.0\" encoding=\"euc-kr\"?>"
				+ HEAD), DocumentDecoderTests.bytes(0x8C, 0x63), DocumentDecoderTests.ascii(TAIL));
		DocumentDecoder.Decoded.Text decoded = (DocumentDecoder.Decoded.Text) DocumentDecoder
			.decode(new FeedDocument(body, null, Instant.parse("2026-10-25T00:00:00Z")), List.of());

		assertThat(itemTitle(parsed(decoded.text()))).isEqualTo("똠");
	}

	private static Document parsed(String text) {
		FeedXmlReader.Read read = FeedXmlReader.read(text);
		assertThat(read).as("%s", read).isInstanceOf(FeedXmlReader.Read.Parsed.class);
		return ((FeedXmlReader.Read.Parsed) read).document();
	}

	private static DocumentProblem failed(String text) {
		FeedXmlReader.Read read = FeedXmlReader.read(text);
		assertThat(read).as("%s", read).isInstanceOf(FeedXmlReader.Read.Failed.class);
		return ((FeedXmlReader.Read.Failed) read).problem();
	}

	/** 위치는 파서가 선언 · 참조를 끝낸 바로 다음 칸이다 */
	private static DocumentProblem dtd(String what, int line, int column) {
		return new DocumentProblem(DocumentProblemKind.DTD_DECLARATION, "DTD 선언과 선언되지 않은 엔티티는 받지 않는다: " + what,
				line, column);
	}

	private static String itemTitle(Document document) {
		return document.getRootElement().getChild("channel").getChild("item").getChildText("title");
	}

	/** JDK 기본 설정의 SAX 파서로 읽은 {@code t} 요소 텍스트 */
	private static String readWithDefaultParser(String text) {
		StringBuilder content = new StringBuilder();
		try {
			SAXParserFactory.newInstance().newSAXParser().parse(new InputSource(new StringReader(text)),
					new DefaultHandler() {

						@Override
						public void characters(char[] ch, int start, int length) {
							content.append(ch, start, length);
						}

					});
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		return content.toString();
	}

}
