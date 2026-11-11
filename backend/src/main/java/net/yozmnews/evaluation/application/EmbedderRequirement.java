package net.yozmnews.evaluation.application;

import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import net.yozmnews.platform.WorkerRole;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 작업 역할의 기동 검사 — 작업 역할은 임베더 없이 운영하지 않는다 · docs/specs/evaluation.md §4 · evaluation-R46.
 * <p>
 * 임베더 빈이 없으면 작업 역할이 뜨지 않는다. 화면 역할만 띄운 프로세스는 임베더를 요구하지 않는다. 임베더를 만들지 못한 경우(모델 · 설정
 * 오류)는 결선이 먼저 기동을 멈춘다.
 */
@WorkerRole
@Component
class EmbedderRequirement {

	static final String NO_EMBEDDER = "임베더가 없어 작업 역할을 띄울 수 없다 — " + EmbeddingProperties.PREFIX
			+ ".provider 를 확인한다";

	/**
	 * @throws IllegalStateException 임베더 빈이 없을 때
	 */
	EmbedderRequirement(ObjectProvider<ArticleEmbedder> embedders) {
		if (embedders.getIfAvailable() == null) {
			throw new IllegalStateException(NO_EMBEDDER);
		}
	}

}
