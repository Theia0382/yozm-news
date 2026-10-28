package net.yozmnews.source.domain.feed;

import org.jdom2.JDOMFactory;
import org.jdom2.input.sax.SAXHandler;
import org.jspecify.annotations.Nullable;
import org.xml.sax.Locator;

/**
 * DTD 의 선언과 선언되지 않은 엔티티를 거부하는 JDOM 처리기. 파서는 내부 subset 의 선언을 본문보다 먼저 알리므로, 엔티티가 펼쳐지기 전에
 * 문서 읽기가 멈춘다. 외부 DTD 를 가리키는 문서에서 파서가 정의되지 않은 엔티티를 건너뛰는 것도 같은 예외로 멈춘다.
 */
final class DeclarationRejectingHandler extends SAXHandler {

	DeclarationRejectingHandler(JDOMFactory factory) {
		super(factory);
	}

	@Override
	public void elementDecl(String name, String model) {
		throw rejected("요소 선언 " + name);
	}

	@Override
	public void attributeDecl(String elementName, String attributeName, String type, String valueDefault,
			String value) {
		throw rejected("속성 선언 " + attributeName);
	}

	@Override
	public void internalEntityDecl(String name, String value) {
		throw rejected("엔티티 선언 " + name);
	}

	@Override
	public void externalEntityDecl(String name, String publicId, String systemId) {
		throw rejected("외부 엔티티 선언 " + name);
	}

	@Override
	public void notationDecl(String name, String publicId, String systemId) {
		throw rejected("표기법 선언 " + name);
	}

	@Override
	public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) {
		throw rejected("파싱하지 않는 엔티티 선언 " + name);
	}

	@Override
	public void skippedEntity(String name) {
		throw rejected("선언되지 않은 엔티티 " + name);
	}

	private DtdDeclarationRejected rejected(String what) {
		Locator locator = getDocumentLocator();
		return new DtdDeclarationRejected("DTD 선언과 선언되지 않은 엔티티는 받지 않는다: " + what, line(locator),
				column(locator));
	}

	private static @Nullable Integer line(@Nullable Locator locator) {
		return (locator == null || locator.getLineNumber() < 1) ? null : locator.getLineNumber();
	}

	private static @Nullable Integer column(@Nullable Locator locator) {
		return (locator == null || locator.getColumnNumber() < 1) ? null : locator.getColumnNumber();
	}

	/** 선언을 만나 문서 읽기를 멈춘다 — 파서를 거쳐 그대로 올라온다 */
	static final class DtdDeclarationRejected extends RuntimeException {

		private final @Nullable Integer line;

		private final @Nullable Integer column;

		DtdDeclarationRejected(String message, @Nullable Integer line, @Nullable Integer column) {
			super(message);
			this.line = line;
			this.column = column;
		}

		DocumentProblem problem() {
			return new DocumentProblem(DocumentProblemKind.DTD_DECLARATION, getMessage(), this.line, this.column);
		}

	}

}
