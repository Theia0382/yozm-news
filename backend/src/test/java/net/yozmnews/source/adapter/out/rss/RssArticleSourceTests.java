package net.yozmnews.source.adapter.out.rss;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.source.domain.feed.DocumentProblem;
import net.yozmnews.source.domain.feed.DocumentProblemKind;
import net.yozmnews.source.domain.fetch.AddressPolicy;
import net.yozmnews.source.domain.fetch.FailurePermanence;
import net.yozmnews.source.domain.fetch.FeedDescriptor;
import net.yozmnews.source.domain.fetch.FetchFailure;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchLimits;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.fetch.NetworkRange;
import net.yozmnews.source.domain.fetch.UserAgent;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 실제 Apache 결선(구성과 같은 공장)으로 WireMock 을 부른다. WireMock 은 루프백에서 뜨므로 기본 클라이언트는 허용 목록에
 * {@code 127.0.0.1/32} 를 둔다 — 허용 목록이 없는 클라이언트로 차단을 따로 확인한다.
 */
class RssArticleSourceTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().dynamicHttpsPort().gzipDisabled(true))
		.build();

	private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

	private static final String USER_AGENT = "YozmNews (+https://github.com/Theia0382/yozm-news; ops@example.org)";

	private static final String ETAG = "W/\"abc\"";

	private static final String LAST_MODIFIED = "Wed, 21 Oct 2026 07:28:00 GMT";

	private static final List<NetworkRange> LOOPBACK = List.of(NetworkRange.parse("127.0.0.1/32"));

	private static final FetchLimits SHORT = new FetchLimits(Duration.ofMillis(500), FetchLimits.MAX_BODY_BYTES, 5);

	private static final FetchLimits SMALL_BODY = new FetchLimits(Duration.ofSeconds(3), 1_000, 5);

	private static final CollectionProfile PROFILE = profile();

	private final List<RssArticleSource> sources = new java.util.ArrayList<>();

	@AfterEach
	void closeSources() {
		this.sources.forEach(RssArticleSource::close);
	}

	// T-S2-1 · T-S2-12
	@Test
	void fetchesAndNormalizesAFeed() {
		server.stubFor(get("/feed").willReturn(rss(feed("첫 기사")).withHeader("ETag", ETAG)
			.withHeader("Last-Modified", LAST_MODIFIED)));

		FetchResult.Fetched fetched = fetched(fetch("/feed", FetchState.NONE));

		assertThat(fetched.normalization().articles()).hasSize(1);
		assertThat(fetched.normalization().articles().getFirst().title()).isEqualTo("첫 기사");
		assertThat(fetched.state()).isEqualTo(new FetchState(ETAG, LAST_MODIFIED));
		assertThat(fetched.fetchedAt()).isEqualTo(NOW);
		assertThat(fetched.movedTo()).isNull();
	}

	// T-S2-2
	@Test
	void sendsConditionalHeadersAndKeepsTheStateOnNotModified() {
		server.stubFor(get("/feed").withHeader("If-None-Match", equalTo(ETAG))
			.willReturn(aResponse().withStatus(304).withHeader("ETag", "\"new\"")));

		FetchResult result = fetch("/feed", new FetchState(ETAG, LAST_MODIFIED));

		assertThat(result).isEqualTo(new FetchResult.NotModified(new FetchState("\"new\"", LAST_MODIFIED), NOW, null));
		server.verify(getRequestedFor(urlEqualTo("/feed")).withHeader("If-None-Match", equalTo(ETAG))
			.withHeader("If-Modified-Since", equalTo(LAST_MODIFIED)));
	}

	@Test
	void sendsNoConditionalHeadersWithoutAPreviousState() {
		server.stubFor(get("/feed").willReturn(rss(feed("첫 기사"))));

		fetched(fetch("/feed", FetchState.NONE));

		server.verify(getRequestedFor(urlEqualTo("/feed")).withHeader("If-None-Match", absent())
			.withHeader("If-Modified-Since", absent()));
	}

	// T-S2-3
	@Test
	void decodesWithTheTransportCharset() {
		byte[] body = feedWithoutDeclaration("한글 제목").getBytes(Charset.forName("EUC-KR"));
		server.stubFor(get("/euc").willReturn(
				aResponse().withHeader("Content-Type", "application/rss+xml; charset=euc-kr").withBody(body)));

		assertThat(fetched(fetch("/euc", FetchState.NONE)).normalization().articles().getFirst().title())
			.isEqualTo("한글 제목");
	}

	@Test
	void withoutTheTransportCharsetTheSameBytesAreMisread() {
		byte[] body = feedWithoutDeclaration("한글 제목").getBytes(Charset.forName("EUC-KR"));
		server.stubFor(get("/euc").willReturn(aResponse().withHeader("Content-Type", "application/rss+xml").withBody(body)));

		FetchResult.Fetched fetched = fetched(fetch("/euc", FetchState.NONE));

		assertThat(fetched.normalization().articles().getFirst().title()).isNotEqualTo("한글 제목");
		assertThat(fetched.normalization().replacedCharacters()).isPositive();
	}

	// T-S2-4
	@Test
	void identifiesItselfAndAcceptsFeedsAndCompression() {
		server.stubFor(get("/feed").willReturn(rss(feed("첫 기사"))));

		fetched(fetch("/feed", FetchState.NONE));

		server.verify(getRequestedFor(urlEqualTo("/feed")).withHeader("User-Agent", equalTo(USER_AGENT))
			.withHeader("Accept",
					equalTo("application/rss+xml, application/atom+xml, application/rdf+xml;q=0.9, "
							+ "application/xml;q=0.9, text/xml;q=0.9, */*;q=0.8"))
			.withHeader("Accept-Encoding", containing("gzip")));
	}

	@Test
	void doesNotSendBackACookie() {
		server.stubFor(get("/c1").willReturn(
				aResponse().withStatus(302).withHeader("Set-Cookie", "a=b; Path=/").withHeader("Location", "/c2")));
		server.stubFor(get("/c2").willReturn(rss(feed("첫 기사"))));

		fetched(fetch("/c1", FetchState.NONE));

		server.verify(getRequestedFor(urlEqualTo("/c2")).withHeader("Cookie", absent()));
	}

	// T-S2-5
	@Test
	void timesOutWhileWaitingForHeaders() {
		server.stubFor(get("/slow").willReturn(rss(feed("첫 기사")).withFixedDelay(2_000)));

		assertTimesOut("/slow", SHORT);
	}

	@Test
	void timesOutWhileTheBodyDrips() {
		server.stubFor(get("/drip").willReturn(rss(feed("첫 기사")).withChunkedDribbleDelay(10, 3_000)));

		assertTimesOut("/drip", SHORT);
	}

	@Test
	void sameResponseWithoutDelayIsFetched() {
		server.stubFor(get("/fast").willReturn(rss(feed("첫 기사"))));

		fetched(source(SHORT, LOOPBACK).fetch(descriptor("/fast"), FetchState.NONE));
	}

	@Test
	void oneDeadlineCoversEveryHop() {
		server.stubFor(get("/h1").willReturn(aResponse().withStatus(302).withHeader("Location", "/h2").withFixedDelay(300)));
		server.stubFor(get("/h2").willReturn(rss(feed("첫 기사")).withFixedDelay(300)));

		assertTimesOut("/h1", SHORT);
		fetched(source(new FetchLimits(Duration.ofMillis(2_000), FetchLimits.MAX_BODY_BYTES, 5), LOOPBACK)
			.fetch(descriptor("/h1"), FetchState.NONE));
	}

	// T-S2-6
	@Test
	void declaredLengthOverTheLimitIsTooLarge() {
		server.stubFor(get("/big").willReturn(aResponse().withBody("a".repeat(1_001))));

		assertFailure(fetchWith(SMALL_BODY, "/big"), FetchFailureReason.TOO_LARGE, null);
	}

	/**
	 * 밝힌 길이로 먼저 거르는지는 본문을 보내지 않고 붙잡는 서버로 가른다 — 거르지 않으면 본문을 기다리다 마감에 걸린다. 압축된
	 * 응답의 밝힌 길이는 푼 바이트와 비교할 수 없어 거르지 않는다(대조 leg).
	 */
	@ParameterizedTest
	@CsvSource({ "'', TOO_LARGE", "identity, TOO_LARGE", "gzip, TIMEOUT" })
	void declaredLengthIsCheckedBeforeReadingTheBody(String encoding, FetchFailureReason reason) throws IOException {
		String encodingHeader = encoding.isEmpty() ? "" : "Content-Encoding: " + encoding + "\r\n";
		try (HoldingServer holding = new HoldingServer("HTTP/1.1 200 OK\r\nContent-Type: application/rss+xml\r\n"
				+ encodingHeader + "Content-Length: 1001\r\n\r\n")) {
			long start = System.nanoTime();
			FetchResult result = source(new FetchLimits(Duration.ofMillis(1_000), 1_000, 5), LOOPBACK)
				.fetch(new FeedDescriptor(URI.create("http://127.0.0.1:" + holding.port() + "/feed"), PROFILE),
						FetchState.NONE);

			assertFailure(result, reason, null);
			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2_000));
			if (reason == FetchFailureReason.TOO_LARGE) {
				assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));
			}
		}
	}

	@Test
	void chunkedBodyOverTheLimitIsTooLarge() {
		server.stubFor(get("/chunked").willReturn(aResponse().withBody("a".repeat(1_001)).withChunkedDribbleDelay(2, 20)));

		assertFailure(fetchWith(SMALL_BODY, "/chunked"), FetchFailureReason.TOO_LARGE, null);
	}

	@Test
	void compressedBodyIsCountedAfterDecompression() {
		byte[] compressed = gzip(new byte[5_000]);
		assertThat(compressed.length).isLessThan(1_000);
		server.stubFor(get("/bomb").willReturn(aResponse().withHeader("Content-Encoding", "gzip").withBody(compressed)));

		assertFailure(fetchWith(SMALL_BODY, "/bomb"), FetchFailureReason.TOO_LARGE, null);
	}

	@Test
	void longDrippingBodyStopsAtTheLimitInsteadOfTheDeadline() {
		server.stubFor(get("/long").willReturn(aResponse().withBody("a".repeat(20_000)).withChunkedDribbleDelay(20, 10_000)));

		long start = System.nanoTime();
		assertFailure(fetchWith(SMALL_BODY, "/long"), FetchFailureReason.TOO_LARGE, null);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2_500));
	}

	@Test
	void bodyOfExactlyTheLimitIsFetched() {
		String body = padded(feed("첫 기사"), 1_000);
		assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(1_000);
		server.stubFor(get("/exact").willReturn(rss(body)));

		assertThat(fetched(fetchWith(SMALL_BODY, "/exact")).normalization().articles()).hasSize(1);
	}

	@Test
	void compressedBodyUnderTheLimitIsFetched() {
		byte[] plain = padded(feed("첫 기사"), 999).getBytes(StandardCharsets.UTF_8);
		assertThat(plain).hasSize(999);
		server.stubFor(get("/gz").willReturn(aResponse().withHeader("Content-Type", "application/rss+xml")
			.withHeader("Content-Encoding", "gzip")
			.withBody(gzip(plain))));

		assertThat(fetched(fetchWith(SMALL_BODY, "/gz")).normalization().articles()).hasSize(1);
	}

	// T-S2-7
	@ParameterizedTest
	@CsvSource({ "500, SERVER_ERROR, TEMPORARY", "503, SERVER_ERROR, TEMPORARY", "404, NOT_FOUND, PERMANENT",
			"403, ACCESS_DENIED, PERMANENT", "429, THROTTLED, TEMPORARY" })
	void classifiesStatusFailures(int status, FetchFailureReason reason, FailurePermanence permanence) {
		server.stubFor(get("/status").willReturn(aResponse().withStatus(status).withHeader("Retry-After", "1")));

		FetchFailure failure = assertFailure(fetch("/status", FetchState.NONE), reason, status);

		assertThat(failure.permanence()).isEqualTo(permanence);
		assertThat(server.findAll(getRequestedFor(urlEqualTo("/status")))).hasSize(1);
	}

	@Test
	void longFailureBodyIsDiscarded() {
		server.stubFor(get("/gone").willReturn(
				aResponse().withStatus(404).withBody("a".repeat(20_000)).withChunkedDribbleDelay(20, 10_000)));

		assertFailure(fetchWith(SMALL_BODY, "/gone"), FetchFailureReason.NOT_FOUND, 404);
	}

	// T-S2-8
	@Test
	void temporaryRedirectIsFollowedWithoutAMove() {
		server.stubFor(get("/old").willReturn(aResponse().withStatus(302).withHeader("Location", "/new")));
		server.stubFor(get("/new").willReturn(rss(feed("첫 기사"))));

		assertThat(fetched(fetch("/old", FetchState.NONE)).movedTo()).isNull();
	}

	@Test
	void permanentRedirectReportsTheNewAddress() {
		server.stubFor(get("/old").willReturn(aResponse().withStatus(301).withHeader("Location", "/new")));
		server.stubFor(get("/new").willReturn(rss(feed("첫 기사"))));

		assertThat(fetched(fetch("/old", FetchState.NONE)).movedTo()).isEqualTo(url("/new"));
	}

	@Test
	void permanentRedirectIsReportedOnNotModifiedToo() {
		server.stubFor(get("/old").willReturn(aResponse().withStatus(301).withHeader("Location", "/new")));
		server.stubFor(get("/new").willReturn(aResponse().withStatus(304)));

		assertThat(fetch("/old", new FetchState(ETAG, null)))
			.isEqualTo(new FetchResult.NotModified(new FetchState(ETAG, null), NOW, url("/new")));
	}

	@Test
	void followsFiveHops() {
		chain(5);

		fetched(fetch("/r0", FetchState.NONE));
	}

	@Test
	void refusesTheSixthHop() {
		chain(6);

		assertFailure(fetch("/r0", FetchState.NONE), FetchFailureReason.TOO_MANY_REDIRECTS, null);
	}

	@Test
	void redirectWithoutLocationIsInvalid() {
		server.stubFor(get("/nowhere").willReturn(aResponse().withStatus(302)));

		assertFailure(fetch("/nowhere", FetchState.NONE), FetchFailureReason.INVALID_REDIRECT, null);
	}

	@ParameterizedTest
	@CsvSource({ "ftp://files.example/feed", "http://127.0.0.1:99999/feed", "http://user:secret@feed_host.example/feed" })
	void redirectToAnUnsupportedUrlIsRefused(String location) {
		server.stubFor(get("/away").willReturn(aResponse().withStatus(302).withHeader("Location", location)));

		FetchFailure failure = assertFailure(fetch("/away", FetchState.NONE), FetchFailureReason.UNSUPPORTED_URL, null);

		assertThat(failure.message()).doesNotContain("secret");
	}

	@Test
	void longRedirectBodyIsDiscarded() {
		server.stubFor(get("/old").willReturn(aResponse().withStatus(302).withHeader("Location", "/new")
			.withBody("a".repeat(20_000))
			.withChunkedDribbleDelay(20, 10_000)));
		server.stubFor(get("/new").willReturn(rss(feed("첫 기사"))));

		fetched(fetchWith(SMALL_BODY, "/old"));
	}

	// T-S2-9
	@Test
	void blocksLoopbackWithoutSendingARequest() {
		server.stubFor(get("/feed").willReturn(rss(feed("첫 기사"))));

		FetchFailure failure = assertFailure(
				source(FetchLimits.DEFAULT, List.of()).fetch(descriptor("/feed"), FetchState.NONE),
				FetchFailureReason.BLOCKED_ADDRESS, null);

		assertThat(failure.message()).isEqualTo("내부 주소로 가는 요청을 막았다: 127.0.0.1 → 127.0.0.1");
		assertThat(server.findAll(anyRequestedFor(anyUrl()))).isEmpty();
	}

	@Test
	void blocksANameThatResolvesToLoopback() {
		server.stubFor(get("/feed").willReturn(rss(feed("첫 기사"))));

		assertFailure(source(FetchLimits.DEFAULT, List.of())
			.fetch(new FeedDescriptor(URI.create("http://localhost:" + server.getPort() + "/feed"), PROFILE),
					FetchState.NONE),
				FetchFailureReason.BLOCKED_ADDRESS, null);
		assertThat(server.findAll(anyRequestedFor(anyUrl()))).isEmpty();
	}

	@Test
	void blocksARedirectToTheMetadataAddress() {
		server.stubFor(get("/meta").willReturn(
				aResponse().withStatus(302).withHeader("Location", "http://169.254.169.254/latest/meta-data/")));

		assertFailure(fetch("/meta", FetchState.NONE), FetchFailureReason.BLOCKED_ADDRESS, null);
		assertThat(server.findAll(anyRequestedFor(anyUrl()))).hasSize(1);
	}

	@Test
	void allowedLoopbackIsFetched() {
		server.stubFor(get("/feed").willReturn(rss(feed("첫 기사"))));

		fetched(fetch("/feed", FetchState.NONE));

		assertThat(server.findAll(anyRequestedFor(anyUrl()))).hasSize(1);
	}

	@Test
	void refusesAnUnsupportedFirstUrlWithoutQuotingUserinfo() {
		FetchFailure failure = assertFailure(
				source(FetchLimits.DEFAULT, LOOPBACK).fetch(
						new FeedDescriptor(URI.create("ftp://user:secret@127.0.0.1/feed"), PROFILE), FetchState.NONE),
				FetchFailureReason.UNSUPPORTED_URL, null);

		assertThat(failure.message()).isEqualTo("요청할 수 없는 주소다: ftp://127.0.0.1/feed");
	}

	@ParameterizedTest
	@CsvSource({ "http://user:secret@127.0.0.1:{port}/feed", "http://127.0.0.1:99999/feed",
			"http://user:secret@feed_host.example/feed" })
	void refusesAFirstUrlThatCannotBeRequested(String template) {
		URI url = URI.create(template.replace("{port}", Integer.toString(server.getPort())));

		FetchFailure failure = assertFailure(source(FetchLimits.DEFAULT, LOOPBACK).fetch(new FeedDescriptor(url, PROFILE),
				FetchState.NONE), FetchFailureReason.UNSUPPORTED_URL, null);

		assertThat(failure.message()).doesNotContain("secret");
		assertThat(server.findAll(anyRequestedFor(anyUrl()))).isEmpty();
	}

	// T-S2-10
	@Test
	void closedPortIsAConnectionFailure() throws IOException {
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}

		assertFailure(source(FetchLimits.DEFAULT, LOOPBACK)
			.fetch(new FeedDescriptor(URI.create("http://127.0.0.1:" + port + "/feed"), PROFILE), FetchState.NONE),
				FetchFailureReason.CONNECTION_FAILED, null);
	}

	@Test
	void unresolvableNameIsANameFailure() {
		assertFailure(source(FetchLimits.DEFAULT, LOOPBACK)
			.fetch(new FeedDescriptor(URI.create("http://feed.example.invalid/feed"), PROFILE), FetchState.NONE),
				FetchFailureReason.NAME_NOT_RESOLVED, null);
	}

	@Test
	void untrustedCertificateIsATlsFailure() {
		server.stubFor(get("/feed").willReturn(rss(feed("첫 기사"))));

		assertFailure(source(FetchLimits.DEFAULT, LOOPBACK).fetch(
				new FeedDescriptor(URI.create("https://127.0.0.1:" + server.getHttpsPort() + "/feed"), PROFILE),
				FetchState.NONE), FetchFailureReason.TLS_FAILURE, null);
	}

	// T-S2-11 · T-S2-12
	@Test
	void documentThatIsNotAFeedIsUnreadable() {
		server.stubFor(get("/html").willReturn(aResponse().withHeader("Content-Type", "text/html")
			.withBody("<html><body>피드 아님</body></html>".getBytes(StandardCharsets.UTF_8))));

		FetchResult result = fetch("/html", FetchState.NONE);

		assertThat(result).isEqualTo(new FetchResult.Failed(FetchFailure.unreadable(
				new DocumentProblem(DocumentProblemKind.NOT_A_FEED, "RSS · Atom 피드가 아니다", null, null)), NOW));
		assertThat(((FetchResult.Failed) result).failure().httpStatus()).isNull();
	}

	private void assertTimesOut(String path, FetchLimits limits) {
		long start = System.nanoTime();
		FetchResult result = source(limits, LOOPBACK).fetch(descriptor(path), FetchState.NONE);

		assertFailure(result, FetchFailureReason.TIMEOUT, null);
		assertThat(((FetchResult.Failed) result).attemptedAt()).isEqualTo(NOW);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_500));
	}

	private static FetchFailure assertFailure(FetchResult result, FetchFailureReason reason, Integer status) {
		assertThat(result).as("%s", result).isInstanceOf(FetchResult.Failed.class);
		FetchFailure failure = ((FetchResult.Failed) result).failure();
		assertThat(failure.reason()).as("%s", failure).isEqualTo(reason);
		assertThat(failure.httpStatus()).isEqualTo(status);
		return failure;
	}

	private static FetchResult.Fetched fetched(FetchResult result) {
		assertThat(result).as("%s", result).isInstanceOf(FetchResult.Fetched.class);
		return (FetchResult.Fetched) result;
	}

	private FetchResult fetch(String path, FetchState previous) {
		return source(FetchLimits.DEFAULT, LOOPBACK).fetch(descriptor(path), previous);
	}

	private FetchResult fetchWith(FetchLimits limits, String path) {
		return source(limits, LOOPBACK).fetch(descriptor(path), FetchState.NONE);
	}

	private RssArticleSource source(FetchLimits limits, List<NetworkRange> allowed) {
		RssArticleSource source = RssArticleSource.create(new AddressPolicy(allowed), InstantSource.fixed(NOW), limits,
				new UserAgent("ops@example.org"));
		this.sources.add(source);
		return source;
	}

	private static FeedDescriptor descriptor(String path) {
		return new FeedDescriptor(url(path), PROFILE);
	}

	private static URI url(String path) {
		return URI.create("http://127.0.0.1:" + server.getPort() + path);
	}

	private static void chain(int hops) {
		for (int i = 0; i < hops; i++) {
			server.stubFor(get("/r" + i).willReturn(aResponse().withStatus(302).withHeader("Location", "/r" + (i + 1))));
		}
		server.stubFor(get("/r" + hops).willReturn(rss(feed("첫 기사"))));
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder rss(String body) {
		return aResponse().withHeader("Content-Type", "application/rss+xml; charset=utf-8")
			.withBody(body.getBytes(StandardCharsets.UTF_8));
	}

	private static String feed(String title) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + feedWithoutDeclaration(title);
	}

	private static String feedWithoutDeclaration(String title) {
		return "<rss version=\"2.0\"><channel><title>알파일보</title><link>https://alpha.example</link>"
				+ "<description>d</description><item><title>" + title
				+ "</title><link>https://alpha.example/1</link></item></channel></rss>";
	}

	/** 문서 끝에 주석을 붙여 UTF-8 바이트 수를 맞춘다 */
	private static String padded(String document, int bytes) {
		int filler = bytes - document.getBytes(StandardCharsets.UTF_8).length - "<!---->".length();
		assertThat(filler).isNotNegative();
		return document + "<!--" + "x".repeat(filler) + "-->";
	}

	/** 응답 머리만 보내고 본문은 보내지 않은 채 연결을 붙잡는 서버 */
	private static final class HoldingServer implements AutoCloseable {

		private final ServerSocket socket;

		HoldingServer(String head) throws IOException {
			this.socket = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"));
			Thread.ofPlatform().daemon().start(() -> {
				try (Socket connection = this.socket.accept()) {
					BufferedReader request = new BufferedReader(
							new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
					String line;
					while ((line = request.readLine()) != null && !line.isEmpty()) {
						// 요청 머리를 끝까지 읽는다
					}
					connection.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
					connection.getOutputStream().flush();
					Thread.sleep(5_000);
				}
				catch (IOException | InterruptedException ex) {
					// 테스트가 닫으면 끝난다
				}
			});
		}

		int port() {
			return this.socket.getLocalPort();
		}

		@Override
		public void close() throws IOException {
			this.socket.close();
		}

	}

	private static byte[] gzip(byte[] plain) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(plain);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	private static CollectionProfile profile() {
		ProfileParse parse = new ProfileParser(new ProfileTextReader()).parseOperatorProfile("""
				source:
				  name: 알파일보
				  homepage: https://alpha.example
				  language: ko
				feeds:
				  - key: all
				    url: https://alpha.example/rss.xml
				    section: 전체
				""");
		assertThat(parse).as("%s", parse).isInstanceOf(ProfileParse.Parsed.class);
		return ((ProfileParse.Parsed) parse).profile();
	}

}
