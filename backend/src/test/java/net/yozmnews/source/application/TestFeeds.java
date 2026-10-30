package net.yozmnews.source.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.gate.FeedActivation;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 수집 통합 테스트의 피드 — WireMock 경로마다 피드 하나인 운영자 프로필 소스를 등록하고 켠다. 피드 URL 은 {@code 127.0.0.1} 이다:
 * {@code localhost} 는 IPv6 루프백으로 풀려 테스트의 허용 대역({@code 127.0.0.1/32}) 밖이 될 수 있다.
 * <p>
 * 피드 켜기는 미리보기로 피드를 한 번 받는다. 켜는 동안만 통과 응답을 두고, 켠 뒤 그 응답 · 요청 기록 · 소스의 요청 시작 시각 예약을
 * 지운다 — 수집 시험의 요청 수 · 수신 시각 · 첫 요청 시각에 미리보기가 섞이지 않는다.
 */
final class TestFeeds {

	private final SourceRegistration registration;

	private final SourceOperations operations;

	private final JdbcClient jdbc;

	private final WireMockExtension server;

	private int sources;

	TestFeeds(SourceRegistration registration, SourceOperations operations, JdbcClient jdbc, WireMockExtension server) {
		this.registration = registration;
		this.operations = operations;
		this.jdbc = jdbc;
		this.server = server;
	}

	/** 소스 하나 — 경로마다 피드 하나. 소스와 피드를 모두 켠다 */
	List<Long> source(String... paths) {
		int n = ++this.sources;
		StringBuilder text = new StringBuilder("""
				source:
				  name: 수집일보%d
				  homepage: https://collect%d.example
				  language: ko
				termsReviewedAt: 2026-10-01
				feeds:
				""".formatted(n, n));
		for (int i = 0; i < paths.length; i++) {
			text.append("  - key: f").append(i).append('\n')
				.append("    url: http://127.0.0.1:").append(this.server.getPort()).append(paths[i]).append('\n')
				.append("    section: 전체\n");
		}
		Registration registered = this.registration.registerOperatorProfile(text.toString());
		assertThat(registered).isInstanceOf(Registration.Registered.class);
		SourceId source = ((Registration.Registered) registered).id();
		this.operations.enableSource(source);
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < paths.length; i++) {
			long id = new RegistryRows(this.jdbc).feedId(source.value(), "f" + i);
			enablePassingPreview(source, new FeedId(id), paths[i]);
			ids.add(id);
		}
		return ids;
	}

	private void enablePassingPreview(SourceId source, FeedId feed, String path) {
		StubMapping preview = this.server.stubFor(get(path).willReturn(rss("preview")));
		assertThat(this.operations.enableFeed(feed)).isInstanceOf(FeedActivation.Activated.class);
		this.server.removeStub(preview);
		this.server.removeServeEventsMatching(getRequestedFor(urlEqualTo(path)).build());
		this.jdbc.sql("delete from source_fetch_slots where source_id = :source").param("source", source.value()).update();
	}

	int requests(String path) {
		return this.server.countRequestsMatching(getRequestedFor(urlEqualTo(path)).build()).getCount();
	}

	/** 경로들의 요청 수신 시각 — 이른 순 */
	List<Instant> receivedAt(List<String> paths) {
		return this.server.getAllServeEvents()
			.stream()
			.filter(event -> paths.contains(event.getRequest().getUrl()))
			.map(ServeEvent::getRequest)
			.map(request -> request.getLoggedDate().toInstant())
			.sorted()
			.collect(Collectors.toList());
	}

	static ResponseDefinitionBuilder rss(String... guids) {
		String items = Arrays.stream(guids).map(guid -> """
				<item><title>제목 %s</title><link>https://news.example/%s</link><guid>%s</guid>
				<pubDate>Wed, 28 Oct 2026 09:00:00 +0900</pubDate></item>
				""".formatted(guid, guid, guid)).collect(Collectors.joining());
		return aResponse().withHeader("Content-Type", "application/rss+xml; charset=utf-8")
			.withBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>합성</title>"
					+ "<link>https://news.example/</link><description>합성 피드</description>" + items
					+ "</channel></rss>");
	}

}
