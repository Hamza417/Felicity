package app.simple.felicity.repository.engines

import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioWithStat

class OfflineRecommendationEngine {

    fun recommendNext(
            currentTrack: Audio,
            library: List<AudioWithStat>,
            limit: Int = 20,
            recentlyPlayedQueue: Set<Long> = emptySet()
    ): List<AudioWithStat> {
        val now = System.currentTimeMillis()
        val tauMillis = 48.0 * 60 * 60 * 1000 // 48-hour half-life

        return library.asSequence()
            .filter { candidate ->
                val a = candidate.audio
                a.isAvailable &&
                        !a.isAlwaysSkip &&
                        a.id != currentTrack.id &&
                        a.duration >= 30_000 &&
                        !recentlyPlayedQueue.contains(a.id)
            }
            .map { candidate ->
                val sim = computeSimilarity(currentTrack, candidate.audio)
                val affinity = computeAffinity(candidate)
                val decay = computeDecay(candidate.lastPlayed, now, tauMillis)

                val finalScore = sim * affinity * decay
                ScoredCandidate(candidate, finalScore)
            }
            // Add weighted reservoir sampling or top-k sort
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.audioWithStat }
            .toList()
    }

    private fun computeAffinity(stat: AudioWithStat): Double {
        val favBonus = if (stat.audio.isFavorite) 2.5 else 0.0
        val replayBonus = stat.replayCount * 1.8
        val playLog = kotlin.math.ln(1.0 + stat.playCount)
        val skipPenalty = (stat.skipCount.toDouble() / (stat.playCount + 1.0)) * 3.0

        return (1.0 + favBonus + replayBonus + playLog - skipPenalty).coerceAtLeast(0.1)
    }

    private fun computeSimilarity(seed: Audio, cand: Audio): Double {
        var score = 0.0

        // 1. Artist / Album Artist
        if (!seed.artist.isNullOrBlank() && seed.artist.equals(cand.artist, ignoreCase = true)) {
            score += 0.35
        } else if (!seed.albumArtist.isNullOrBlank() && seed.albumArtist.equals(cand.albumArtist, ignoreCase = true)) {
            score += 0.20
        }

        // 2. Genre overlap
        if (!seed.genre.isNullOrBlank() && !cand.genre.isNullOrBlank()) {
            if (seed.genre.equals(cand.genre, ignoreCase = true)) {
                score += 0.25
            }
        }

        // 3. Parent Folder (directory locality)
        val seedFolder = seed.path?.substringBeforeLast('/', "")
        val candFolder = cand.path?.substringBeforeLast('/', "")
        if (!seedFolder.isNullOrEmpty() && seedFolder == candFolder) {
            score += 0.20
        }

        // 4. Era (Year)
        val y1 = seed.year?.toIntOrNull()
        val y2 = cand.year?.toIntOrNull()
        if (y1 != null && y2 != null) {
            val diff = kotlin.math.abs(y1 - y2)
            val eraScore = (1.0 - (diff / 15.0)).coerceIn(0.0, 1.0)
            score += eraScore * 0.10
        }

        // 5. Baseline discovery floor so isolated tracks can still surface
        return (score + 0.10).coerceIn(0.0, 1.0)
    }

    private fun computeDecay(lastPlayed: Long, now: Long, tau: Double): Double {
        if (lastPlayed <= 0L) return 1.0 // Unplayed tracks get zero penalty
        val delta = (now - lastPlayed).coerceAtLeast(0L)
        return (1.0 - kotlin.math.exp(-delta / tau)).coerceIn(0.05, 1.0)
    }

    /**
     * Recommends tracks for a "Discover" section.
     * Focuses on tracks with low play counts that match the user's preferred genres/artists.
     */
    fun recommendDiscover(
            library: List<AudioWithStat>,
            limit: Int = 20
    ): List<AudioWithStat> {
        // Find favorite genres and artists to build a preference profile
        val favoriteGenres = library.filter { it.audio.isFavorite || it.playCount > 5 }
            .mapNotNull { it.audio.genre }
            .groupingBy { it }
            .eachCount()

        val favoriteArtists = library.filter { it.audio.isFavorite || it.playCount > 5 }
            .mapNotNull { it.audio.artist }
            .groupingBy { it }
            .eachCount()

        return library.asSequence()
            .filter { candidate ->
                val a = candidate.audio
                a.isAvailable && !a.isAlwaysSkip && candidate.playCount <= 2
            }
            .map { candidate ->
                var score = 1.0

                // Boost score based on affinity with user's preferred genres and artists
                candidate.audio.genre?.let { genre ->
                    score += (favoriteGenres[genre] ?: 0) * 0.15
                }
                candidate.audio.artist?.let { artist ->
                    score += (favoriteArtists[artist] ?: 0) * 0.20
                }

                // Add randomness for discovery freshness and variety
                val randomBoost = 0.5 + (Math.random() * 1.0) // multiplier between 0.5 and 1.5

                ScoredCandidate(candidate, score * randomBoost)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.audioWithStat }
            .toList()
    }

    /**
     * Generates a completely random pick of tracks, useful for a "Random" section.
     * Lightly favors tracks with some affinity while remaining heavily random.
     */
    fun recommendRandomPicks(
            library: List<AudioWithStat>,
            limit: Int = 20
    ): List<AudioWithStat> {
        return library.asSequence()
            .filter { candidate ->
                candidate.audio.isAvailable && !candidate.audio.isAlwaysSkip
            }
            .map { candidate ->
                // Use a combination of a small baseline affinity and heavy randomness
                val affinity = computeAffinity(candidate)
                val randomScore = Math.random() * 10.0
                ScoredCandidate(candidate, (affinity * 0.2) + randomScore)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.audioWithStat }
            .toList()
    }

    /**
     * Generates a mix of the user's top tracks, balancing favorites with decay.
     * Perfect for an "Overall Mix" or "Daily Mix" section.
     */
    fun recommendOverallMix(
            library: List<AudioWithStat>,
            limit: Int = 20
    ): List<AudioWithStat> {
        val now = System.currentTimeMillis()
        val tauMillis = 72.0 * 60 * 60 * 1000 // 72-hour half-life for mixes to allow more variety

        return library.asSequence()
            .filter { candidate ->
                candidate.audio.isAvailable && !candidate.audio.isAlwaysSkip && candidate.playCount > 0
            }
            .map { candidate ->
                val affinity = computeAffinity(candidate)
                val decay = computeDecay(candidate.lastPlayed, now, tauMillis)

                // Slight random noise to keep the mix fresh daily (0.8 to 1.2)
                val noise = 0.8 + (Math.random() * 0.4)

                val finalScore = affinity * decay * noise
                ScoredCandidate(candidate, finalScore)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.audioWithStat }
            .toList()
    }
}