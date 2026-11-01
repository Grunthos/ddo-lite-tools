package ddolite.builder.data

/**
 * A Character Builder data file that the VB6 loader splits into records, with the marker
 * passed to `Split`. Taken from the Load* routines in Source/Modules/Build/basDataLoad.bas:
 * LoadRaces (:81), LoadClasses (:225), LoadSpells (:454), LoadTemplates (:602),
 * LoadFeats (:709), LoadAutoGrantedFeats (:1274), LoadEnhancements (:1336),
 * LoadDestinies (:1380), LoadNameChanges (:2018).
 *
 * Not listed: FeatMap.txt, EpicGrantedFeats.txt, Exceptions.txt and Help.txt, which are read
 * differently and will be added when their loaders are ported.
 */
enum class BuilderRecordFile(
    val path: String,
    val marker: String,
    /** True if the VB loader reads the record's lines with [parseLine]. */
    val usesParseLine: Boolean = true,
) {
    RACES("Builder/Races.txt", "RaceName: "),
    CLASSES("Builder/Classes.txt", "ClassName: "),
    SPELLS("Builder/Spells.txt", "SpellName: "),

    /** Read with its own `ParseTemplate` (basDataLoad.bas:672), not `ParseLine`. */
    TEMPLATES("Builder/Templates.txt", "Class: ", usesParseLine = false),
    FEATS("Builder/Feats.txt", "FeatName: "),

    /** No ": " in the marker – the level follows it ("GrantedFeats5: ..."); VB splits by hand on ':' and ", ". */
    AUTO_GRANTED_FEATS("Builder/AutoGrantedFeats.txt", "GrantedFeats", usesParseLine = false),
    ENHANCEMENTS("Builder/Enhancements.txt", "TreeName: "),
    DESTINIES("Builder/Destinies.txt", "DestinyName: "),
    NAME_CHANGES("Builder/NameChange.txt", "NameChangeType: "),
}

/**
 * Markers that split a record into sub-records: abilities within an enhancement tree or
 * destiny (basDataLoad.LoadTree, :1411), and selector options within a feat or ability
 * (LoadFeat :947, LoadAbility :1713).
 */
object BuilderSubMarkers {
    const val ABILITY = "AbilityName: "
    const val SELECTOR = "SelectorName: "
}
