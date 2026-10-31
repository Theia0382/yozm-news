/**
 * 운영자 기능 — 운영자 화면이 부르는 HTTP API. 각 모듈의 공개 운영 API 를 부른다. docs/specs/admin.md.
 */
@ApplicationModule(allowedDependencies = { "source", "platform" })
package net.yozmnews.admin;

import org.springframework.modulith.ApplicationModule;
