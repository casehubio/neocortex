package io.casehub.neocortex.thing;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.casehub.neocortex.thing")
class DependencyConstraintTest {

    @ArchTest
    static final ArchRule noQuarkus = noClasses().should()
        .dependOnClassesThat().resideInAnyPackage("io.quarkus..", "jakarta..");

    @ArchTest
    static final ArchRule noSpring = noClasses().should()
        .dependOnClassesThat().resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule noCasehubDomain = noClasses()
        .that().resideInAPackage("io.casehub.neocortex.thing..")
        .should().dependOnClassesThat(
            DescribedPredicate.describe("casehub classes outside thing-api",
                (JavaClass cls) -> cls.getPackageName().startsWith("io.casehub.")
                    && !cls.getPackageName().startsWith("io.casehub.neocortex.thing")));

    @ArchTest
    static final ArchRule noCognitive = noClasses().should()
        .dependOnClassesThat().resideInAnyPackage("io.casehub.neocortex.cognitive..");

    @ArchTest
    static final ArchRule noMindmap = noClasses().should()
        .dependOnClassesThat().resideInAnyPackage("io.casehub.neocortex.mindmap..");

    @ArchTest
    static final ArchRule noPlatform = noClasses().should()
        .dependOnClassesThat().resideInAnyPackage("io.casehub.platform..");
}
