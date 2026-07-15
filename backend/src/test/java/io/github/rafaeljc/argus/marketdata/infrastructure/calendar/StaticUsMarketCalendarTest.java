package io.github.rafaeljc.argus.marketdata.infrastructure.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StaticUsMarketCalendarTest {

    private final StaticUsMarketCalendar calendar = new StaticUsMarketCalendar();

    @ParameterizedTest
    @CsvSource({
        "2024-01-02",   // Tue after New Year's
        "2024-06-04",   // Tue mid-year
        "2024-11-29",   // Fri after Thanksgiving — Black Friday is a trading day
        "2025-04-01",   // Tue
        "2025-10-15"    // Wed
    })
    void isTradingDay_regularWeekday_returnsTrue(LocalDate date) {
        assertThat(calendar.isTradingDay(date)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "2024-01-06",   // Sat
        "2024-01-07",   // Sun
        "2025-06-14",   // Sat
        "2025-06-15"    // Sun
    })
    void isTradingDay_weekend_returnsFalse(LocalDate date) {
        assertThat(calendar.isTradingDay(date)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        // Fixed-date holidays, no observed shift needed
        "2024-01-01",   // New Year's Day (Mon)
        "2025-01-01",   // New Year's Day (Wed)
        "2024-06-19",   // Juneteenth (Wed)
        "2025-06-19",   // Juneteenth (Thu)
        "2024-07-04",   // Independence Day (Thu)
        "2025-07-04",   // Independence Day (Fri)
        "2024-12-25",   // Christmas (Wed)
        "2025-12-25",   // Christmas (Thu)
        // Nth-weekday-of-month holidays
        "2024-01-15",   // MLK Day (3rd Mon Jan)
        "2025-01-20",   // MLK Day
        "2024-02-19",   // Presidents Day (3rd Mon Feb)
        "2025-02-17",   // Presidents Day
        "2024-05-27",   // Memorial Day (last Mon May)
        "2025-05-26",   // Memorial Day
        "2024-09-02",   // Labor Day (1st Mon Sep)
        "2025-09-01",   // Labor Day
        "2024-11-28",   // Thanksgiving (4th Thu Nov)
        "2025-11-27",   // Thanksgiving
        // Good Friday (Easter minus two days)
        "2024-03-29",   // Easter 2024 = Mar 31
        "2025-04-18"    // Easter 2025 = Apr 20
    })
    void isTradingDay_holiday_returnsFalse(LocalDate date) {
        assertThat(calendar.isTradingDay(date)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "2020-06-19",   // Fri — Juneteenth predates NYSE observance (first observed 2022)
        "2021-06-18"    // Fri — observed shift of Sat Jun 19 2021 also predates observance
    })
    void isTradingDay_juneteenthBeforeFirstObservedYear_returnsTrue(LocalDate date) {
        assertThat(calendar.isTradingDay(date)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "2021-07-05",   // Sun Jul 4 2021 → observed Mon Jul 5
        "2021-12-24",   // Sat Dec 25 2021 → observed Fri Dec 24
        "2021-12-31",   // Sat Jan 1 2022 → observed Fri Dec 31 2021 (cross-year shift)
        "2022-06-20",   // Sun Jun 19 2022 → observed Mon Jun 20
        "2026-07-03",   // Sat Jul 4 2026 → observed Fri Jul 3
        "2027-12-24"    // Sat Dec 25 2027 → observed Fri Dec 24
    })
    void isTradingDay_observedShiftedHoliday_returnsFalse(LocalDate date) {
        assertThat(calendar.isTradingDay(date)).isFalse();
    }

    @Test
    void mostRecentTradingDayOnOrBefore_tradingDay_returnsSameDay() {
        LocalDate tue = LocalDate.of(2024, 6, 4);

        assertThat(calendar.mostRecentTradingDayOnOrBefore(tue)).isEqualTo(tue);
    }

    @Test
    void mostRecentTradingDayOnOrBefore_saturday_returnsPriorFriday() {
        LocalDate sat = LocalDate.of(2024, 6, 8);

        assertThat(calendar.mostRecentTradingDayOnOrBefore(sat)).isEqualTo(LocalDate.of(2024, 6, 7));
    }

    @Test
    void mostRecentTradingDayOnOrBefore_sunday_returnsPriorFriday() {
        LocalDate sun = LocalDate.of(2024, 6, 9);

        assertThat(calendar.mostRecentTradingDayOnOrBefore(sun)).isEqualTo(LocalDate.of(2024, 6, 7));
    }

    @Test
    void mostRecentTradingDayOnOrBefore_holidayMonday_returnsPriorFriday() {
        // Memorial Day 2024 = Mon May 27; prior trading day = Fri May 24.
        assertThat(calendar.mostRecentTradingDayOnOrBefore(LocalDate.of(2024, 5, 27)))
                .isEqualTo(LocalDate.of(2024, 5, 24));
    }

    @Test
    void mostRecentTradingDayOnOrBefore_blackFriday_returnsSameDay() {
        LocalDate blackFriday = LocalDate.of(2024, 11, 29);

        assertThat(calendar.mostRecentTradingDayOnOrBefore(blackFriday)).isEqualTo(blackFriday);
    }

    @Test
    void mostRecentTradingDayOnOrBefore_saturdayAfterObservedFridayHoliday_returnsThursday() {
        // Jul 4 2026 = Sat → observed Fri Jul 3 non-trading. Walking back from Sat Jul 4 lands on Thu Jul 2.
        assertThat(calendar.mostRecentTradingDayOnOrBefore(LocalDate.of(2026, 7, 4)))
                .isEqualTo(LocalDate.of(2026, 7, 2));
    }
}
