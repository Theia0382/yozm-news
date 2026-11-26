package net.yozmnews;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 분석 결과의 수명 — 시험이 ArchUnit 으로 읽은 클래스 집합({@link JavaClasses})을 static 필드에 두지 않는지 본다. static 에 두면 그 시험
 * 클래스가 끝나도 테스트 JVM 이 끝날 때까지 남는다(docs/development/infrastructure.md "테스트 JVM").
 */
class ImportedClassesLifetimeTests {

	@Test
	void testsHoldImportedClassesOnlyForTheirOwnLifetime() {
		JavaClasses tests = new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
			.importPackages("net.yozmnews");
		// 대조 — 시험 클래스를 실제로 읽었고, 분석 결과를 필드로 드는 시험이 있다. 없으면 아래 규칙이 무엇이든 통과한다
		assertThat(tests.contain(StoredKeyBoundaryTests.class)).isTrue();
		assertThat(fieldsHoldingImportedClasses(tests)).hasSizeGreaterThanOrEqualTo(3);

		noFields().that().haveRawType(JavaClasses.class).should().beStatic().check(tests);
	}

	private static List<String> fieldsHoldingImportedClasses(JavaClasses classes) {
		return classes.stream()
			.flatMap(type -> type.getFields().stream())
			.filter(field -> field.getRawType().isEquivalentTo(JavaClasses.class))
			.map(field -> field.getFullName())
			.toList();
	}

}
