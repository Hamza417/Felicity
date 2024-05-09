package app.simple.felicity.constants

import app.simple.felicity.preferences.AccessibilityPreferences

@Suppress("MemberVisibilityCanBePrivate")
object Colors {

    const val PASTEL = 0
    const val RETRO = 1
    const val COFFEE = 2
    const val COLD = 3

    private val pastel: ArrayList<Int> by lazy {
        arrayListOf(
                0xFFA7727D.toInt(),
                0xFF6096B4.toInt(),
                0xFFD0B8A8.toInt(),
                0xFF8B7E74.toInt(),
                0xFF7895B2.toInt(),
                0xFF967E76.toInt(),
                0xFF7D9D9C.toInt(),
                0xFF748DA6.toInt(),
                0xFF576F72.toInt(),
                0xFF68A7AD.toInt(),
                0xFF9A86A4.toInt(),
                0xFF655D8A.toInt(),
                0xFF7C99AC.toInt(),
                0xFF8E806A.toInt(),
                0xFF96C7C1.toInt(),
                0xFF87AAAA.toInt(),
                0xFF9D9D9D.toInt(),
                0xFF89B5AF.toInt(),
                0xFFF29191.toInt(),
                0xFFF2C57C.toInt(),
                0xFFA0937D.toInt(),
                0xFF798777.toInt(),
                0xFFB67162.toInt(),
                0xFF865858.toInt(),
                0xFF435560.toInt(),
                0xFF557174.toInt(),
                0xFF7C9473.toInt(),
                0xFF6155A6.toInt(),
        )
    }

    private val retro: ArrayList<Int> by lazy {
        arrayListOf(
                0xFFB31312.toInt(),
                0xFF2B2A4C.toInt(),
                0xFF5C8984.toInt(),
                0xFF545B77.toInt(),
                0xFF068DA9.toInt(),
                0xFFA459D1.toInt(),
                0xFF1B9C85.toInt(),
                0xFFB71375.toInt(),
                0xFF3C486B.toInt(),
                0xFF245953.toInt(),
                0xFF408E91.toInt(),
                0xFF4E6E81.toInt(),
                0xFFEA5455.toInt(),
                0xFFBAD7E9.toInt(),
                0xFF609EA2.toInt(),
                0xFFEB455F.toInt(),
                0xFF434242.toInt(),
                0xFFE26868.toInt(),
                0xFFADDDD0.toInt(),
                0xFFFFDE00.toInt(),
                0xFF7A4495.toInt(),
                0xFF1A4D2E.toInt(),
                0xFF4B5D67.toInt(),
                0xFF413F42.toInt(),
                0xFF6A67CE.toInt(),
                0xFF8D8DAA.toInt(),
                0xFF006E7F.toInt(),
                0xFF874356.toInt(),
        )
    }

    private val cold: ArrayList<Int> by lazy {
        arrayListOf(
                0xFF27374D.toInt(),
                0xFF526D82.toInt(),
                0xFF11009E.toInt(),
                0xFF57C5B6.toInt(),
                0xFF19A7CE.toInt(),
                0xFF635985.toInt(),
                0xFF3E54AC.toInt(),
                0xFF3C84AB.toInt(),
                0xFF6096B4.toInt(),
                0xFF497174.toInt(),
                0xFF557153.toInt(),
                0xFF6D9886.toInt(),
                0xFF256D85.toInt(),
                0xFF395B64.toInt(),
                0xFF495C83.toInt(),
                0xFF635666.toInt(),
                0xFF3BACB6.toInt(),
                0xFF069A8E.toInt(),
                0xFF39AEA9.toInt(),
                0xFF557B83.toInt(),
                0xFF1C658C.toInt(),
                0xFFD3DEDC.toInt(),
                0xFF6998AB.toInt(),
                0xFF009DAE.toInt(),
                0xFF678983.toInt(),
                0xFFE6DDC4.toInt(),
                0xFF345B63.toInt(),
                0xFFD4ECDD.toInt(),
        )
    }

    private val coffee: ArrayList<Int> by lazy {
        arrayListOf(
                0xFF884A39.toInt(),
                0xFFA9907E.toInt(),
                0xFFABC4AA.toInt(),
                0xFFFFC26F.toInt(),
                0xFF8D7B68.toInt(),
                0xFFC8B6A6.toInt(),
                0xFFA7727D.toInt(),
                0xFFA75D5D.toInt(),
                0xFF343434.toInt(),
                0xFF8B7E74.toInt(),
                0xFF65647C.toInt(),
                0xFFD45D79.toInt(),
                0xFF704F4F.toInt(),
                0xFFD1512D.toInt(),
                0xFF3F4E4F.toInt(),
                0xFFA27B5C.toInt(),
                0xFFDCD7C9.toInt(),
                0xFF87805E.toInt(),
                0xFFAC7D88.toInt(),
                0xFF826F66.toInt(),
                0xFF3A3845.toInt(),
                0xFF54BAB9.toInt(),
                0xFF8E806A.toInt(),
                0xFFDBD0C0.toInt(),
                0xFF4B6587.toInt(),
                0xFFC8C6C6.toInt(),
                0xFF7C7575.toInt(),
                0xFF8E8B82.toInt(),
        )
    }

    fun getPastelColor(): ArrayList<Int> {
        return pastel
    }

    fun getRetroColor(): ArrayList<Int> {
        return retro
    }

    fun getColdColor(): ArrayList<Int> {
        return cold
    }

    fun getCoffeeColor(): ArrayList<Int> {
        return coffee
    }

    fun getColors(): ArrayList<Int> {
        return when (AccessibilityPreferences.getColorfulIconsPalette()) {
            COLD -> getColdColor()
            RETRO -> getRetroColor()
            COFFEE -> getCoffeeColor()
            else -> getPastelColor()
        }
    }

    fun getColorsTwice(): ArrayList<Int> {
        val colors = getColors()
        colors.addAll(colors)
        return colors
    }
}
