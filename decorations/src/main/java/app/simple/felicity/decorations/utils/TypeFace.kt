package app.simple.felicity.decorations.utils

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.simple.felicity.core.constants.TypeFaceConstants
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.R

object TypeFace {

    fun getTypeFace(appFont: String, style: Int, context: Context): Typeface? {
        var typeface: Typeface? = null

        when (appFont) {
            TypeFaceConstants.AUTO -> {
                when (style) {
                    0, 1 -> {
                        typeface = Typeface.DEFAULT
                    }

                    2, 3 -> {
                        typeface = Typeface.DEFAULT_BOLD
                    }
                }
            }

            TypeFaceConstants.LATO -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.lato_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.lato_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.lato_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.lato_bold)
                    }
                }
            }

            TypeFaceConstants.PLUS_JAKARTA -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_bold)
                    }
                }
            }

            TypeFaceConstants.MULISH -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mulish_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mulish_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mulish_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mulish_bold)
                    }
                }
            }

            TypeFaceConstants.JOST -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jost_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jost_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jost_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jost_bold)
                    }
                }
            }

            TypeFaceConstants.EPILOGUE -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.epilogue_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.epilogue_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.epilogue_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.epilogue_bold)
                    }
                }
            }

            TypeFaceConstants.UBUNTU -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.ubuntu_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.ubuntu_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.ubuntu_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.ubuntu_bold)
                    }
                }
            }

            TypeFaceConstants.POPPINS -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.poppins_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.poppins_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.poppins_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.poppins_bold)
                    }
                }
            }

            TypeFaceConstants.MANROPE -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.manrope_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.manrope_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.manrope_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.manrope_bold)
                    }
                }
            }

            TypeFaceConstants.INTER -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.inter_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.inter_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.inter_bold)
                    }
                }
            }

            TypeFaceConstants.OVERPASS -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.overpass_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.overpass_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.overpass_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.overpass_bold)
                    }
                }
            }

            TypeFaceConstants.URBANIST -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.urbanist_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.urbanist_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.urbanist_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.urbanist_bold)
                    }
                }
            }

            TypeFaceConstants.NUNITO -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.nunito_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.nunito_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.nunito_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.nunito_bold)
                    }
                }
            }

            TypeFaceConstants.OSWALD -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.oswald_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.oswald_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.oswald_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.oswald_bold)
                    }
                }
            }

            TypeFaceConstants.ROBOTO -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.roboto_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.roboto_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.roboto_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.roboto_bold)
                    }
                }
            }

            TypeFaceConstants.REFORMA -> {
                when (style) {
                    0,
                    1,
                        -> {
                        typeface = ResourcesCompat.getFont(context, R.font.reforma_blanca)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.reforma_gris)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.reforma_negra)
                    }
                }
            }

            TypeFaceConstants.SUBJECTIVITY -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.subjectivity_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.subjectivity_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.subjectivity_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.subjectivity_bold)
                    }
                }
            }

            TypeFaceConstants.MOHAVE -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mohave_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mohave_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mohave_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mohave_bold)
                    }
                }
            }

            TypeFaceConstants.YESSICA -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.yessica_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.yessica_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.yessica_regular)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.yessica_bold)
                    }
                }
            }

            TypeFaceConstants.AUDREY -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.audrey_regular)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.audrey_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.audrey_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.audrey_bold)
                    }
                }
            }

            TypeFaceConstants.JOSEFIN -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.josefin_sans_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.josefin_sans_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.josefin_sans_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.josefin_sans_bold)
                    }
                }
            }

            TypeFaceConstants.COMFORTAA -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.comfortaa_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.comfortaa_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.comfortaa_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.comfortaa_bold)
                    }
                }
            }

            TypeFaceConstants.CHILLAX -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.chillax_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.chillax_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.chillax_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.chillax_semi_bold)
                    }
                }
            }

            TypeFaceConstants.BONNY -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.bonny_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.bonny_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.bonny_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.bonny_bold)
                    }
                }
            }

            TypeFaceConstants.SOURCE_CODE_PRO -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.source_code_pro_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.source_code_pro_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.source_code_pro_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.source_code_pro_bold)
                    }
                }
            }

            TypeFaceConstants.FREDOKA -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.fredoka_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.fredoka_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.fredoka_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.fredoka_bold)
                    }
                }
            }

            TypeFaceConstants.HEEBO -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.heebo_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.heebo_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.heebo_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.heebo_bold)
                    }
                }
            }

            TypeFaceConstants.MALI -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mali_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mali_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mali_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.mali_bold)
                    }
                }
            }

            TypeFaceConstants.RAJDHANI -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.rajdhani_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.rajdhani_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.rajdhani_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.rajdhani_bold)
                    }
                }
            }

            TypeFaceConstants.JETBRAINS_MONO -> {
                when (style) {
                    0 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_light)
                    }

                    1 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular)
                    }

                    2 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)
                    }

                    3 -> {
                        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold)
                    }
                }
            }

            else -> {
                when (style) {
                    0, 1 -> {
                        typeface = Typeface.DEFAULT
                    }

                    2, 3 -> {
                        typeface = Typeface.DEFAULT_BOLD
                    }
                }
            }
        }

        return typeface
    }

    fun getBoldTypeFace(context: Context): Typeface? {
        return getTypeFace(AppearancePreferences.getAppFont(), TypeFaceConstants.TypefaceStyle.BOLD.style, context)
    }

    fun getRegularTypeFace(context: Context): Typeface? {
        return getTypeFace(AppearancePreferences.getAppFont(), TypeFaceConstants.TypefaceStyle.REGULAR.style, context)
    }

    fun getMediumTypeFace(context: Context): Typeface? {
        return getTypeFace(AppearancePreferences.getAppFont(), TypeFaceConstants.TypefaceStyle.MEDIUM.style, context)
    }

    fun getLightTypeFace(context: Context): Typeface? {
        return getTypeFace(AppearancePreferences.getAppFont(), TypeFaceConstants.TypefaceStyle.LIGHT.style, context)
    }

    /**
     * List of all typefaces with their code names and red IDs
     */
    val list: ArrayList<TypeFaceModel> = arrayListOf(
            TypeFaceModel("Auto (System Default)", 0, TypeFaceConstants.AUTO),
            TypeFaceModel("Lato", R.font.lato_bold, TypeFaceConstants.LATO),
            TypeFaceModel("Plus Jakarta Sans", R.font.plus_jakarta_bold, TypeFaceConstants.PLUS_JAKARTA),
            TypeFaceModel("Mulish", R.font.mulish_bold, TypeFaceConstants.MULISH),
            TypeFaceModel("Jost", R.font.jost_bold, TypeFaceConstants.JOST),
            TypeFaceModel("Epilogue", R.font.epilogue_bold, TypeFaceConstants.EPILOGUE),
            TypeFaceModel("Ubuntu", R.font.ubuntu_bold, TypeFaceConstants.UBUNTU),
            TypeFaceModel("Poppins", R.font.poppins_bold, TypeFaceConstants.POPPINS),
            TypeFaceModel("Manrope", R.font.manrope_bold, TypeFaceConstants.MANROPE),
            TypeFaceModel("Inter", R.font.inter_bold, TypeFaceConstants.INTER),
            TypeFaceModel("Overpass", R.font.overpass_bold, TypeFaceConstants.OVERPASS),
            TypeFaceModel("Urbanist", R.font.urbanist_bold, TypeFaceConstants.URBANIST),
            TypeFaceModel("Nunito", R.font.nunito_bold, TypeFaceConstants.NUNITO),
            TypeFaceModel("Oswald", R.font.oswald_bold, TypeFaceConstants.OSWALD),
            TypeFaceModel("Roboto", R.font.roboto_bold, TypeFaceConstants.ROBOTO),
            TypeFaceModel("Reforma", R.font.reforma_negra, TypeFaceConstants.REFORMA),
            TypeFaceModel("Subjectivity", R.font.subjectivity_bold, TypeFaceConstants.SUBJECTIVITY),
            TypeFaceModel("Mohave", R.font.mohave_bold, TypeFaceConstants.MOHAVE),
            TypeFaceModel("Yessica", R.font.yessica_bold, TypeFaceConstants.YESSICA),
            TypeFaceModel("Audrey", R.font.audrey_bold, TypeFaceConstants.AUDREY),
            TypeFaceModel("Josefin Sans", R.font.josefin_sans_bold, TypeFaceConstants.JOSEFIN),
            TypeFaceModel("Comfortaa", R.font.comfortaa_bold, TypeFaceConstants.COMFORTAA),
            TypeFaceModel("Chillax", R.font.chillax_semi_bold, TypeFaceConstants.CHILLAX),
            TypeFaceModel("Bonny", R.font.bonny_bold, TypeFaceConstants.BONNY),
            TypeFaceModel("Source Code Pro", R.font.source_code_pro_bold, TypeFaceConstants.SOURCE_CODE_PRO),
            TypeFaceModel("Fredoka", R.font.fredoka_bold, TypeFaceConstants.FREDOKA),
            TypeFaceModel("Heebo", R.font.heebo_bold, TypeFaceConstants.HEEBO),
            TypeFaceModel("Mali", R.font.mali_bold, TypeFaceConstants.MALI),
            TypeFaceModel("Rajdhani", R.font.rajdhani_bold, TypeFaceConstants.RAJDHANI),
            TypeFaceModel("JetBrains Mono", R.font.jetbrains_mono_bold, TypeFaceConstants.JETBRAINS_MONO)
    )

    class TypeFaceModel(
            /**
             * Proper name of the typeface such as [TypeFaceConstants.ROBOTO]
             */
            val typefaceName: String,

            /**
             * Resource ID for the type face that is used to set typeface
             */
            val typeFaceResId: Int,

            /**
             * Name of the typeface that is used by the
             * preference manager of the app to identify
             * which typeface is used similar to [typefaceName]
             * except it is all lowercase
             */
            val name: String,
    )
}