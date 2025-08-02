package app.simple.felicity.decorations.constants

import app.simple.felicity.decorations.constants.TypeFaceConstants.AUTO

@Suppress("SpellCheckingInspection")
object TypeFaceConstants {

    enum class TypefaceStyle(val style: Int) {
        LIGHT(0),
        REGULAR(1),
        MEDIUM(2),
        BOLD(3)
    }

    /**
     * [AUTO] is default as it is appropriate
     * to let the initial font to be of user's
     * choice unless he specifies his preferences
     * in later stages.
     */
    const val AUTO = "auto"
    const val PLUS_JAKARTA = "plus_jakarta"
    const val LATO = "lato"
    const val MULISH = "mulish"
    const val JOST = "jost"
    const val EPILOGUE = "epilogue"
    const val UBUNTU = "ubuntu"
    const val POPPINS = "poppins"
    const val MANROPE = "manrope"
    const val INTER = "inter"
    const val OVERPASS = "overpass"
    const val URBANIST = "urbanist"
    const val NUNITO = "nunito"
    const val OSWALD = "oswald"
    const val ROBOTO = "roboto"
    const val REFORMA = "reforma"
    const val SUBJECTIVITY = "subjectivity"
    const val MOHAVE = "mohave"
    const val YESSICA = "yessica"
    const val AUDREY = "audrey"
    const val JOSEFIN = "josefin_sans"
    const val COMFORTAA = "comfortaa"
    const val CHILLAX = "chillax"
    const val BONNY = "bonny"
    const val SOURCE_CODE_PRO = "source_code_pro"
    const val FREDOKA = "fredoka"
    const val HEEBO = "heebo"
    const val MALI = "mali"
    const val RAJDHANI = "rajdhani"
    const val JETBRAINS_MONO = "jetbrains_mono"
}