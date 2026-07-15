package io.github.rafaeljc.argus.marketdata.infrastructure.calendar;

import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// In-memory US-market calendar for local/test profiles. Weekends + US federal holidays with NYSE observed-shift rules
// (Sat → prior Fri, Sun → next Mon). No half-day handling. Phase 10 replaces this with a vendor-driven adapter under
// the prod profile.
@Component
@Profile({"local", "test"})
class StaticUsMarketCalendar implements MarketCalendar {

    @Override
    public boolean isTradingDay(LocalDate date) {
        return !isWeekend(date) && !isHoliday(date);
    }

    @Override
    public LocalDate mostRecentTradingDayOnOrBefore(LocalDate date) {
        LocalDate cursor = date;
        while (!isTradingDay(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    // NYSE first observed Juneteenth in 2022 (federal holiday enacted Jun 2021, too late for that year's session).
    private static final int JUNETEENTH_FIRST_OBSERVED_YEAR = 2022;

    private static boolean isHoliday(LocalDate date) {
        int year = date.getYear();
        // Jan 1 of the *following* year is checked because a Sat-anchored New Year's observes back into December of
        // the prior year (e.g. Jan 1 2022 Sat → Fri Dec 31 2021).
        return date.equals(observed(LocalDate.of(year, Month.JANUARY, 1)))
                || date.equals(observed(LocalDate.of(year + 1, Month.JANUARY, 1)))
                || (year >= JUNETEENTH_FIRST_OBSERVED_YEAR
                        && date.equals(observed(LocalDate.of(year, Month.JUNE, 19))))
                || date.equals(observed(LocalDate.of(year, Month.JULY, 4)))
                || date.equals(observed(LocalDate.of(year, Month.DECEMBER, 25)))
                || date.equals(nthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3))
                || date.equals(nthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3))
                || date.equals(lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY))
                || date.equals(nthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1))
                || date.equals(nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4))
                || date.equals(goodFriday(year));
    }

    private static LocalDate observed(LocalDate holiday) {
        return switch (holiday.getDayOfWeek()) {
            case SATURDAY -> holiday.minusDays(1);
            case SUNDAY -> holiday.plusDays(1);
            default -> holiday;
        };
    }

    private static LocalDate nthWeekdayOfMonth(int year, Month month, DayOfWeek dow, int ordinal) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dow));
    }

    private static LocalDate lastWeekdayOfMonth(int year, Month month, DayOfWeek dow) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(dow));
    }

    private static LocalDate goodFriday(int year) {
        return easterSunday(year).minusDays(2);
    }

    // Anonymous Gregorian algorithm (Meeus/Jones/Butcher). Valid for years 1583+.
    private static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
