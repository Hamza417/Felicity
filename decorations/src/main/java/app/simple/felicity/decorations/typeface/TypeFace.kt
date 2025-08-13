package app.simple.felicity.decorations.typeface

import android.content.Context
import android.graphics.Typeface
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

    fun getTypeFace(appFont: String, style: Int, context: Context): Typeface? {
        var typeface: Typeface? = null

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
            TypeFaceModel("Auto (System Default)", 0, TypeFaceConstants.AUTO),
            TypeFaceModel("NotoSans", R.font.notosans_bold, TypeFaceConstants.NOTOSANS),
            TypeFaceModel("NotoSans Condensed", R.font.notosans_condensed_bold, TypeFaceConstants.NOTOSANS_CONDENSED),
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