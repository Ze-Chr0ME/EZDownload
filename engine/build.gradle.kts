plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies {
    api("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.google.code.gson:gson:2.13.2")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("org.mp4parser:muxer:1.9.56")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
    providers.gradleProperty("smokeMedia").orNull?.let { systemProperty("ezsocial.fixture", it) }
}
