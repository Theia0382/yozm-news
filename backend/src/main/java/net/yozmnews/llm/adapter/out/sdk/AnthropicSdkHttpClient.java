package net.yozmnews.llm.adapter.out.sdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.anthropic.backends.Backend;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;

/**
 * Anthropic SDK 의 HTTP 클라이언트를 {@link JdkTransport} 로 구현한다 — docs/specs/llm.md §4 · llm-R13.
 * <p>
 * SDK 의 요청 · 응답을 옮기기만 한다. 키는 SDK 의 백엔드가 요청에 싣는다 — 요청을 백엔드로 준비 · 인증한 뒤 보낸다. 마감과 리다이렉트는
 * SDK 의 요청 선택지가 아니라 전송의 것을 쓴다(리다이렉트를 따르지 않는다).
 */
final class AnthropicSdkHttpClient implements HttpClient {

	private final JdkTransport transport;

	private final Backend backend;

	AnthropicSdkHttpClient(JdkTransport transport, Backend backend) {
		this.transport = Objects.requireNonNull(transport, "transport");
		this.backend = Objects.requireNonNull(backend, "backend");
	}

	@Override
	public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
		return this.backend.prepareResponse(response(this.transport.send(request(request))));
	}

	@Override
	public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
		return this.transport.sendAsync(request(request))
			.thenApply(response -> this.backend.prepareResponse(response(response)));
	}

	@Override
	public void close() {
		this.transport.close();
		this.backend.close();
	}

	private JdkTransport.Request request(HttpRequest original) {
		HttpRequest prepared = this.backend.prepareRequest(original);
		// 요청에 주소가 없으면(SDK 의 요청은 주소를 비워 둘 수 있다) 백엔드의 주소로 보낸다
		if (prepared.baseUrl() == null || prepared.baseUrl().isEmpty()) {
			prepared = prepared.toBuilder().baseUrl(this.backend.baseUrl()).build();
		}
		HttpRequest request = this.backend.authorizeRequest(prepared);
		Map<String, List<String>> headers = new LinkedHashMap<>();
		for (String name : request.headers().names()) {
			headers.put(name, request.headers().values(name));
		}
		byte[] body = null;
		HttpRequestBody content = request.body();
		if (content != null) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			content.writeTo(bytes);
			body = bytes.toByteArray();
			if (content.contentType() != null && !request.headers().names().contains("Content-Type")) {
				headers.put("Content-Type", List.of(content.contentType()));
			}
		}
		return new JdkTransport.Request(request.method().name(), URI.create(request.url()), headers, body);
	}

	private static HttpResponse response(JdkTransport.Response response) {
		Headers.Builder headers = Headers.builder();
		response.headers().forEach(headers::put);
		Headers built = headers.build();
		byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
		return new HttpResponse() {

			@Override
			public int statusCode() {
				return response.status();
			}

			@Override
			public Headers headers() {
				return built;
			}

			@Override
			public InputStream body() {
				return new ByteArrayInputStream(body);
			}

			@Override
			public void close() {
			}

		};
	}

}
