package ddolite.builder.data

import ddolite.builder.model.AutoGrantedFeat
import ddolite.builder.model.Feat
import ddolite.builder.model.FeatMapEntry
import ddolite.dataformat.DataSource
import ddolite.dataformat.Vb
import ddolite.dataformat.cleanText
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

private const val FEAT_MAP_FILE = "FeatMap.txt"
private const val AUTO_GRANTED_FILE = "AutoGrantedFeats.txt"

/** Builder/FeatMap.txt: tab-separated, one feat per line, after a heading line. */
const val FEAT_MAP_PATH = "Builder/FeatMap.txt"

/**
 * Loads FeatMap.txt. Ported from `basDataLoad.LoadFeatMap` (Build/basDataLoad.bas:1218).
 *
 * The first line is a heading and is skipped. Every other line must have exactly three
 * tab-separated names; VB keeps a blank entry for a bad line (so the entries keep their line
 * positions), and so does this.
 */
fun loadFeatMap(source: DataSource, log: LoadLog): List<FeatMapEntry> {
    val decoded = source.readText(FEAT_MAP_PATH)
    if (decoded == null) {
        log.fileNotFound(FEAT_MAP_FILE)
        return emptyList()
    }
    val lines = Vb.split(decoded.text, "\n")
    val entries = ArrayList<FeatMapEntry>()
    if (lines.size < 2) {
        // VB: actLoadFeatMap with LineNumber 0.
        log.error(FEAT_MAP_FILE, null, "$FEAT_MAP_FILE: File is empty")
        return entries
    }
    for (i in 1 until lines.size) {
        val tokens = Vb.split(lines[i], "\t")
        if (tokens.size == 3) {
            entries.add(FeatMapEntry(tokens[0], tokens[1], tokens[2]))
        } else {
            // VB: "Line " & log.LineNumber + 1, i.e. the 1-based line number.
            log.error(FEAT_MAP_FILE, i + 1, "$FEAT_MAP_FILE: Line ${i + 1} is invalid:", lines[i])
            entries.add(FeatMapEntry("", "", ""))
        }
    }
    return entries
}

/**
 * Checks that each FeatMap entry names a real feat, and selector if it has one ("Feat: Selector").
 * Ported from `basProcess.ProcessFeatMap` (Build/basProcess.bas:64).
 */
internal fun checkFeatMap(entries: List<FeatMapEntry>, data: BuilderData, log: LoadLog) {
    for (entry in entries) {
        if (entry.lite.isEmpty()) {
            continue
        }
        var featName = entry.lite
        var selectorName = ""
        val separator = entry.lite.indexOf(": ")
        if (separator >= 0) {
            featName = entry.lite.substring(0, separator)
            selectorName = entry.lite.substring(separator + 2)
        }
        val feat = data.findFeat(featName)
        if (feat == null) {
            log.error(FEAT_MAP_FILE, null, "$FEAT_MAP_FILE: Feat not found: $featName")
        } else if (selectorName.isNotEmpty() && feat.findSelector(selectorName) == null) {
            log.error(FEAT_MAP_FILE, null, "$FEAT_MAP_FILE: $featName selector not found: $selectorName")
        }
    }
}

/**
 * Loads AutoGrantedFeats.txt. Ported from `basDataLoad.LoadAutoGrantedFeats` (Build/basDataLoad.bas:1254).
 *
 * The file is split on "GrantedFeats"; each part is "<level>: feat, feat, …". Quirks kept: the
 * part is cut at its *first* colon after the level and the rest up to the next colon is the list,
 * so a feat name containing ':' would be cut short; the list is split on ", " exactly.
 * A feat that doesn't exist (exact name) is reported with VB's `ErrorLoading`, whose message for
 * this file has no explanation in brackets.
 *
 * Difference: VB's `CInt` stops with a Type mismatch error if the level isn't a number; here the
 * part is reported and skipped.
 */
fun loadAutoGrantedFeats(source: DataSource, feats: List<Feat>, log: LoadLog): List<AutoGrantedFeat> {
    val decoded = source.readText(BuilderRecordFile.AUTO_GRANTED_FEATS.path)
    if (decoded == null) {
        log.fileNotFound(AUTO_GRANTED_FILE)
        return emptyList()
    }
    val featNames = HashSet<String>()
    for (feat in feats) {
        featNames.add(feat.name)
    }
    val result = ArrayList<AutoGrantedFeat>()
    for (part in splitRecords(decoded.text, BuilderRecordFile.AUTO_GRANTED_FEATS.marker).records) {
        if (!part.body.contains(':')) {
            log.error(AUTO_GRANTED_FILE, part.line, "Invalid section in $AUTO_GRANTED_FILE", Vb.split(part.body, "\n")[0])
            continue
        }
        val pieces = Vb.split(part.body, ":")
        if (!Vb.isNumeric(pieces[0])) {
            log.vbDifference(AUTO_GRANTED_FILE, part.line, "level isn't a number: GrantedFeats${pieces[0]} (VB6 stops with a Type mismatch error)")
            continue
        }
        val level = Vb.cLng(Vb.valOf(pieces[0]))
        val list = Vb.trim(cleanText(pieces[1]))
        for (name in Vb.split(list, ", ")) {
            if (featNames.contains(Vb.trim(name))) {
                result.add(AutoGrantedFeat(name, level))
            } else {
                log.error(AUTO_GRANTED_FILE, part.line, "Invalid section in $AUTO_GRANTED_FILE", name)
            }
        }
    }
    return result
}
