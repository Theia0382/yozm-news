package net.yozmnews.source.domain.feed;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.jdom2.JDOMException;
import org.jdom2.input.sax.XMLReaderJDOMFactory;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * 피드 XML 파서를 호출마다 새 팩토리로 만든다. JDOM 의 공유 팩토리({@code XMLReaders})는 한 번 걸린 기능 설정이 같은 JVM 의 다른
 * 빌더에도 남으므로 쓰지 않는다. 외부 엔티티 · 외부 DTD 를 읽지 않고, DOCTYPE 자체는 받는다 — DTD 의 선언은
 * {@link DeclarationRejectingHandler} 가 거부한다.
 */
final class IsolatedXmlReaders implements XMLReaderJDOMFactory {

	@Override
	public XMLReader createXMLReader() throws JDOMException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setValidating(false);
			// 지원하지 않는 기능이면 예외가 난다 — 설정이 조용히 빠진 파서는 쓰지 않는다
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
			XMLReader reader = factory.newSAXParser().getXMLReader();
			reader.setEntityResolver((publicId, systemId) -> {
				throw new SAXException("외부 자원을 읽지 않는다: " + systemId);
			});
			return reader;
		}
		catch (ParserConfigurationException | SAXException ex) {
			throw new JDOMException("피드 XML 파서를 구성하지 못했다", ex);
		}
	}

	@Override
	public boolean isValidating() {
		return false;
	}

}
