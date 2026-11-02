package ddolite.builder.model

/**
 * One line of FeatMap.txt: the same feat's name in this app, in Ron's Character Planner and in
 * DDO Builder, used when importing and exporting builds. Ported from `FeatMapType`
 * (Build/basDataDefs.bas:254). Any of the three may be empty.
 *
 * @property lite the name here, as "Feat" or "Feat: Selector".
 */
data class FeatMapEntry(val lite: String, val ron: String, val builder: String)

/**
 * A feat every character gets at a level (AutoGrantedFeats.txt), e.g. Heroic Durability.
 * Ported from `AutoGrantedFeatType` (Build/basDataDefs.bas:283).
 */
data class AutoGrantedFeat(val featName: String, val level: Int)
