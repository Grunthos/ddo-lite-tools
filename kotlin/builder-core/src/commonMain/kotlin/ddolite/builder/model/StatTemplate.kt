package ddolite.builder.model

/**
 * A suggested stat spread for a class, from Templates.txt, offered on the Stats screen.
 * Ported from `TemplateType` (Build/basDataDefs.bas:270). Named `StatTemplate` because
 * "template" means other things in Kotlin.
 *
 * Differences from VB:
 * - [className] is the name as written in the file, not a `ClassEnum` (plan D-7); `loadBuilderData`
 *   checks it against the loaded classes.
 * - VB's `Divine` field is never set by the loader, so it's left out.
 *
 * @property trapping `Flags: Traps` – offered to builds that want trapping skills.
 * @property always `Flags: Always` – offered whether or not the build wants trapping.
 * @property caption short name shown in the list, e.g. "THF".
 * @property levelups the stat suggested for level-up points, or null if the file doesn't say.
 * @property statPoints points spent on each stat, for each build-point total:
 *   `statPoints[column][stat.ordinal]`, columns 0..4 being 28, 30, 32, 34 and 36 point builds
 *   (VB: `StatPoints(column, stat)`, and `28 + column * 2` in basProcess.ProcessTemplates).
 */
data class StatTemplate(
    val className: String,
    val trapping: Boolean,
    val always: Boolean,
    val caption: String,
    val description: String,
    val warning: String,
    val levelups: Stat?,
    val statPoints: List<List<Int>>,
) {
    /** Points on [stat] for build-point column [column] (0..4). VB: `StatPoints(column, stat)`. */
    fun points(column: Int, stat: Stat): Int = statPoints[column][stat.ordinal]

    /**
     * The name VB uses in error messages: "Class: Caption", plus " (Traps)" for a trapping template.
     * Ported from basErrorLog.LogTemplateName (Build/basErrorLog.bas:211).
     */
    val logName: String
        get() {
            var result = "$className: $caption"
            if (trapping) {
                result = "$result (Traps)"
            }
            return result
        }

    companion object {
        /** The five build-point columns (28 to 36 points). */
        const val COLUMNS = 5

        /** Build points for [column]: VB's `28 + column * 2`. */
        fun buildPoints(column: Int): Int = 28 + column * 2
    }
}
