package net.yozmnews.admin.adapter.in.web;

/**
 * 지우면 함께 지워질 기사 수 — 그 소스(피드)만 가진 기사, 지금 기준. docs/specs/admin.md §3
 */
record DeletionImpactResponse(long exclusiveArticles) {
}
