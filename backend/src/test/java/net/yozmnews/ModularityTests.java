package net.yozmnews;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * 모듈 경계 — Spring Modulith 가 모듈 구조를 검증하고 모듈 문서를 쓴다
 */
class ModularityTests {

	ApplicationModules modules = ApplicationModules.of(YozmnewsApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

	@Test
	void writesDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
