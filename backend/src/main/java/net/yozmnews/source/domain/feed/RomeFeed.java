package net.yozmnews.source.domain.feed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jspecify.annotations.Nullable;

/**
 * JDOM 문서 → Rome 의 형식 판별과 항목별 제목 · 링크 — docs/specs/source.md §4 · source-R39.
 * <p>
 * Rome 의 항목은 문서의 항목 요소와 순서대로 1:1 로 짝짓는다 — 그 밖의 값은 짝지은 요소의 원문에서 읽는다.
 */
final class RomeFeed {

	/** Rome 이 문서를 읽은 결과 */
	sealed interface Built {

		/** 형식을 판별하고 항목을 짝지었다 */
		record Feed(FeedFormat format, List<Entry> entries) implements Built {

			public Feed {
				Objects.requireNonNull(format, "format");
				entries = List.copyOf(entries);
			}

		}

		/** RSS · Atom 피드가 아니다 */
		record Failed(DocumentProblem problem) implements Built {

			public Failed {
				Objects.requireNonNull(problem, "problem");
			}

		}

	}

	/**
	 * Rome 항목과 짝지은 항목 요소.
	 * @param element 짝지은 항목 요소
	 * @param title Rome 이 읽은 제목(Atom 텍스트 구성을 푼 값)
	 * @param link Rome 이 고른 기사 링크(RSS 는 link 가 없으면 permalink guid, Atom 은 alternate)
	 */
	record Entry(Element element, @Nullable String title, @Nullable String link) {

		Entry {
			Objects.requireNonNull(element, "element");
		}

	}

	private RomeFeed() {
	}

	static Built build(Document document) {
		SyndFeed feed;
		try {
			// Document 를 받는 경로 — Rome 의 원문 보정(healer)을 거치지 않는다
			feed = new SyndFeedInput().build(document);
		}
		catch (FeedException | RuntimeException ex) {
			return new Built.Failed(DocumentProblem.of(DocumentProblemKind.NOT_A_FEED, "RSS · Atom 피드가 아니다"));
		}
		FeedFormat format = FeedFormat.ofRomeType(feed.getFeedType());
		List<Element> elements = format.itemElements(document);
		List<SyndEntry> syndEntries = feed.getEntries();
		if (elements.size() != syndEntries.size()) {
			throw new IllegalStateException("Rome 항목 " + syndEntries.size() + "개와 " + format + " 항목 요소 "
					+ elements.size() + "개가 짝지어지지 않는다");
		}
		List<Entry> entries = new ArrayList<>(elements.size());
		for (int i = 0; i < elements.size(); i++) {
			SyndEntry entry = syndEntries.get(i);
			entries.add(new Entry(elements.get(i), entry.getTitle(), entry.getLink()));
		}
		return new Built.Feed(format, entries);
	}

}
