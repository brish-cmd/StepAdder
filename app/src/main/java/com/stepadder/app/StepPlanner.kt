package com.stepadder.app

import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** One step record: [count] steps between [start] and [end]. */
data class StepChunk(val start: Instant, val end: Instant, val count: Long)

/**
 * Splits a step total into minute-by-minute chunks that end at [end], the way a
 * phone pedometer reports walking. Cadence varies slightly minute to minute
 * (95–118 steps/min), which is a normal walking pace.
 */
object StepPlanner {
    const val MIN_CADENCE = 95
    const val MAX_CADENCE = 118
    const val MAX_STEPS_PER_WRITE = 100_000L

    fun plan(total: Long, end: Instant, random: Random = Random.Default): List<StepChunk> {
        require(total in 1..MAX_STEPS_PER_WRITE) { "Steps must be between 1 and $MAX_STEPS_PER_WRITE" }
        val chunks = ArrayList<StepChunk>()
        var remaining = total
        var chunkEnd = end
        while (remaining > 0) {
            val cadence = random.nextInt(MIN_CADENCE, MAX_CADENCE + 1).toLong()
            val count = minOf(remaining, cadence)
            // A partial last minute gets a proportionally shorter duration (min 1 s).
            val seconds = if (count == cadence) 60L else maxOf(1L, (count * 60 + cadence - 1) / cadence)
            val chunkStart = chunkEnd.minus(Duration.ofSeconds(seconds))
            chunks.add(StepChunk(chunkStart, chunkEnd, count))
            remaining -= count
            chunkEnd = chunkStart
        }
        chunks.reverse()
        return chunks
    }

    /** Approximate duration of the walk, for the on-screen preview. */
    fun estimatedStart(total: Long, end: Instant): Instant {
        val avgCadence = (MIN_CADENCE + MAX_CADENCE) / 2.0
        return end.minusSeconds(((total / avgCadence) * 60).toLong())
    }
}
