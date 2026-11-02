plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(project(":data-format"))
    testImplementation(kotlin("test"))
}

application {
    mainClass = "ddolite.tools.formlayout.MainKt"
}

// `gradlew :tools:form-layout:run` does every Character Builder form into build/form-layout.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
    val forms = rootDir.resolve("../Source/Forms/Build").listFiles { f -> f.name.endsWith(".frm") }
    val formPaths = ArrayList<String>()
    if (forms != null) {
        forms.sortBy { it.name }
        for (form in forms) {
            formPaths.add(form.canonicalPath)
        }
    }
    args(listOf(layout.buildDirectory.dir("form-layout").get().asFile.path) + formPaths)
}
