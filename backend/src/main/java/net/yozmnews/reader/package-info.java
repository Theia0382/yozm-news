/**
 * 독자 화면 — 인스턴스 사용자가 뉴스 내용을 보는 화면이 부르는 HTTP API. 각 모듈의 공개 조회를 엮어 응답을 정하고 데이터 · 판정을 갖지 않는다.
 * docs/specs/reader.md.
 */
@ApplicationModule(allowedDependencies = { "storyline", "event", "article", "digest", "platform" })
package net.yozmnews.reader;

import org.springframework.modulith.ApplicationModule;
