package org.goafabric.centerstage.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.Test

class ArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
        .importPackages("org.goafabric.centerstage")

    @Test
    fun `persistence should not access logic or controller`() {
        noClasses().that().resideInAPackage("..persistence..")
            .should().accessClassesThat().resideInAPackage("..logic..")
            .orShould().accessClassesThat().resideInAPackage("..controller..")
            .check(classes)
    }

    @Test
    fun `logic (excluding mapper) should not access controller layer classes`() {
        // DTOs in controller/dto are shared data classes — logic may use them directly.
        // This rule guards against logic calling into controller *behaviour* (e.g. CatalogController).
        noClasses().that().resideInAPackage("..logic..")
            .and().resideOutsideOfPackage("..logic.mapper..")
            .should().accessClassesThat()
            .resideInAPackage("..controller")   // exact package only, not subpackages
            .check(classes)
    }
}
