package app.simple.felicity.decorations.typeface

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.constants.TypeFaceConstants
import app.simple.felicity.preferences.AppearancePreferences

object TypeFace {

    private val notoSansMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.notosans_extralight,
                TypefaceStyle.LIGHT.style to R.font.notosans_light,
                TypefaceStyle.REGULAR.style to R.font.notosans_regular,
                TypefaceStyle.MEDIUM.style to R.font.notosans_medium,
                TypefaceStyle.BOLD.style to R.font.notosans_bold,
                TypefaceStyle.BLACK.style to R.font.notosans_black
        )
    }

    private val notoSansCondensedMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.notosans_condensed_extralight,
                TypefaceStyle.LIGHT.style to R.font.notosans_condensed_light,
                TypefaceStyle.REGULAR.style to R.font.notosans_condensed_regular,
                TypefaceStyle.MEDIUM.style to R.font.notosans_condensed_medium,
                TypefaceStyle.BOLD.style to R.font.notosans_condensed_bold,
                TypefaceStyle.BLACK.style to R.font.notosans_condensed_black
        )
    }

    private val workSansMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.work_sans_extralight,
                TypefaceStyle.LIGHT.style to R.font.work_sans_light,
                TypefaceStyle.REGULAR.style to R.font.work_sans_regular,
                TypefaceStyle.MEDIUM.style to R.font.work_sans_medium,
                TypefaceStyle.BOLD.style to R.font.work_sans_bold,
                TypefaceStyle.BLACK.style to R.font.work_sans_black
        )
    }

    private val interMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.inter_extralight,
                TypefaceStyle.LIGHT.style to R.font.inter_light,
                TypefaceStyle.REGULAR.style to R.font.inter_regular,
                TypefaceStyle.MEDIUM.style to R.font.inter_medium,
                TypefaceStyle.BOLD.style to R.font.inter_bold,
                TypefaceStyle.BLACK.style to R.font.inter_black
        )
    }

    private val barlowMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.barlow_extralight,
                TypefaceStyle.LIGHT.style to R.font.barlow_light,
                TypefaceStyle.REGULAR.style to R.font.barlow_regular,
                TypefaceStyle.MEDIUM.style to R.font.barlow_medium,
                TypefaceStyle.BOLD.style to R.font.barlow_bold,
                TypefaceStyle.BLACK.style to R.font.barlow_black
        )
    }

    private val nunitoMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.nunito_extralight,
                TypefaceStyle.LIGHT.style to R.font.nunito_light,
                TypefaceStyle.REGULAR.style to R.font.nunito_regular,
                TypefaceStyle.MEDIUM.style to R.font.nunito_medium,
                TypefaceStyle.BOLD.style to R.font.nunito_bold,
                TypefaceStyle.BLACK.style to R.font.nunito_black
        )
    }

    private val spectralMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.spectral_extralight,
                TypefaceStyle.LIGHT.style to R.font.spectral_light,
                TypefaceStyle.REGULAR.style to R.font.spectral_regular,
                TypefaceStyle.MEDIUM.style to R.font.spectral_medium,
                TypefaceStyle.BOLD.style to R.font.spectral_bold,
                TypefaceStyle.BLACK.style to R.font.spectral_extrabold
        )
    }

    private val jostMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.jost_extralight,
                TypefaceStyle.LIGHT.style to R.font.jost_light,
                TypefaceStyle.REGULAR.style to R.font.jost_regular,
                TypefaceStyle.MEDIUM.style to R.font.jost_medium,
                TypefaceStyle.BOLD.style to R.font.jost_bold,
                TypefaceStyle.BLACK.style to R.font.jost_black
        )
    }

    private val exo2Map by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.exo2_extralight,
                TypefaceStyle.LIGHT.style to R.font.exo2_light,
                TypefaceStyle.REGULAR.style to R.font.exo2_regular,
                TypefaceStyle.MEDIUM.style to R.font.exo2_medium,
                TypefaceStyle.BOLD.style to R.font.exo2_bold,
                TypefaceStyle.BLACK.style to R.font.exo2_black
        )
    }

    private val sourGummyMap by lazy {
        mapOf(
                TypefaceStyle.EXTRA_LIGHT.style to R.font.sourgummy_extralight,
                TypefaceStyle.LIGHT.style to R.font.sourgummy_light,
                TypefaceStyle.REGULAR.style to R.font.sourgummy_regular,
                TypefaceStyle.MEDIUM.style to R.font.sourgummy_medium,
                TypefaceStyle.BOLD.style to R.font.sourgummy_bold,
                TypefaceStyle.BLACK.style to R.font.sourgummy_black
        )
    }

    fun getTypeFace(appFont: String, style: Int, context: Context): Typeface? {
        var typeface: Typeface? = null
        Log.d("TypeFace", "getTypeFace: appFont=$appFont, style=$style")
        when (appFont) {
            TypeFaceConstants.NOTOSANS -> {
                notoSansMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.NOTOSANS_CONDENSED -> {
                notoSansCondensedMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.WORK_SANS -> {
                workSansMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.INTER -> {
                interMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.BARLOW -> {
                barlowMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.NUNITO -> {
                nunitoMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.SPECTRAL -> {
                spectralMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.JOST -> {
                jostMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.EXO2 -> {
                exo2Map[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            TypeFaceConstants.SOURGUMMY -> {
                sourGummyMap[style]?.let {
                    typeface = ResourcesCompat.getFont(context, it)
                }
            }
            else -> {
                typeface = when (style) {
                    TypefaceStyle.REGULAR.style, TypefaceStyle.LIGHT.style, TypefaceStyle.EXTRA_LIGHT.style -> {
                        Typeface.DEFAULT
                    }
                    TypefaceStyle.MEDIUM.style, TypefaceStyle.BOLD.style, TypefaceStyle.BLACK.style -> {
                        Typeface.DEFAULT_BOLD
                    }
                    else -> null
                }
            }
        }

        return typeface
    }

    fun getBlackTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.BLACK, context)
    fun getBoldTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.BOLD, context)
    fun getMediumTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.MEDIUM, context)
    fun getRegularTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.REGULAR, context)
    fun getLightTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.LIGHT, context)
    fun getExtraLightTypeFace(context: Context) = getTypeFaceForStyle(TypefaceStyle.EXTRA_LIGHT, context)

    private fun getTypeFaceForStyle(style: TypefaceStyle, context: Context): Typeface? {
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
                    typefaceName = "NotoSans Condensed",
                    name = TypeFaceConstants.NOTOSANS_CONDENSED,
                    type = TYPE_SANS_SERIF,
                    description = "A condensed version of NotoSans, ideal for compact layouts.",
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
                    typefaceName = "Barlow",
                    name = TypeFaceConstants.BARLOW,
                    type = TYPE_SANS_SERIF,
                    description = "Barlow is a slightly rounded, low-contrast, grotesk type family. " +
                            "Drawing from the visual style of the California public, Barlow shares qualities " +
                            "with the state's car plates, highway signs, busses, and trains.",
                    license = "OFL (Open Font License) © Jeremy Tribby"
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
                    typefaceName = "Spectral",
                    name = TypeFaceConstants.SPECTRAL,
                    type = TYPE_SERIF,
                    description = "Spectral is a versatile, contemporary serif typeface for text " +
                            "and display use. It is a high-contrast serif with a distinctive character, " +
                            "informed by the Scotch Modern genre.",
                    license = "OFL (Open Font License) © Production Type"
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
            /**
             * Proper marketed name of the typeface
             */
            val typefaceName: String,

            /**
             * Name of the typeface that is used by the
             * preference manager of the app to identify
             * which typeface is used similar to [typefaceName]
             * except it is all lowercase
             */
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