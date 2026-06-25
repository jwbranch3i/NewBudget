package com.newbudget.ui;

import org.junit.jupiter.api.Test;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BudgetViewControllerTest {

    @Test
    void determineInitialMonthPrefersCurrentMonthWhenDataExists() {
        YearMonth currentMonth = YearMonth.of(2026, Month.JUNE);

        YearMonth selectedMonth = BudgetViewController.determineInitialMonth(
            currentMonth,
            List.of(YearMonth.of(2026, Month.MARCH), currentMonth)
        );

        assertEquals(currentMonth, selectedMonth);
    }

    @Test
    void determineInitialMonthFallsBackToMostRecentMonthWithData() {
        YearMonth currentMonth = YearMonth.of(2026, Month.JUNE);
        YearMonth latestMonthWithData = YearMonth.of(2026, Month.MAY);

        YearMonth selectedMonth = BudgetViewController.determineInitialMonth(
            currentMonth,
            List.of(YearMonth.of(2026, Month.MARCH), latestMonthWithData)
        );

        assertEquals(latestMonthWithData, selectedMonth);
    }

    @Test
    void determineInitialMonthUsesCurrentMonthWhenNoDataExists() {
        YearMonth currentMonth = YearMonth.of(2026, Month.JUNE);

        YearMonth selectedMonth = BudgetViewController.determineInitialMonth(currentMonth, List.of());

        assertEquals(currentMonth, selectedMonth);
    }
}