package net.yozmnews.evaluation.adapter.out.jev;

import java.net.URI;

import net.yozmnews.evaluation.domain.jev.JevApiKey;

/**
 * 시험 도우미 — 다른 패키지의 시험이 실제 Jev 평가기를 만든다. 닿지 않는 주소와 시험용 키를 쓴다 — 원본 응답 다시 읽기는 요청을 보내지 않는다.
 */
public final class JevEvaluators {

	private JevEvaluators() {
	}

	/** 요청을 보내면 연결 실패로 끝나는 평가기 — 다시 읽기 시험용 */
	public static JevArticleEvaluator offline() {
		return new JevArticleEvaluator(URI.create("http://127.0.0.1:9"), "jev-latest",
				JevApiKey.of("tsk-test-only-0123456789"));
	}

}
