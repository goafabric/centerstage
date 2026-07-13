import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val javaVersion = "25"
java.sourceCompatibility = JavaVersion.toVersion(javaVersion)
tasks.withType<KotlinCompile>().all { compilerOptions { jvmTarget.set(JvmTarget.fromTarget(javaVersion)); javaParameters = true } }

val dockerRegistry = "goafabric"

plugins {
	java
	jacoco
	id("io.quarkus") version "3.37.0"
	id("net.researchgate.release") version "3.1.0"
	id("org.sonarqube") version "7.3.1.8318"

	kotlin("jvm") version "2.4.0"
	kotlin("plugin.jpa") version "2.4.0"
	kotlin("plugin.allopen") version "2.4.0"
	kotlin("kapt") version "2.4.0"
}

repositories {
	mavenCentral()
}

dependencies {
	constraints {
		annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
		implementation("org.mapstruct:mapstruct:1.6.3")

		kapt("org.mapstruct:mapstruct-processor:1.6.3")

		testImplementation("org.assertj:assertj-core:3.27.7")
		testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
		testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
	}

	kapt(enforcedPlatform("io.quarkus:quarkus-bom:3.37.0"))
	implementation(enforcedPlatform("io.quarkus:quarkus-bom:3.37.0"))
}

dependencies {
	//web
	implementation("io.quarkus:quarkus-arc")
	implementation("io.quarkus:quarkus-rest-jackson")
	implementation("org.jboss.logmanager:log4j2-jboss-logmanager")

	//monitoring
	implementation("io.quarkus:quarkus-smallrye-health")
	implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
	//implementation("io.quarkus:quarkus-smallrye-openapi")
	//implementation("io.quarkus:quarkus-opentelemetry")

	//crosscutting
	implementation("io.quarkus:quarkus-hibernate-validator")

	//persistence
	implementation("io.quarkus:quarkus-data-hibernate")
	implementation("io.quarkus:quarkus-jdbc-h2")
	implementation("io.quarkus:quarkus-flyway")
	implementation("io.quarkus:quarkus-scheduler")
	kapt("org.hibernate.orm:hibernate-processor")
	runtimeOnly("com.h2database:h2")

	//yaml parsing
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

	//adapter
	implementation("io.quarkus:quarkus-rest-client-jackson")
	implementation("io.quarkus:quarkus-smallrye-fault-tolerance")

	//jib
	implementation("io.quarkus:quarkus-container-image-jib")

	//code generation
	implementation("org.mapstruct:mapstruct")
	kapt("org.mapstruct:mapstruct-processor")

	//kotlin
	implementation("io.quarkus:quarkus-kotlin")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	//test
	testImplementation("io.quarkus:quarkus-junit5")
	testImplementation("io.rest-assured:rest-assured")
	testImplementation("io.quarkus:quarkus-jacoco")
	testImplementation("org.assertj:assertj-core")
	testImplementation("com.tngtech.archunit:archunit-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin")
	testImplementation("io.quarkus:quarkus-junit-mockito")
}

tasks.withType<Test> {
	useJUnitPlatform()
	exclude("**/*NRIT*")
	systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
	finalizedBy("jacocoTestReport")
}

tasks.jacocoTestReport {
	executionData.setFrom(
		fileTree(layout.buildDirectory.get()).include("jacoco/test.exec", "jacoco-quarkus.exec")
	)
	reports { xml.required.set(true); csv.required.set(true); html.required.set(true) }
}

tasks.register<Exec>("dockerImageNative") { description = "native image"; group = "build"; dependsOn("quarkusBuild", "testNative")
	if (gradle.startParameter.taskNames.contains("dockerImageNative")) {
		if (System.getProperty("os.arch").equals("aarch64")) {
			System.setProperty("quarkus.jib.platforms", "linux/arm64/v8")
		}

		System.setProperty("quarkus.native.builder-image", "quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-25")
		System.setProperty("quarkus.package.jar.enabled", "false")

		System.setProperty("quarkus.native.enabled", "true")
		System.setProperty("quarkus.native.container-build", "true")
		System.setProperty("quarkus.container-image.build", "true")

		System.setProperty("quarkus.native.native-image-xmx", "8000m")
		System.setProperty("quarkus.container-image.image", "${dockerRegistry}/${project.name}:${project.version}")

		commandLine("/bin/sh", "-c", "docker push ${dockerRegistry}/${project.name}:${project.version}")
	}
}

configure<net.researchgate.release.ReleaseExtension> {
	buildTasks.set(listOf("build", "test", "dockerImageNative"))
	tagTemplate.set("v${version}".replace("-SNAPSHOT", ""))
}

allOpen {
	annotation("jakarta.ws.rs.Path")
	annotation("jakarta.enterprise.context.ApplicationScoped")
	annotation("jakarta.persistence.Entity")
	annotation("io.quarkus.test.junit.QuarkusTest")
}

sonarqube {
	properties {
		property("sonar.exclusions", "**/.github/workflows/**")
	}
}

tasks.matching { it.name == "checkSnapshotDependencies" }.configureEach {
	enabled = false
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
	freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}