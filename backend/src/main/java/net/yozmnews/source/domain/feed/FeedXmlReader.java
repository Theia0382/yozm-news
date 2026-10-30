package net.yozmnews.source.domain.feed;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.SAXHandler;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * 사전 보정을 마친 피드 원문 → JDOM 문서 — docs/specs/source.md §4 · source-R43 · source-R44.
 * <p>
 * 파서는 {@link IsolatedXmlReaders} 가 만들고, DTD 의 선언은 {@link DeclarationRejectingHandler} 가 거부한다.
 */
final class FeedXmlReader {

	private static final String DECLARATION_HANDLER = "http://xml.org/sax/properties/declaration-handler";

	/** XML 읽기 결과 */
	sealed interface Read {

		/** 문서를 읽었다 */
		record Parsed(Document document) implements Read {

			public Parsed {
				Objects.requireNonNull(document, "document");
			}

		}

		/** 문서를 읽지 못했다 */
		record Failed(DocumentProblem problem) implements Read {

			public Failed {
				Objects.requireNonNull(problem, "problem");
			}

		}

	}

	private FeedXmlReader() {
	}

	static Read read(String text) {
		try {
			return new Read.Parsed(new Builder().build(new StringReader(text)));
		}
		catch (DeclarationRejectingHandler.DtdDeclarationRejected ex) {
			return new Read.Failed(ex.problem());
		}
		catch (JDOMParseException ex) {
			return new Read.Failed(new DocumentProblem(DocumentProblemKind.MALFORMED_XML, "XML 형식이 틀렸다",
					positive(ex.getLineNumber()), positive(ex.getColumnNumber())));
		}
		catch (JDOMException | IOException ex) {
			// 입력이 아니라 파서 구성의 문제다
			throw new IllegalStateException("피드 XML 을 읽는 파서를 쓸 수 없다", ex);
		}
	}

	private static @Nullable Integer positive(int value) {
		return (value < 1) ? null : value;
	}

	/**
	 * 선언 처리기를 직접 등록하는 빌더. JDOM 은 엔티티를 펼치지 않을 때만 선언 처리기를 붙이고 등록 실패를 삼키므로, 그 설정에 기대면 선언
	 * 거부가 조용히 빠질 수 있다.
	 */
	private static final class Builder extends SAXBuilder {

		Builder() {
			super(new IsolatedXmlReaders(), DeclarationRejectingHandler::new, null);
			setExpandEntities(false);
		}

		@Override
		protected void configureParser(XMLReader parser, SAXHandler contentHandler) throws JDOMException {
			super.configureParser(parser, contentHandler);
			try {
				parser.setProperty(DECLARATION_HANDLER, contentHandler);
			}
			catch (SAXNotSupportedException | SAXNotRecognizedException ex) {
				throw new JDOMException("DTD 선언 처리기를 등록하지 못했다", ex);
			}
		}

	}

}
