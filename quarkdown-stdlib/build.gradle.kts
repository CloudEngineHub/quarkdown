plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(testFixtures(project(":quarkdown-core")))
    implementation(project(":quarkdown-core"))
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.10.0")
    implementation("org.kodein.emoji:emoji-kt:2.2.0")
    dokkaPlugin(project(":quarkdown-quarkdoc"))
}
