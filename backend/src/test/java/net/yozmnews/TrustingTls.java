package net.yozmnews;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * 시험 TLS — 모든 서버 인증서를 받는 {@link SSLContext}. WireMock 의 HTTPS 포트(자체 서명 인증서)를 부르는 시험이 쓴다.
 * <p>
 * 호스트 이름 확인도 트러스트 매니저가 하므로 함께 건너뛴다. {@link #whileDefault} 는 이 문맥을 JVM 기본으로 둔 채 생성을 실행하고 원래 기본값으로
 * 되돌린다 — {@code sslContext} 를 정하지 않은 JDK {@code HttpClient} 는 만들 때 기본 문맥을 쥐므로, 어댑터를 프로덕션 후크 없이 HTTPS 앞에
 * 세울 수 있다. 기본값을 바꾸는 창은 그 생성 한 번이고, 시험 실행이 병렬이 아니라는 전제다(같은 창에 다른 스레드가 만든 클라이언트도 이 문맥을 쥔다).
 */
public final class TrustingTls {

	private static final SSLContext CONTEXT = trustingAll();

	private TrustingTls() {
	}

	/** 모든 서버 인증서를 받는 문맥 */
	public static SSLContext context() {
		return CONTEXT;
	}

	/** 이 문맥을 JVM 기본으로 둔 채 {@code build} 를 실행하고 원래 기본값으로 되돌린다 */
	public static synchronized <T> T whileDefault(Supplier<T> build) {
		Objects.requireNonNull(build, "build");
		SSLContext previous;
		try {
			previous = SSLContext.getDefault();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("JVM 기본 TLS 문맥을 읽지 못했다", ex);
		}
		SSLContext.setDefault(CONTEXT);
		try {
			return build.get();
		}
		finally {
			SSLContext.setDefault(previous);
		}
	}

	private static SSLContext trustingAll() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new TrustingAll() }, null);
			return context;
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("시험 TLS 문맥을 만들지 못했다", ex);
		}
	}

	private static final class TrustingAll extends X509ExtendedTrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}

	}

}
