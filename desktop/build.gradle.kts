plugins { application }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies {
    implementation(project(":engine"))
    implementation("net.java.dev.jna:jna:5.17.0")
}
application { mainClass.set("com.ezsocial.desktop.App") }
