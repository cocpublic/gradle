plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains the Java plugin, and it supporting classes.  This plugin is used as the basis for building a Java library or application by more specific plugins, and is sometimes applied by other JVM language projects."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":diagnostics"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":logging"))
    implementation(project(":ivy"))
    implementation(project(":maven"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-jvm-test-suite-base"))
    implementation(project(":publish"))
    implementation(project(":reporting"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.ant)
    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}
