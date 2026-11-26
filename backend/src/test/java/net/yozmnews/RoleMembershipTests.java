package net.yozmnews;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import net.yozmnews.platform.WebRole;
import net.yozmnews.platform.WorkerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 역할 소속 — main 클래스의 패키지와 애너테이션으로 구성요소가 맞는 역할에 속하는지 본다. 규칙마다 대상이 있는지 먼저 센다.
 * <p>
 * 지키는 규칙: platform-R9
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleMembershipTests {

	/** main 클래스 — 이 시험 클래스의 수명 동안만 든다(docs/development/infrastructure.md "테스트 JVM") */
	private final JavaClasses classes = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("net.yozmnews");

	private static final DescribedPredicate<CanBeAnnotated> SPRING_COMPONENT = metaAnnotatedWith(Component.class);

	private static final DescribedPredicate<CanBeAnnotated> WEB_ROLE = metaAnnotatedWith(WebRole.class);

	private static final DescribedPredicate<CanBeAnnotated> WORKER_ROLE = metaAnnotatedWith(WorkerRole.class);

	private static final DescribedPredicate<JavaClass> MODULE_LISTENER = DescribedPredicate.describe(
			"have an @ApplicationModuleListener method",
			type -> type.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(ApplicationModuleListener.class)));

	@Test
	void webAdaptersBelongToTheWebRole() {
		DescribedPredicate<JavaClass> webAdapters = resideInAPackage("..adapter.in.web..").and(SPRING_COMPONENT);
		assertThat(classes.that(webAdapters)).hasSizeGreaterThanOrEqualTo(2);

		classes().that(webAdapters).should().beMetaAnnotatedWith(WebRole.class).check(classes);
	}

	@Test
	void jobEntryPointsBelongToTheWorkerRole() {
		DescribedPredicate<JavaClass> jobEntryPoints = resideInAPackage("..adapter.in.job..").and(SPRING_COMPONENT);
		assertThat(classes.that(jobEntryPoints)).hasSizeGreaterThanOrEqualTo(4);

		classes().that(jobEntryPoints).should().beMetaAnnotatedWith(WorkerRole.class).check(classes);
	}

	@Test
	void eventListenersBelongToBothRoles() {
		// 리스너는 발행한 프로세스에서 돈다 — 한 역할에서 빼면 그 역할이 발행한 이벤트를 받지 못한다
		assertThat(classes.that(MODULE_LISTENER)).hasSizeGreaterThanOrEqualTo(1);

		classes().that(MODULE_LISTENER)
			.should()
			.notBeMetaAnnotatedWith(WebRole.class)
			.andShould()
			.notBeMetaAnnotatedWith(WorkerRole.class)
			.check(classes);
	}

	@Test
	void noComponentBelongsToBothRoles() {
		assertThat(classes.that(WEB_ROLE)).hasSizeGreaterThanOrEqualTo(2);
		assertThat(classes.that(WORKER_ROLE)).hasSizeGreaterThanOrEqualTo(6);

		classes().that(WEB_ROLE).should().notBeMetaAnnotatedWith(WorkerRole.class).check(classes);
	}

}
