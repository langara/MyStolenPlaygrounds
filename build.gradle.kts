@file:OptIn(okio.ExperimentalFileSystem::class)
import okio.FileSystem.Companion.SYSTEM
import okio.Path.Companion.toPath
import okio.Path
import kotlin.text.RegexOption.DOT_MATCHES_ALL
import kotlin.text.RegexOption.MULTILINE

task("stealAndroidxComposeSamples") {
    doLast {
        val androidxRootDir = "/home/marek/code/android/androidx-main".toPath()
        val androidxSupportDir = androidxRootDir / "frameworks/support"
        val srcKotlinDir = project.rootOkioPath / "app/src/main/kotlin"
        val stolenSrcKotlinDir = srcKotlinDir / "stolen"
        val templatesSrcKotlinDir = srcKotlinDir / "templates"

        // just copy the @Sampled annotation definition
        SYSTEM.processEachKtFile(
            inputRootDir = androidxSupportDir / "annotation/annotation-sampled/src/main/java/androidx/annotation",
            outputRootDir = stolenSrcKotlinDir / "androidx-annotation"
        ) { _, _, content -> content }

        val samples = mutableListOf<Pair<String, Path?>>() // funName to filePath

        // copy samples from ui/ui-graphics/samples and collect @Sampled @Composable fun names
        SYSTEM.processEachKtFile(
            inputRootDir = androidxSupportDir / "compose/ui/ui-graphics/samples/src/main/java/androidx/compose/ui/graphics/samples",
            outputRootDir = stolenSrcKotlinDir / "ui-graphics-samples"
        ) { inputPath, outputPath, sampleFileContent ->
            samples += sampleFileContent.findSampledComposableFunNames().map { it to outputPath }
            sampleFileContent
        }

        // copy samples from animation/animation-core/samples and collect @Sampled @Composable fun names
        SYSTEM.processEachKtFile(
            inputRootDir = androidxSupportDir / "compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples",
            outputRootDir = stolenSrcKotlinDir / "animation-core-samples"
        ) { inputPath, outputPath, sampleFileContent ->
            samples += sampleFileContent.findSampledComposableFunNames().map { it to outputPath }
            sampleFileContent
        }

        // copy samples from animation/animation/samples and collect @Sampled @Composable fun names
        SYSTEM.processEachKtFile(
            inputRootDir = androidxSupportDir / "compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples",
            outputRootDir = stolenSrcKotlinDir / "animation-samples"
        ) { inputPath, outputPath, sampleFileContent ->
            samples += sampleFileContent.findSampledComposableFunNames().map { it to outputPath }
            sampleFileContent
        }

        SYSTEM.processEachKtFile(templatesSrcKotlinDir, templatesSrcKotlinDir) { inputPath, outputPath, templateFileContent ->

            val ureBigIdent = ure {
                1 of posixUpper
                0..MAX of (word or digit)
            }

            val ureParamsNotNested = ure { // I assume no internal expressions with parenthesis
                1 of ch("\\(")
                0..MAX of oneCharNotOf("(")
                1 of ch("\\)")
            }

            val ureAnnotations = ure {
                1..MAX of { // single annotation
                    1 of ch("@")
                    1 of ureBigIdent
                    0..1 of ureParamsNotNested
                    1..MAX of space
                }
            }

            val ureAnnotationsWithComposable = ure {
                1 of ureAnnotations
                1 of lookBehind {
                    1 of ir("@Composable")
                    1..100 of space
                }
            }

            val ureMaybeSomeSpaces = ure { 0..MAX of ch(" ") }

            val ureIndentedNotEmptyLineContent = ure {
                1 of ch(" ")
                1 of ureMaybeSomeSpaces
                1 of oneCharNotOf(" ", "\\n")
                0..MAX of oneCharNotOf("\\n")
            }

            val ureIndentedLine = ure(MULTILINE) {
                1 of BOL
                1 of (ureIndentedNotEmptyLineContent or ureMaybeSomeSpaces)
                1 of lf
            }

            val ureComposableFunTemplate = ure(MULTILINE) {
                1 of ureAnnotationsWithComposable
                0..MAX of space // annotations can contain ending spaces too
                1 of BOL // we have to start from new line to easier find ending brace }
                1 of ir("fun")
                1 of space
                1 of ure("funName") { 1 of ureBigIdent }
                1 of ir("Template")
                1 of ureParamsNotNested
                1 of space
                1 of ch("\\{")
                1 of lf
                1..MAX of ureIndentedLine
                1 of BOL
                1 of ch("\\}")
                1 of lf
            }

            val ureBeginGenerationAreaMarker = ure(MULTILINE) {
                1 of BOL
                1 of ir("// BEGIN generated ")
                1 of ref(name = "funName")
                1 of ir(" from ")
                1 of ref(name = "funName")
                1 of ir("Template")
                1 of lf
            }

            val ureEndGenerationAreaMarker = ure(MULTILINE) {
                1 of BOL
                1 of ir("// END generated ")
                1 of ref(name = "funName")
                1 of ir(" from ")
                1 of ref(name = "funName")
                1 of ir("Template")
                1 of lf

            }

            val ureContentWithTemplate = ure {
                1 of ure("partBeforeTemplate", DOT_MATCHES_ALL) { x(0..MAX, reluctant = true) of any }
                1 of ure("partTemplate") { 1 of ureComposableFunTemplate }
                1 of ure("partBeforeGenerationArea", DOT_MATCHES_ALL) { 0..MAX of any }
                1 of ure("partBeginGenerationAreaMarker") { 1 of ureBeginGenerationAreaMarker }
                1 of ure("partGenerationArea", DOT_MATCHES_ALL) { 0..MAX of any }
                1 of ure("partEndGenerationAreaMarker")  { 1 of ureEndGenerationAreaMarker }
                1 of ure("partAfterGenerationArea", DOT_MATCHES_ALL) { 0..MAX of any }
            }

            val result = ureContentWithTemplate.compile().matchEntire(templateFileContent) ?: error("matchEntire failed")
            val groups = result.groups

            val newGenerationArea = processFunTemplate(
                template = groups["partTemplate"]!!.value,
                templateFunName = groups["funName"]!!.value,
                samples = samples
            )

            val newTemplateFileContent =
                groups["partBeforeTemplate"]!!.value +
                groups["partTemplate"]!!.value +
                groups["partBeforeGenerationArea"]!!.value +
                groups["partBeginGenerationAreaMarker"]!!.value +
                newGenerationArea +
                groups["partEndGenerationAreaMarker"]!!.value +
                groups["partAfterGenerationArea"]!!.value

            newTemplateFileContent
        }
    }
}

fun String.findSampledComposableFunNames(): Sequence<String> {
    val ureFunHeader = ure(MULTILINE) {
        1 of BOL
        1 of ir("@Sampled")
        1..MAX of space
        1 of ir("@Composable")
        1..MAX of space
        1 of ir("fun")
        1..MAX of space
        1 of ure("funName") {
            1 of posixUpper
            0..MAX of (word or digit)
        }
        1 of ir("\\(\\)")
    }

    return ureFunHeader.compile()
        .findAll(this)
        .map { it.groups["funName"]!!.value.also { println("found fun: $it") } }
}

fun ureWholeLineWith(text: UreIR) = ure(MULTILINE) {
    1 of BOL
    0..MAX of any
    1 of ir(text) // TODO_later: maybe force literal texts
    0..MAX of any
    1 of EOL
    1 of lf
}

fun processFunTemplate(template: String, templateFunName: String, samples: List<Pair<String, Path?>>) =
    template
        .replace(templateFunName + "Template", templateFunName)
        .replace(ureWholeLineWith("// REMOVE").compile(), "")
        .replace(ureWholeLineWith("// REPLACE").compile(),
            samples
                .joinToString(
                    prefix = " ".repeat(8),
                    separator = "\n" + " ".repeat(8),
                    postfix = "\n",
                    transform = { (name, path) ->
                        val pathStr = if (path == null) "null" else "\"$path\""
                        "MySampleData(\"$name\", $pathStr) { $name() },"
                    }
                )
        )
