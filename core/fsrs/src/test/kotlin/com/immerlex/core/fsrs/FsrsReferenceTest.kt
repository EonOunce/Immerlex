package com.immerlex.core.fsrs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compares the Kotlin port against vectors.json produced by py-fsrs (FSRS-6)
 * with: no learning/relearning steps, maximum_interval=3650, fuzzing off, duration=0.
 */
class FsrsReferenceTest {

    private val tolerance = 1e-6

    @Test
    fun matchesPyFsrsReferenceVectors() {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("vectors.json"))
            .bufferedReader().readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val params = root.getValue("params").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
        val desiredRetention = root.getValue("desired_retention").jsonPrimitive.double
        val maximumInterval = root.getValue("maximum_interval").jsonPrimitive.int
        val scheduler = Scheduler(
            parameters = params,
            desiredRetention = desiredRetention,
            learningSteps = emptyList(),
            relearningSteps = emptyList(),
            maximumInterval = maximumInterval,
            enableFuzzing = false,
        )

        val t0 = Instant.parse("2024-01-01T00:00:00Z")
        var checked = 0
        for (case in root.getValue("cases").jsonArray) {
            var card = Card()
            var cursor = t0
            for (review in case.jsonObject.getValue("reviews").jsonArray) {
                val o = review.jsonObject
                val rating = Rating.entries[o.getValue("rating").jsonPrimitive.int - 1]
                val gapDays = o.getValue("gap_days").jsonPrimitive.int
                val at = cursor.plus(gapDays.toLong(), ChronoUnit.DAYS)

                card = scheduler.reviewCard(card, rating, at, 0L).card
                cursor = at

                val after = o.getValue("after").jsonObject
                assertTrue(
                    abs(card.stability!! - after.getValue("s").jsonPrimitive.double) < tolerance,
                    "stability mismatch at review #$checked",
                )
                assertTrue(
                    abs(card.difficulty!! - after.getValue("d").jsonPrimitive.double) < tolerance,
                    "difficulty mismatch at review #$checked",
                )
                val intervalDays = ChronoUnit.DAYS.between(at, card.due)
                assertEquals(
                    after.getValue("interval_days").jsonPrimitive.intOrNull?.toLong(),
                    intervalDays,
                    "interval mismatch at review #$checked",
                )
                checked++
            }
        }
        assertEquals(32, checked, "all reference reviews replayed")
    }
}
