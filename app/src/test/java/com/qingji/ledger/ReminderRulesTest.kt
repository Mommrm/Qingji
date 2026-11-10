package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderRulesTest {
    private val mondayMorning = ZonedDateTime.of(2026, 11, 9, 7, 0, 0, 0, ZoneId.of("Asia/Shanghai"))

    @Test fun presetsCoverTheExpectedWeekdays() {
        assertEquals((1..7).toSet(), REMINDER_PRESETS.first { it.first == "每天" }.second)
        assertEquals(setOf(1, 2, 3, 4, 5), REMINDER_PRESETS.first { it.first == "工作日" }.second)
        assertEquals(setOf(6, 7), REMINDER_PRESETS.first { it.first == "周末" }.second)
        assertEquals(3, REMINDER_PRESETS.size)
    }

    @Test fun repeatSummaryDescribesPresetsAndCustomDays() {
        assertEquals("每天", repeatSummary((1..7).toSet()))
        assertEquals("工作日", repeatSummary((1..5).toSet()))
        assertEquals("周末", repeatSummary(setOf(6, 7)))
        assertEquals("每周三", repeatSummary(setOf(3)))
        assertEquals("每周一、三、五", repeatSummary(setOf(5, 1, 3)))
        assertEquals("未选择提醒日", repeatSummary(emptySet()))
    }

    @Test fun normalizeReminderDaysNeverProducesAnEmptySchedule() {
        assertEquals(REMINDER_ALL_DAYS, normalizeReminderDays(emptySet()))
        assertEquals(setOf(2, 4), normalizeReminderDays(setOf(2, 4, 99, 0)))
    }

    @Test fun nextReminderTextShowsDateAndTimeAndRespectsSwitch() {
        val s = Settings(enabled = true, time = "08:30", days = setOf(1, 3, 5))
        val text = nextReminderText(s, mondayMorning)
        assertTrue(text.contains("11月9日")); assertTrue(text.contains("08:30"))
        assertEquals("暂无下次提醒", nextReminderText(s.copy(enabled = false), mondayMorning))
        assertEquals("暂无下次提醒", nextReminderText(s.copy(days = emptySet()), mondayMorning))
    }

    @Test fun singleWeekdaySkipsForwardToThatDay() {
        val friday = Settings(enabled = true, time = "21:00", days = setOf(5))
        val mondayNight = ZonedDateTime.of(2026, 11, 9, 22, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        assertEquals(LocalDate.of(2026, 11, 13), nextReminder(friday, mondayNight)?.toLocalDate())
    }

    @Test fun workdaysSkipWeekend() {
        val workdays = Settings(enabled = true, time = "09:00", days = (1..5).toSet())
        val fridayNight = ZonedDateTime.of(2026, 11, 13, 20, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        assertEquals(LocalDate.of(2026, 11, 16), nextReminder(workdays, fridayNight)?.toLocalDate())
    }
}
