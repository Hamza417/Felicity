package app.simple.felicity.repository.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ArtistTagUtils]. All calls pass explicit preference/whitelist values so the
 * logic stays pure and runs on the plain JVM without Android.
 *
 * The empty whitelist in most cases is deliberate: it proves the conservative default separator
 * set alone keeps legitimate names whole, without leaning on the whitelist. The whitelist is only
 * exercised in [customSeparatorsRespectWhitelist] as a second line of defence.
 */
class ArtistTagUtilsTest {

    private fun split(
            field: String?,
            separators: String = "",
            whitelist: Set<String> = emptySet()
    ): List<String> = ArtistTagUtils.splitArtists(
            field = field,
            splittingEnabled = true,
            customSeparators = separators,
            whitelist = whitelist
    )

    private fun matches(
            field: String?,
            name: String,
            whitelist: Set<String> = emptySet()
    ): Boolean = ArtistTagUtils.artistFieldMatchesName(
            artistField = field,
            name = name,
            splittingEnabled = true,
            customSeparators = "",
            whitelist = whitelist
    )

    // region False positives: names that must NEVER be split by the conservative default

    @Test
    fun acdcStaysWhole() {
        assertEquals(listOf("AC/DC"), split("AC/DC"))
    }

    @Test
    fun ampersandNamesStayWhole() {
        assertEquals(listOf("Simon & Garfunkel"), split("Simon & Garfunkel"))
    }

    @Test
    fun commaAndAmpersandNamesStayWhole() {
        assertEquals(listOf("Earth, Wind & Fire"), split("Earth, Wind & Fire"))
        assertEquals(listOf("Crosby, Stills, Nash & Young"), split("Crosby, Stills, Nash & Young"))
        assertEquals(listOf("Blood, Sweat & Tears"), split("Blood, Sweat & Tears"))
    }

    @Test
    fun commaNamesStayWhole() {
        assertEquals(listOf("Tyler, The Creator"), split("Tyler, The Creator"))
    }

    @Test
    fun plusNamesStayWhole() {
        assertEquals(listOf("Florence + The Machine"), split("Florence + The Machine"))
    }

    @Test
    fun slashNamesStayWhole() {
        assertEquals(listOf("Lennon/McCartney"), split("Lennon/McCartney"))
    }

    @Test
    fun withAndTrailingXStayWhole() {
        assertEquals(listOf("Sleeping with Sirens"), split("Sleeping with Sirens"))
        assertEquals(listOf("Lil Nas X"), split("Lil Nas X"))
    }

    // endregion

    // region True collaborations: high-confidence markers must still split

    @Test
    fun featSplits() {
        assertEquals(listOf("AKON", "WYCLEF"), split("AKON feat. WYCLEF"))
        assertEquals(listOf("Eminem", "Rihanna"), split("Eminem ft. Rihanna"))
        assertEquals(listOf("Calvin Harris", "Dua Lipa"), split("Calvin Harris featuring Dua Lipa"))
    }

    @Test
    fun unambiguousPunctuationSplits() {
        assertEquals(listOf("Artist A", "Artist B"), split("Artist A; Artist B"))
        assertEquals(listOf("Artist A", "Artist B"), split("Artist A;Artist B"))
    }

    @Test
    fun vsAndXSplit() {
        assertEquals(listOf("DJ A", "DJ B"), split("DJ A vs. DJ B"))
        assertEquals(listOf("Jay-Z", "Linkin Park"), split("Jay-Z x Linkin Park"))
    }

    // endregion

    // region Edge cases

    @Test
    fun nullAndBlankYieldEmpty() {
        assertTrue(split(null).isEmpty())
        assertTrue(split("").isEmpty())
        assertTrue(split("   ").isEmpty())
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(listOf("Adele"), split("  Adele  "))
    }

    @Test
    fun duplicatesAreCollapsedPreservingOrder() {
        assertEquals(listOf("A"), split("A feat. A"))
        assertEquals(listOf("B", "A"), split("B; A; B"))
    }

    @Test
    fun splittingDisabledKeepsFieldWhole() {
        val result = ArtistTagUtils.splitArtists(
                field = "AKON feat. WYCLEF",
                splittingEnabled = false,
                customSeparators = "",
                whitelist = emptySet()
        )
        assertEquals(listOf("AKON feat. WYCLEF"), result)
    }

    // endregion

    // region Custom separators + whitelist (second line of defence)

    @Test
    fun customSeparatorsReplaceTheDefaultSet() {
        // User opts into slash-splitting; a non-whitelisted name splits as requested.
        assertEquals(listOf("Foo", "Bar"), split("Foo/Bar", separators = "/"))
    }

    @Test
    fun customSeparatorsRespectWhitelist() {
        // Even with slash-splitting enabled, a whitelisted name is protected.
        assertEquals(
                listOf("AC/DC"),
                split("AC/DC", separators = "/", whitelist = setOf("AC/DC"))
        )
    }

    // endregion

    // region artistFieldMatchesName

    @Test
    fun singleWordPieceDoesNotMatchNameFragment() {
        assertFalse(matches("AC/DC", "AC"))
        assertFalse(matches("AC/DC", "DC"))
        assertFalse(matches("LISA GERRARD", "LISA"))
    }

    @Test
    fun fullNameMatches() {
        assertTrue(matches("AC/DC", "AC/DC"))
        assertTrue(matches("Daft Punk", "Daft Punk"))
    }

    @Test
    fun creditedCollaboratorMatches() {
        assertTrue(matches("AKON feat. WYCLEF", "AKON"))
        assertTrue(matches("AKON feat. WYCLEF", "WYCLEF"))
    }

    @Test
    fun nullFieldOrBlankNameDoesNotMatch() {
        assertFalse(matches(null, "AC"))
        assertFalse(matches("AC/DC", ""))
    }

    // endregion
}
 