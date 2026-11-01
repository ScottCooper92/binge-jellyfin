package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinTaskTriggersTest {
    private fun triggers(json: String) = (Json.parseToJsonElement(json) as JsonArray).toTriggers()

    @Test
    fun `each kind of trigger is read, with its time or interval and its limit`() {
        val read =
            triggers(
                """[{"Type":"DailyTrigger","TimeOfDayTicks":108000000000,"MaxRuntimeTicks":72000000000},
                {"Type":"WeeklyTrigger","DayOfWeek":"Monday","TimeOfDayTicks":36000000000},
                {"Type":"IntervalTrigger","IntervalTicks":216000000000},
                {"Type":"StartupTrigger"}]""",
            )
        assertEquals(listOf(TriggerKind.Daily, TriggerKind.Weekly, TriggerKind.Interval, TriggerKind.Startup), read.map { it.kind })
        assertEquals(3 * TICKS_PER_HOUR, read[0].timeOfDayTicks)
        assertEquals(2 * TICKS_PER_HOUR, read[0].maxRuntimeTicks)
        assertEquals("Monday", read[1].dayOfWeek)
        assertEquals(6 * TICKS_PER_HOUR, read[2].intervalTicks)
        assertNull(read[3].maxRuntimeTicks)
    }

    @Test
    fun `a trigger this app doesn't know is kept as the server sent it`() {
        val read = triggers("""[{"Type":"SystemEventTrigger","FutureField":7},{"NoType":1}]""")
        assertEquals(1, read.size)
        assertNull(read.single().kind)
        assertEquals("""{"Type":"SystemEventTrigger","FutureField":7}""", read.single().toJson().toString())
    }

    @Test
    fun `a new trigger sends only the fields its kind has`() {
        assertEquals("""{"Type":"DailyTrigger","TimeOfDayTicks":108000000000}""", TaskTrigger.daily(3 * TICKS_PER_HOUR).toJson().toString())
        assertEquals(
            """{"Type":"WeeklyTrigger","TimeOfDayTicks":36000000000,"DayOfWeek":"Friday","MaxRuntimeTicks":72000000000}""",
            TaskTrigger.weekly("Friday", TICKS_PER_HOUR, 2 * TICKS_PER_HOUR).toJson().toString(),
        )
        assertEquals(
            """{"Type":"IntervalTrigger","IntervalTicks":27000000000}""",
            TaskTrigger.interval(45 * TICKS_PER_MINUTE).toJson().toString(),
        )
        assertEquals("""{"Type":"StartupTrigger"}""", TaskTrigger.startup().toJson().toString())
        val sent: JsonObject = TaskTrigger.startup().toJson()
        assertFalse("TimeOfDayTicks" in sent)
        assertTrue(sent.size == 1)
    }

    @Test
    fun `a task's triggers come with it, and a task with none has an empty list`() {
        val task = (Json.parseToJsonElement("""[{"Id":"t","Key":"K","Name":"N","State":"Idle"}]""") as JsonArray).toTasks().single()
        assertEquals(emptyList<TaskTrigger>(), task.triggers)
    }
}
