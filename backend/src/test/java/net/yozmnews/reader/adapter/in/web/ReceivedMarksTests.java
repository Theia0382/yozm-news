package net.yozmnews.reader.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.ReceivedArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 사건마다 받은 날짜 — 사건의 기사 가운데 받은 날짜가 가장 이른 것, 서로 다른 기사를 상한씩 나눠 묻는다.
 * <p>
 * 지키는 규칙: reader-R5
 */
class ReceivedMarksTests {

	private static final UserId USER = new UserId(42);

	private final Map<EventId, List<ArticleId>> articles = new LinkedHashMap<>();

	private final Map<ArticleId, LocalDate> received = new LinkedHashMap<>();

	private final List<List<ArticleId>> asked = new ArrayList<>();

	@Test
	void eachEventGetsTheEarliestDateOfItsReceivedArticles() {
		this.articles.put(new EventId(1), ids(11, 12, 13));
		this.articles.put(new EventId(2), ids(21));
		this.received.put(new ArticleId(12), LocalDate.parse("2026-11-05"));
		this.received.put(new ArticleId(13), LocalDate.parse("2026-11-03"));

		Map<EventId, LocalDate> marks = marks().of(USER, List.of(new EventId(1), new EventId(2)));

		assertThat(marks).containsExactly(Map.entry(new EventId(1), LocalDate.parse("2026-11-03")));
		assertThat(this.asked).containsExactly(ids(11, 12, 13, 21));
	}

	@Test
	void distinctArticlesAreAskedInChunksOfTheLimit() {
		// 사건 1 은 기사 1 ~ 1000, 사건 2 는 기사 1000 · 1001 — 서로 다른 기사는 1001 개라 1000 · 1 로 나뉜다
		this.articles.put(new EventId(1), LongStream.rangeClosed(1, 1000).mapToObj(ArticleId::new).toList());
		this.articles.put(new EventId(2), ids(1000, 1001));
		this.received.put(new ArticleId(1001), LocalDate.parse("2026-11-02"));
		this.received.put(new ArticleId(500), LocalDate.parse("2026-11-04"));

		Map<EventId, LocalDate> marks = marks().of(USER, List.of(new EventId(1), new EventId(2)));

		assertThat(this.asked).extracting(List::size).containsExactly(ReceivedArticles.MAX_ARTICLES, 1);
		assertThat(this.asked.get(1)).containsExactly(new ArticleId(1001));
		// 두 번째 묶음의 답이 사건 2 에, 첫 묶음의 답이 사건 1 에 실린다
		assertThat(marks).containsExactly(Map.entry(new EventId(1), LocalDate.parse("2026-11-04")),
				Map.entry(new EventId(2), LocalDate.parse("2026-11-02")));
	}

	@Test
	void noEventsMeansNoQueries() {
		assertThat(marks().of(USER, List.of())).isEmpty();
		assertThat(this.asked).isEmpty();
	}

	private ReceivedMarks marks() {
		EventDirectory events = (EventDirectory) Proxy.newProxyInstance(EventDirectory.class.getClassLoader(),
				new Class<?>[] { EventDirectory.class }, (proxy, method, args) -> {
					if (!method.getName().equals("articleIdsOf")) {
						throw new UnsupportedOperationException(method.getName());
					}
					@SuppressWarnings("unchecked")
					Collection<EventId> asked = (Collection<EventId>) args[0];
					Map<EventId, List<ArticleId>> found = new LinkedHashMap<>();
					asked.forEach(event -> {
						List<ArticleId> ids = this.articles.get(event);
						if (ids != null) {
							found.put(event, ids);
						}
					});
					return found;
				});
		ReceivedArticles receivedArticles = (user, ids) -> {
			this.asked.add(List.copyOf(ids));
			Map<ArticleId, LocalDate> found = new LinkedHashMap<>();
			ids.forEach(id -> {
				LocalDate date = this.received.get(id);
				if (date != null) {
					found.put(id, date);
				}
			});
			return found;
		};
		return new ReceivedMarks(events, receivedArticles);
	}

	private static List<ArticleId> ids(long... values) {
		return LongStream.of(values).mapToObj(ArticleId::new).toList();
	}

}
