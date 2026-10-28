package net.yozmnews.source.adapter.out.rss;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.InstantSource;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLException;

import net.yozmnews.source.domain.feed.FeedDocument;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.feed.FeedNormalizer;
import net.yozmnews.source.domain.fetch.AddressPolicy;
import net.yozmnews.source.domain.fetch.ArticleSource;
import net.yozmnews.source.domain.fetch.ContentTypeCharset;
import net.yozmnews.source.domain.fetch.FeedDescriptor;
import net.yozmnews.source.domain.fetch.FetchFailure;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchLimits;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.fetch.FetchUrls;
import net.yozmnews.source.domain.fetch.RedirectTrail;
import net.yozmnews.source.domain.fetch.StatusOutcome;
import net.yozmnews.source.domain.fetch.StatusRule;
import net.yozmnews.source.domain.fetch.UserAgent;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.Nullable;

/**
 * v1 수집 구현 — 피드를 HTTP(S) 로 받아 정규화로 넘긴다. 리다이렉트는 직접 따라가며 홉마다 domain 규칙으로 판정하고, 한
 * 번의 수집 전체에 마감 하나를 건다(홉마다 남은 시간 뒤에 그 홉의 요청을 취소한다). 받은 클라이언트와 취소 스케줄러를
 * 소유하고 {@link #close()} 에서 닫는다.
 */
public final class RssArticleSource implements ArticleSource, AutoCloseable {

	/** RSS · Atom 을 앞세우되 무엇이 와도 정규화가 판정한다 */
	private static final String ACCEPT ="application/rss+xml, application/atom+xml, application/rdf+xml;q=0.9, "
			+ "application/xml;q=0.9, text/xml;q=0.9, */*;q=0.8";

	private final CloseableHttpClient client;

	private final ScheduledExecutorService canceller;

	private final FeedNormalizer normalizer;

	private final InstantSource clock;

	private final FetchLimits limits;

	private final UserAgent userAgent;

	RssArticleSource(CloseableHttpClient client, ScheduledExecutorService canceller, FeedNormalizer normalizer,
			InstantSource clock, FetchLimits limits, UserAgent userAgent) {
		this.client = Objects.requireNonNull(client, "client");
		this.canceller = Objects.requireNonNull(canceller, "canceller");
		this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.limits = Objects.requireNonNull(limits, "limits");
		this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
	}

	/**
	 * 구성과 테스트가 같은 결선으로 만든다 — 수집용 클라이언트와 데몬 취소 스레드 하나. 홉이 끝나면 마감 취소 예약을 거두므로,
	 * 거둔 예약은 바로 큐에서 지운다(지우지 않으면 마감까지 그 홉의 요청을 붙잡고 남는다).
	 */
	static RssArticleSource create(AddressPolicy policy, InstantSource clock, FetchLimits limits, UserAgent userAgent) {
		ScheduledThreadPoolExecutor canceller = new ScheduledThreadPoolExecutor(1,
				Thread.ofPlatform().daemon().name("feed-fetch-canceller").factory());
		canceller.setRemoveOnCancelPolicy(true);
		return new RssArticleSource(FeedHttpClients.create(policy), canceller, new FeedNormalizer(), clock, limits,
				userAgent);
	}

	@Override
	public FetchResult fetch(FeedDescriptor feed, FetchState previous) {
		Objects.requireNonNull(feed, "feed");
		Objects.requireNonNull(previous, "previous");
		// 마감은 단조 시계로 잰다 — 벽시계가 뛰어도 늘거나 줄지 않는다. 주입한 시계는 받은 시각을 찍을 때만 쓴다
		long deadline = System.nanoTime() + this.limits.deadline().toNanos();
		URI current = feed.url();
		if (!FetchUrls.isFetchable(current)) {
			return failed(FetchFailure.of(FetchFailureReason.UNSUPPORTED_URL, display(current)));
		}
		RedirectTrail trail = new RedirectTrail(this.limits.maxRedirects());
		while (true) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				return failed(timeout());
			}
			Hop hop = exchange(current, previous, remaining, deadline);
			switch (hop) {
				case Hop.Body body -> {
					return normalized(body, feed, trail);
				}
				case Hop.Unchanged unchanged -> {
					return new FetchResult.NotModified(unchanged.state(), this.clock.instant(), trail.movedTo());
				}
				case Hop.Failure failure -> {
					return failed(failure.failure());
				}
				case Hop.Redirect redirect -> {
					Optional<URI> target = FetchUrls.resolveLocation(current, redirect.location());
					if (target.isEmpty()) {
						return failed(FetchFailure.of(FetchFailureReason.INVALID_REDIRECT, display(current)));
					}
					if (!FetchUrls.isFetchable(target.get())) {
						return failed(FetchFailure.of(FetchFailureReason.UNSUPPORTED_URL, display(target.get())));
					}
					if (!trail.follow(redirect.permanent(), target.get())) {
						return failed(FetchFailure.of(FetchFailureReason.TOO_MANY_REDIRECTS,
								this.limits.maxRedirects() + "회"));
					}
					current = target.get();
				}
			}
		}
	}

	/** 취소 스레드를 멈춘 뒤 클라이언트를 닫는다 */
	@Override
	public void close() {
		this.canceller.shutdownNow();
		try {
			this.client.close();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private FetchResult normalized(Hop.Body body, FeedDescriptor feed, RedirectTrail trail) {
		var fetchedAt = this.clock.instant();
		return switch (this.normalizer.normalize(new FeedDocument(body.body(), body.charset(), fetchedAt),
				feed.profile())) {
			case FeedNormalization.Normalized normalized ->
				new FetchResult.Fetched(normalized, body.state(), fetchedAt, trail.movedTo());
			case FeedNormalization.Unreadable unreadable ->
				new FetchResult.Failed(FetchFailure.unreadable(unreadable.problem()), fetchedAt);
		};
	}

	/**
	 * 홉 하나를 주고받는다. 남은 시간 뒤에 이 홉의 요청을 취소하도록 예약한다 — 예약이 실행됐으면 예외 종류와 상관없이
	 * 마감 초과다.
	 */
	@SuppressWarnings("deprecation")
	private Hop exchange(URI url, FetchState previous, long remainingNanos, long deadline) {
		HttpGet get = new HttpGet(url);
		get.setHeader(HttpHeaders.USER_AGENT, this.userAgent.headerValue());
		get.setHeader(HttpHeaders.ACCEPT, ACCEPT);
		if (previous.etag() != null) {
			get.setHeader(HttpHeaders.IF_NONE_MATCH, previous.etag());
		}
		if (previous.lastModified() != null) {
			get.setHeader(HttpHeaders.IF_MODIFIED_SINCE, previous.lastModified());
		}
		Timeout timeout = Timeout.of(remainingNanos, TimeUnit.NANOSECONDS);
		// setConnectTimeout 은 5.6.5 에서 deprecated 지만, 요청마다 연결 타임아웃을 줄 수 있는 지점이 이것뿐이다 — 연결
		// 경로(InternalExecRuntime)가 이 값을 읽고, 없을 때만 연결 관리자의 ConnectionConfig 값을 쓴다
		get.setConfig(RequestConfig.custom()
			.setRedirectsEnabled(false)
			.setConnectionRequestTimeout(timeout)
			.setConnectTimeout(timeout)
			.setResponseTimeout(timeout)
			.build());
		AtomicBoolean deadlineFired = new AtomicBoolean();
		ScheduledFuture<?> deadlineCancel = this.canceller.schedule(() -> {
			deadlineFired.set(true);
			get.cancel();
		}, remainingNanos, TimeUnit.NANOSECONDS);
		try {
			return this.client.execute(get, response -> handle(get, response, previous));
		}
		catch (IOException ex) {
			boolean pastDeadline = deadlineFired.get() || System.nanoTime() - deadline >= 0;
			return new Hop.Failure(failure(ex, url, pastDeadline));
		}
		finally {
			deadlineCancel.cancel(false);
		}
	}

	/**
	 * 응답 하나를 판정한다. 본문을 끝까지 읽지 않는 갈래는 처리기를 나오기 전에 요청을 취소해 연결을 버린다 — Apache 는
	 * 처리기가 끝나면 남은 본문을 끝까지 읽으므로, 버리지 않으면 긴 본문이 마감까지 붙잡는다.
	 */
	private Hop handle(HttpGet get, ClassicHttpResponse response, FetchState previous) throws IOException {
		int status = response.getCode();
		return switch (StatusRule.classify(status, previous.isConditional())) {
			case StatusOutcome.Body body -> {
				if (declaredTooLarge(response)) {
					get.cancel();
					throw new BodyTooLargeException(this.limits.maxBodyBytes());
				}
				yield new Hop.Body(readBody(get, response.getEntity()),
						ContentTypeCharset.of(header(response, HttpHeaders.CONTENT_TYPE)),
						FetchState.fromResponse(header(response, HttpHeaders.ETAG),
								header(response, HttpHeaders.LAST_MODIFIED)));
			}
			case StatusOutcome.NotModified notModified -> new Hop.Unchanged(
					previous.updatedBy(header(response, HttpHeaders.ETAG), header(response, HttpHeaders.LAST_MODIFIED)));
			case StatusOutcome.Redirect redirect -> {
				get.cancel();
				yield new Hop.Redirect(redirect.permanent(), header(response, HttpHeaders.LOCATION));
			}
			case StatusOutcome.Failure failure -> {
				get.cancel();
				yield new Hop.Failure(FetchFailure.status(failure.reason(), status));
			}
		};
	}

	/** 압축(Apache 가 풀어 준다)이 아닌 응답이 상한보다 큰 길이를 밝혔는가 — 압축된 응답의 길이는 푼 바이트와 비교할 수 없다 */
	private boolean declaredTooLarge(ClassicHttpResponse response) {
		String encoding = header(response, HttpHeaders.CONTENT_ENCODING);
		if (encoding != null && !encoding.strip().toLowerCase(Locale.ROOT).equals("identity")) {
			return false;
		}
		String length = header(response, HttpHeaders.CONTENT_LENGTH);
		if (length == null) {
			return false;
		}
		try {
			return Long.parseLong(length.strip()) > this.limits.maxBodyBytes();
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

	/** 압축을 푼 바이트를 상한까지만 읽는다 — 넘으면 연결을 버린 뒤 실패를 올린다 */
	private byte[] readBody(HttpGet get, @Nullable HttpEntity entity) throws IOException {
		if (entity == null) {
			return new byte[0];
		}
		InputStream in = entity.getContent();
		try {
			return BoundedBody.read(in, this.limits.maxBodyBytes());
		}
		catch (BodyTooLargeException ex) {
			get.cancel();
			throw ex;
		}
	}

	private FetchFailure failure(IOException ex, URI url, boolean pastDeadline) {
		if (pastDeadline) {
			return timeout();
		}
		if (ex instanceof BlockedAddressException blocked) {
			return FetchFailure.of(FetchFailureReason.BLOCKED_ADDRESS,
					url.getHost() + " → " + blocked.address().getHostAddress());
		}
		if (ex instanceof UnknownHostException) {
			return FetchFailure.of(FetchFailureReason.NAME_NOT_RESOLVED, Objects.requireNonNullElse(url.getHost(), ""));
		}
		if (ex instanceof SSLException) {
			return FetchFailure.of(FetchFailureReason.TLS_FAILURE, display(url));
		}
		if (ex instanceof BodyTooLargeException) {
			return FetchFailure.of(FetchFailureReason.TOO_LARGE, this.limits.maxBodyBytes() + "바이트");
		}
		if (ex instanceof SocketTimeoutException || ex instanceof ConnectionRequestTimeoutException) {
			return timeout();
		}
		// 연결 거부 · 끊김, 응답 형식 위반, 요청하지 않은 Content-Encoding 등 그 밖의 입출력 오류
		return FetchFailure.of(FetchFailureReason.CONNECTION_FAILED, display(url));
	}

	private FetchFailure timeout() {
		return FetchFailure.of(FetchFailureReason.TIMEOUT, this.limits.deadline().toMillis() + "ms");
	}

	private FetchResult failed(FetchFailure failure) {
		return new FetchResult.Failed(failure, this.clock.instant());
	}

	private static @Nullable String header(ClassicHttpResponse response, String name) {
		Header header = response.getFirstHeader(name);
		return (header != null) ? header.getValue() : null;
	}

	/**
	 * 문구에 인용할 URL — userinfo 를 지운다(비밀 값일 수 있다). 호스트로 읽히지 않는 authority(예: 밑줄이 든 이름)는
	 * {@code URI} 가 userinfo 를 따로 나누지 않으므로, authority 의 마지막 {@code @} 앞을 지운다.
	 */
	static String display(URI url) {
		String authority = url.getRawAuthority();
		int at = (authority != null) ? authority.lastIndexOf('@') : -1;
		if (at < 0) {
			return url.toString();
		}
		return ((url.getScheme() != null) ? url.getScheme() + ":" : "") + "//" + authority.substring(at + 1)
				+ Objects.requireNonNullElse(url.getRawPath(), "")
				+ ((url.getRawQuery() != null) ? "?" + url.getRawQuery() : "");
	}

	/** 홉 하나의 결과 */
	private sealed interface Hop {

		record Body(byte[] body, @Nullable String charset, FetchState state) implements Hop {
		}

		record Unchanged(FetchState state) implements Hop {
		}

		record Redirect(boolean permanent, @Nullable String location) implements Hop {
		}

		record Failure(FetchFailure failure) implements Hop {
		}

	}

}
