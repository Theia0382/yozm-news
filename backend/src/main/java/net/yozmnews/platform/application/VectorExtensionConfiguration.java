package net.yozmnews.platform.application;

import net.yozmnews.platform.domain.DatabaseExtensions;
import net.yozmnews.platform.domain.VectorExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * pgvector 확장 요건 검사의 결선 — docs/specs/platform.md §4 · platform-R19
 * <p>
 * 빈을 만들 때 설치 버전을 읽어 판정한다. 읽기가 받는 {@code JdbcClient} 는 DB 초기화(Flyway) 뒤에 만들어지므로 첫 기동에서도 마이그레이션이 설치한
 * 확장을 본다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Configuration(proxyBeanMethods = false)
class VectorExtensionConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(VectorExtensionConfiguration.class);

	@Bean
	VectorExtension vectorExtension(DatabaseExtensions extensions) {
		VectorExtension extension = VectorExtension.installed(extensions.installedVersion(VectorExtension.NAME));
		logger.info("pgvector 확장 버전 {} — 최소 버전 {} 을 충족한다", extension.version(), VectorExtension.MINIMUM_VERSION);
		return extension;
	}

}
