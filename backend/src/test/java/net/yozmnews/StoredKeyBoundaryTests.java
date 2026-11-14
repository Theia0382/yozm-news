package net.yozmnews;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import net.yozmnews.llm.StoredKey;
import net.yozmnews.llm.StoredKeySource;
import org.junit.jupiter.api.Test;

/**
 * 저장한 키의 공급 인터페이스의 경계 — 본 코드의 클래스 파일을 읽어, {@code llm} · {@code credential} 밖의 클래스가 공급 인터페이스의 두
 * 타입에 의존하지 않고 {@code credential} 안에서는 구현 하나만 의존함을 본다. 의존 선언이 없는 모듈은 모듈 검증이 막지 못해 이 시험이 막는다.
 * <p>
 * 지키는 규칙: llm-R18 · credential-R13
 */
class StoredKeyBoundaryTests {

	private static final JavaClasses PRODUCTION = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("net.yozmnews");

	@Test
	void onlyLlmAndCredentialDependOnTheSupplyInterface() {
		// 대조 — 본 코드를 실제로 읽었다. 빈 집합이면 아래 규칙이 무엇이든 통과한다
		assertThat(PRODUCTION.contain(StoredKeySource.class)).isTrue();
		assertThat(PRODUCTION.size()).isGreaterThan(500);

		noClasses().that()
			.resideOutsideOfPackages("net.yozmnews.llm..", "net.yozmnews.credential..")
			.should()
			.dependOnClassesThat()
			.belongToAnyOf(StoredKeySource.class, StoredKey.class)
			.check(PRODUCTION);
	}

	@Test
	void oneClassInCredentialKnowsTheSupplyInterface() {
		List<String> dependents = PRODUCTION.stream()
			.filter(type -> type.getPackageName().startsWith("net.yozmnews.credential"))
			.filter(StoredKeyBoundaryTests::dependsOnTheSupplyInterface)
			.map(JavaClass::getName)
			.toList();

		assertThat(dependents).containsExactly("net.yozmnews.credential.application.StoredKeys");
	}

	private static boolean dependsOnTheSupplyInterface(JavaClass type) {
		return type.getDirectDependenciesFromSelf()
			.stream()
			.map(dependency -> dependency.getTargetClass().getName())
			.anyMatch(name -> name.equals(StoredKeySource.class.getName()) || name.equals(StoredKey.class.getName()));
	}

}
