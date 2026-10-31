package net.yozmnews.platform.application;

import net.yozmnews.platform.WorkerRole;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 작업 역할의 스케줄링 — docs/specs/platform.md §4 · platform-R9
 * <p>
 * Spring Modulith 의 멈춤 감시(platform-R2)가 이것으로 돈다.
 */
@WorkerRole
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class WorkerSchedulingConfiguration {

}
