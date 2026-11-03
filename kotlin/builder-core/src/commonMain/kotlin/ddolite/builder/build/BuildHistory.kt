package ddolite.builder.build

import ddolite.builder.data.BuilderData

/**
 * Undo and redo for one open build. New code: VB has no undo.
 *
 * The history holds copies of the build ([Build.deepCopy]) as it was after each change, starting
 * with the build as opened. [record] adds the current state after a change (dropping anything that
 * had been undone); [undo] and [redo] return the state to show: a fresh build with its rules ([BuildRules.build]).
 * The copies are the build alone: the rules' lists are worked out again by [restore].
 *
 * @param limit the most states kept; the oldest go first.
 */
class BuildHistory(private val data: BuilderData, private val limit: Int = 200) {
    private val states = ArrayList<Build>()
    private var current = -1

    /** The position of the state last saved to a file (or opened), or -1 if that's been dropped. */
    private var saved = -1

    val canUndo: Boolean get() = current > 0
    val canRedo: Boolean get() = current >= 0 && current < states.size - 1

    /** True if the current state is the one last saved or opened (so it has no unsaved changes). */
    val atSaved: Boolean get() = current == saved

    /** Starts again with [build] as the only state, as opened or new (and saved). */
    fun reset(build: Build) {
        lastKey = null
        states.clear()
        states.add(build.deepCopy())
        current = 0
        saved = 0
    }

    /** No build open. */
    fun clear() {
        states.clear()
        current = -1
        saved = -1
    }

    /** The build was saved: its current state is the saved one. */
    fun markSaved() {
        saved = current
    }

    /** The current state isn't in a file (an imported build, or a restored backup). */
    fun markUnsaved() {
        saved = -1
    }

    /** The [record] key of the newest state, while it can still be merged into. */
    private var lastKey: String? = null

    /**
     * After a change: [build] as it is now becomes the newest state. Changes with the same [mergeKey]
     * in a row (typing in one text box) make one step: each replaces the last.
     */
    fun record(build: Build, mergeKey: String? = null) {
        if (current < 0) {
            return
        }
        if (mergeKey != null && mergeKey == lastKey && current == states.size - 1 && current > 0 && saved != current) {
            states[current] = build.deepCopy()
            return
        }
        lastKey = mergeKey
        while (states.size > current + 1) {
            states.removeAt(states.size - 1)
        }
        if (saved > current) {
            saved = -1
        }
        states.add(build.deepCopy())
        current++
        if (states.size > limit) {
            states.removeAt(0)
            current--
            saved--
        }
    }

    /** The state before the current one, or null if there's none. */
    fun undo(): BuildRules? {
        if (!canUndo) {
            return null
        }
        lastKey = null
        current--
        return restore(states[current])
    }

    /** The state after the current one (after an undo), or null if there's none. */
    fun redo(): BuildRules? {
        if (!canRedo) {
            return null
        }
        lastKey = null
        current++
        return restore(states[current])
    }

    /**
     * A working copy of [state] with its rules worked out: the feats listed first, so the spell slots
     * keep their pact spells (a fresh [BuildRules.cascadeAll] alone would empty them, as VB does when
     * a file is opened), then everything else as after any change.
     */
    private fun restore(state: Build): BuildRules {
        val build = state.deepCopy()
        val rules = BuildRules(data, build)
        if (build.levelClass(1) != null) {
            rules.initBuildFeats()
        }
        rules.cascadeAll()
        return rules
    }
}
