package ddolite.tools.formlayout

import ddolite.dataformat.decodeText
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Writes a layout report (Markdown) and wireframe pictures (PNG) for VB6 forms.
 *
 * Usage: `form-layout <output dir> <form.frm>...`
 * (`./gradlew :tools:form-layout:run` does the Builder's forms into build/form-layout).
 * New code for plan step F0; see PLAN.md §8.1.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: form-layout <output dir> <form.frm>...")
        System.exit(2)
    }
    val outDir = Path.of(args[0])
    Files.createDirectories(outDir)
    for (i in 1 until args.size) {
        val frm = Path.of(args[i])
        val formName = frm.fileName.toString().removeSuffix(".frm")
        val file = parseFrm(decodeText(Files.readAllBytes(frm)).text)
        val root = placeControls(file.form)

        val imageNames = ArrayList<String>()
        for (view in wireframeViews(root)) {
            var imageName = "$formName.png"
            if (view.title != "main") {
                imageName = formName + "-" + view.title.replace("(", "").replace(")", "") + ".png"
            }
            ImageIO.write(drawWireframe(root, view), "png", outDir.resolve(imageName).toFile())
            imageNames.add(imageName)
        }
        val report = layoutReport(frm.fileName.toString(), file, root, imageNames)
        Files.writeString(outDir.resolve("$formName.md"), report)
        println(formName + ": " + imageNames.size + " wireframe(s)")
    }
    println("Written to " + outDir.toAbsolutePath())
}
