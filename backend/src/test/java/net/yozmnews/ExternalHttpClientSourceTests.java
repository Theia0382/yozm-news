package net.yozmnews;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 외부 API 클라이언트 정본 — main 클래스가 JDK {@code HttpClient} 를 공용 구성 밖에서 만들거나 그 프로토콜 버전을 정하지 않는지 본다. 외부 API 를
 * 부르는 클라이언트는 {@code ExternalHttpClients} 하나에서 만든다.
 * <p>
 * 지키는 규칙: platform-R135
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalHttpClientSourceTests {

	/** main 소스에서 JDK 클라이언트를 만드는 유일한 클래스 */
	private static final String EXTERNAL_HTTP_CLIENTS = "net.yozmnews.platform.ExternalHttpClients";

	/** main 클래스 — 이 시험 클래스의 수명 동안만 든다(docs/development/infrastructure.md "테스트 JVM") */
	private final JavaClasses classes = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("net.yozmnews");

	private static final DescribedPredicate<JavaAccess<?>> CLIENT_CREATION = DescribedPredicate
		.describe("create a JDK HttpClient", access -> targets(access, HttpClient.class.getName(),
				Set.of("newBuilder", "newHttpClient")));

	private static final DescribedPredicate<JavaAccess<?>> VERSION_CHOICE = DescribedPredicate.describe(
			"choose the HTTP version of a JDK HttpClient",
			access -> targets(access, HttpClient.Builder.class.getName(), Set.of("version")));

	@Test
	void mainClassesCreateJdkHttpClientsOnlyThroughTheSharedConfiguration() {
		// 대상이 있는지 먼저 센다 — 같은 판정이 공용 구성의 생성 호출 하나를 잡는다
		assertThat(classes.get(EXTERNAL_HTTP_CLIENTS).getAccessesFromSelf()).filteredOn(CLIENT_CREATION).hasSize(1);
		noClasses().that()
			.doNotHaveFullyQualifiedName(EXTERNAL_HTTP_CLIENTS)
			.should()
			.accessTargetWhere(CLIENT_CREATION)
			.check(classes);
	}

	@Test
	void mainClassesChooseTheHttpVersionOnlyInTheSharedConfiguration() {
		// 대상이 있는지 먼저 센다 — 같은 판정이 공용 구성의 버전 고정 하나를 잡는다
		assertThat(classes.get(EXTERNAL_HTTP_CLIENTS).getAccessesFromSelf()).filteredOn(VERSION_CHOICE).hasSize(1);
		noClasses().that()
			.doNotHaveFullyQualifiedName(EXTERNAL_HTTP_CLIENTS)
			.should()
			.accessTargetWhere(VERSION_CHOICE)
			.check(classes);
	}

	private static boolean targets(JavaAccess<?> access, String owner, Set<String> names) {
		return access instanceof JavaCodeUnitAccess<?> codeUnitAccess
				&& codeUnitAccess.getTargetOwner().getFullName().equals(owner)
				&& names.contains(codeUnitAccess.getName());
	}

}
