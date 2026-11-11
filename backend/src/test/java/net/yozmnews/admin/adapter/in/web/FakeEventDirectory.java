package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.BigEvent;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventCursor;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventNeighbor;
import net.yozmnews.event.EventPage;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.event.EventView;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 사건 조회 대역 — 시험이 넣은 값을 돌려주고, 받은 호출을 순서대로 남긴다.
 */
class FakeEventDirectory implements EventDirectory {

	/** 사건 ID 로 찾는 결과 — 합쳐진 사건 ID 에 남는 사건을 넣어 둔다 */
	final Map<EventId, EventView> events = new LinkedHashMap<>();

	final Map<EventId, List<EventArticleView>> articles = new LinkedHashMap<>();

	final Map<EventId, ArticleId> representatives = new LinkedHashMap<>();

	@Nullable EventPage page;

	List<BigEvent> bigEvents = List.of();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.events.clear();
		this.articles.clear();
		this.representatives.clear();
		this.page = null;
		this.bigEvents = List.of();
		this.calls.clear();
	}

	@Override
	public Optional<EventView> find(EventId id) {
		this.calls.add("find " + id.value());
		return Optional.ofNullable(this.events.get(id));
	}

	@Override
	public List<EventArticleView> articles(EventId id) {
		this.calls.add("articles " + id.value());
		return this.articles.getOrDefault(id, List.of());
	}

	@Override
	public Map<ArticleId, EventId> eventsOf(Collection<ArticleId> articleIds) {
		throw new UnsupportedOperationException("운영자 컨트롤러는 부르지 않는다");
	}

	@Override
	public Map<EventId, ArticleId> representatives(Map<EventId, ? extends Collection<ArticleId>> among) {
		throw new UnsupportedOperationException("운영자 컨트롤러는 사건 전체의 대표만 부른다");
	}

	@Override
	public Map<EventId, ArticleId> representativesOfAll(Collection<EventId> events) {
		this.calls.add("representativesOfAll " + events.stream().map(EventId::value).toList());
		Map<EventId, ArticleId> found = new LinkedHashMap<>();
		events.forEach(event -> {
			ArticleId article = this.representatives.get(event);
			if (article != null) {
				found.put(event, article);
			}
		});
		return found;
	}

	@Override
	public List<BigEvent> bigEvents(EventPeriod period) {
		this.calls.add("bigEvents " + period.from() + " ~ " + period.to());
		return this.bigEvents;
	}

	@Override
	public EventPage list(EventPeriod period, @Nullable EventCursor after, int size) {
		this.calls.add("list " + period.from() + " ~ " + period.to() + " after "
				+ ((after == null) ? null : after.lastArticleAt() + "/" + after.id().value()) + " size " + size);
		return FakeResults.given(this.page);
	}

	/** 운영자 API 는 스토리라인 잇기의 재료를 읽지 않는다 */
	@Override
	public Map<EventId, EventProfile> profiles(Collection<EventId> ids, int keyTerms) {
		throw new UnsupportedOperationException("운영자 API 는 사건 표현을 읽지 않는다");
	}

	@Override
	public List<EventNeighbor> neighbors(EventId id, Instant from, Instant to, double minCosine, int keyTerms,
			int limit) {
		throw new UnsupportedOperationException("운영자 API 는 이웃 사건을 읽지 않는다");
	}

	@Override
	public EventTermCounts termCounts(Collection<String> terms, Instant from, Instant to) {
		throw new UnsupportedOperationException("운영자 API 는 말 수를 읽지 않는다");
	}

	@Override
	public List<EventView> lastArticleSince(Instant since) {
		throw new UnsupportedOperationException("운영자 API 는 채우기 대상을 읽지 않는다");
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeEventDirectory fakeEventDirectory() {
			return new FakeEventDirectory();
		}

	}

}
