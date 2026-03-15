package app.simple.felicity.decorations.typeface

import android.content.Context
import android.graphics.Typeface
import app.simple.felicity.decorations.constants.TypeFaceConstants
import app.simple.felicity.preferences.AppearancePreferences

object TypeFace {

    // stores the heavy fonts so they only build ONCE.
    private val typefaceCache = HashMap<String, Typeface>()

    private fun getFontAssetPath(appFont: String): String? {
        return when (appFont) {
            TypeFaceConstants.NOTOSANS -> "fonts/noto_sans.ttf"
            TypeFaceConstants.WORK_SANS -> "fonts/work_sans.ttf"
            TypeFaceConstants.INTER -> "fonts/inter.ttf"
            TypeFaceConstants.NUNITO -> "fonts/nunito.ttf"
            TypeFaceConstants.JOST -> "fonts/jost.ttf"
            TypeFaceConstants.EXO2 -> "fonts/exo2.ttf"
            TypeFaceConstants.SOURGUMMY -> "fonts/sour_gummy.ttf"
            else -> null
        }
    }

    private fun getFontWeight(style: Int): Int {
        return when (style) {
            TypefaceStyle.EXTRA_LIGHT.style -> 200
            TypefaceStyle.LIGHT.style -> 300
            TypefaceStyle.REGULAR.style -> 400
            TypefaceStyle.MEDIUM.style -> 500
            TypefaceStyle.BOLD.style -> 700
            TypefaceStyle.BLACK.style -> 900
            else -> 400
        }
    }

    fun getTypeFace(appFont: String, style: Int, context: Context): Typeface {
        val weight = getFontWeight(style)

        // Create a unique cache key (e.g., "Jost-700")
        val cacheKey = "$appFont-$weight"

        // CHECK CACHE FIRST: If we already built it, return instantly (0ms)
        typefaceCache[cacheKey]?.let { return it }

        val assetPath = getFontAssetPath(appFont)

        // Build the font (Takes ~30-50ms, but only happens ONCE per weight)
        val typeface = if (assetPath != null) {
            try {
                Typeface.Builder(context.assets, assetPath)
                    .setFontVariationSettings("'wght' $weight")
                    .build() ?: Typeface.create(null, weight, false)
            } catch (e: Exception) {
                Typeface.create(null, weight, false)
            }
        } else {
            // Fallback for Auto (System Default) or older Android versions
            Typeface.create(null, weight, false)
        }

        // SAVE TO CACHE: We will never do disk I/O for this specific weight again
        typefaceCache[cacheKey] = typeface

        return typeface
    }

    fun getBlackTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.BLACK, context)
    fun getBoldTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.BOLD, context)
    fun getMediumTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.MEDIUM, context)
    fun getRegularTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.REGULAR, context)
    fun getLightTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.LIGHT, context)
    fun getExtraLightTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.EXTRA_LIGHT, context)

    private fun getTypeFaceForStyle(style: TypefaceStyle, context: Context): Typeface {
        return getTypeFace(
                appFont = AppearancePreferences.getAppFont(),
                style = style.style,
                context = context
        )
    }

    /**
     * List of all typefaces with their code names and red IDs
     */
    val list: ArrayList<TypeFaceModel> = arrayListOf(
            TypeFaceModel(
                    typefaceName = "Auto (System Default)",
                    name = TypeFaceConstants.AUTO,
                    type = "System"),
            TypeFaceModel(
                    typefaceName = "NotoSans",
                    name = TypeFaceConstants.NOTOSANS,
                    type = TYPE_SANS_SERIF,
                    description = "Noto Sans is an unmodulated (“sans serif”) design for texts in the Latin, " +
                            "Cyrillic and Greek scripts, which is also suitable as the complementary choice for " +
                            "other script-specific Noto Sans fonts.",
                    license = "OFL (Open Font License) © Noto Project Authors"
            ),
            TypeFaceModel(
                    typefaceName = "Work Sans",
                    name = TypeFaceConstants.WORK_SANS,
                    type = TYPE_SANS_SERIF,
                    description = "Work Sans is a typeface family based loosely on early Grotesques, " +
                            "such as those by Stephenson Blake, Miller & Richard and Bauerschen Giesserei.",
                    license = "OFL (Open Font License) © Wei Huang"
            ),
            TypeFaceModel(
                    typefaceName = "Inter",
                    name = TypeFaceConstants.INTER,
                    type = TYPE_SANS_SERIF,
                    description = "Inter is a typeface specially designed for computer screens. " +
                            "It features a tall x-height to aid in readability of mixed-case and lower-case text.",
                    license = "OFL (Open Font License) © Rasmus Andersson"
            ),
            TypeFaceModel(
                    typefaceName = "Nunito",
                    name = TypeFaceConstants.NUNITO,
                    type = TYPE_SANS_SERIF,
                    description = "Nunito is a well balanced sans serif typeface superfamily, with " +
                            "rounded terminals. The Nunito project started as a single typeface with " +
                            "two weights (regular and bold).",
                    license = "OFL (Open Font License) © Vernon Adams"
            ),
            TypeFaceModel(
                    typefaceName = "Jost",
                    name = TypeFaceConstants.JOST,
                    type = TYPE_SANS_SERIF,
                    description = "Jost is a revival of the German sans-serif typeface " +
                            "family 'Futura' (1927) which was originally designed by Paul Renner. ",
                    license = "SIL Open Font License, Version 1.1 © Owen Earl"
            ),
            TypeFaceModel(
                    typefaceName = "Exo 2",
                    name = TypeFaceConstants.EXO2,
                    type = TYPE_SANS_SERIF,
                    description = "Exo 2 is a contemporary geometric sans serif typeface. " +
                            "It is the updated version of Exo font family, which was designed by " +
                            "Natanael Gama in 2011. Exo 2 features a more technological feel, " +
                            "a more consistent stroke width and a more uniform design.",
                    license = "OFL (Open Font License) © Natanael Gama"
            ),
            TypeFaceModel(
                    typefaceName = "Sour Gummy",
                    name = TypeFaceConstants.SOURGUMMY,
                    type = TYPE_SANS_SERIF,
                    description = "Sour Gummy Font is a fun and playful typeface that was first created in 2018. " +
                            "The letters are designed to look like they are made of bubbles, with rounded edges and a " +
                            "slightly irregular shape that adds to the playful nature of the font.",
                    license = "OFL (Open Font License) © Stefie Justprince"
            )
    )

    class TypeFaceModel(
            val typefaceName: String,
            val name: String,
            val type: String,
            val description: String? = null,
            val license: String? = null
    )

    private const val TYPE_SANS = "Sans"
    private const val TYPE_SANS_SERIF = "Sans Serif"
    private const val TYPE_SERIF = "Serif"
    private const val TYPE_MONOSPACE = "Monospace"
}