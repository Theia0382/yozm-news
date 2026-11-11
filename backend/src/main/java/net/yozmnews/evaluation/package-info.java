/**
 * 모델 평가 관문 — 판정마다 공개 API 하나(태그 판정 · 사건 소속 판정)로 모델 요청을 조립해 부르고 결과를 대조해 판정 원본을
 * 기록한다. 모델 포트(Jev · 로컬 대체물), 판정 원본 조회, 지운 기사의 판정 원본 지우기. 기사 임베딩 공개 API 하나(결과를 확인하고 기록하지
 * 않는다)와 임베더 포트(로컬 모델 · OpenAI 호환 · 로컬 대체물). docs/specs/evaluation.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "article", "source" })
package net.yozmnews.evaluation;

import org.springframework.modulith.ApplicationModule;
