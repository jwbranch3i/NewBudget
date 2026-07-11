package com.newbudget.ui;

import com.newbudget.model.BudgetLine;
import com.newbudget.model.CategoryType;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void calculateSectionTotalsUsesTopLevelRowsOnly() {
        List<BudgetTableRow> rows = List.of(
            row(1, 0, 1000.0, 1200.0, 200.0, 500.0),
            row(2, 1, 100.0, 120.0, 20.0, 50.0),
            row(3, 0, 600.0, 700.0, 100.0, 300.0)
        );

        Object totals = BudgetViewController.calculateSectionTotals(rows);

        assertEquals(1600.0, extract(totals, "actual"));
        assertEquals(1900.0, extract(totals, "budget"));
        assertEquals(300.0, extract(totals, "difference"));
        assertEquals(800.0, extract(totals, "balance"));
    }

    @Test
    void calculateNetTotalsSubtractsMandatoryAndDiscretionaryFromIncome() {
        Object income = BudgetViewController.calculateSectionTotals(List.of(row(1, 0, 3000.0, 3200.0, 200.0, 1500.0)));
        Object mandatory = BudgetViewController.calculateSectionTotals(List.of(row(2, 0, 900.0, 1000.0, 100.0, 400.0)));
        Object discretionary = BudgetViewController.calculateSectionTotals(List.of(row(3, 0, 400.0, 500.0, 100.0, 150.0)));

        Object net = BudgetViewController.calculateNetTotals(
            (BudgetViewController.SectionTotals) income,
            (BudgetViewController.SectionTotals) mandatory,
            (BudgetViewController.SectionTotals) discretionary
        );

        assertEquals(1700.0, extract(net, "actual"));
        assertEquals(1700.0, extract(net, "budget"));
        assertEquals(0.0, extract(net, "difference"));
        assertEquals(950.0, extract(net, "balance"));
    }

    @Test
    void childOfMasterRowsBlankBudgetLikeCells() {
        BudgetTableRow childRow = row(10, 1, 120.0, 150.0, 30.0, 75.0, false, true);
        BudgetTableRow normalRow = row(11, 1, 120.0, 150.0, 30.0, 75.0, false, false);

        assertTrue(BudgetViewController.shouldBlankBudgetLikeCell(childRow));
        assertFalse(BudgetViewController.shouldBlankBudgetLikeCell(normalRow));
    }

    @Test
    void masterToggleIsOfferedOnlyForParentRows() {
        BudgetTableRow parentRow = row(20, 0, 200.0, 300.0, 100.0, 250.0, true, false);
        TreeItem<BudgetTableRow> parentItem = new TreeItem<>(parentRow);
        parentItem.getChildren().add(new TreeItem<>(row(21, 1, 100.0, 125.0, 25.0, 80.0, false, false)));

        BudgetTableRow leafRow = row(22, 0, 75.0, 100.0, 25.0, 60.0, false, false);
        TreeItem<BudgetTableRow> leafItem = new TreeItem<>(leafRow);

        assertTrue(BudgetViewController.shouldOfferMasterToggle(parentRow, parentItem));
        assertFalse(BudgetViewController.shouldOfferMasterToggle(leafRow, leafItem));
    }

    private BudgetTableRow row(int id, int depth, double actual, double budget, double diff, double balance) {
        return row(id, depth, actual, budget, diff, balance, false, false);
    }

    private BudgetTableRow row(
        int id,
        int depth,
        double actual,
        double budget,
        double diff,
        double balance,
        boolean master,
        boolean childOfMaster
    ) {
        return new BudgetTableRow(new BudgetLine(
            id,
            "Category " + id,
            depth,
            actual,
            budget,
            diff,
            balance,
            CategoryType.MANDATORY,
            false,
            master,
            childOfMaster,
            false
        ));
    }

    private double extract(Object totals, String method) {
        try {
            return (double) totals.getClass().getMethod(method).invoke(totals);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to read totals method: " + method, ex);
        }
    }
}