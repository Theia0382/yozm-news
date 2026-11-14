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

import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;

/**
 * OpenAI SDK 의 HTTP 클라이언트를 {@link JdkTransport} 로 구현한다 — docs/specs/llm.md §4 · llm-R13.
 * <p>
 * SDK 의 요청 · 응답을 옮기기만 한다. 마감은 SDK 의 요청 선택지가 아니라 전송의 것을 쓴다.
 */
final class OpenAiSdkHttpClient implements HttpClient {

	private final JdkTransport transport;

	OpenAiSdkHttpClient(JdkTransport transport) {
		this.transport = Objects.requireNonNull(transport, "transport");
	}

	@Override
	public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
		return response(this.transport.send(request(request)));
	}

	@Override
	public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
		return this.transport.sendAsync(request(request)).thenApply(OpenAiSdkHttpClient::response);
	}

	@Override
	public void close() {
		this.transport.close();
	}

	private static JdkTransport.Request request(HttpRequest request) {
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
