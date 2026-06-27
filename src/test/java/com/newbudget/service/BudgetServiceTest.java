package com.newbudget.service;

import com.newbudget.data.BudgetRepository;
import com.newbudget.data.Database;
import com.newbudget.model.BudgetLine;
import com.newbudget.model.CategoryType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Month;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetServiceTest {

    @Test
    void movingLeafMovesParentAndSiblingLeaves() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int parentId = repository.findOrCreateCategory("Parent Group", "Parent Group", null, 1, CategoryType.MANDATORY);
        int childAId = repository.findOrCreateCategory("Child A", "Parent Group/Child A", parentId, 2, CategoryType.MANDATORY);
        int childBId = repository.findOrCreateCategory("Child B", "Parent Group/Child B", parentId, 3, CategoryType.MANDATORY);
        int otherId = repository.findOrCreateCategory("Other", "Other", null, 4, CategoryType.MANDATORY);

        YearMonth month = YearMonth.of(2026, Month.MARCH);
        repository.ensureMonthlyClassificationsFromDefaults(month);

        service.updateMonthlyClassificationGroup(month, childAId, CategoryType.DISCRETIONARY);

        Map<Integer, CategoryType> types = repository.getMonthlyClassifications(month);
        assertEquals(CategoryType.DISCRETIONARY, types.get(parentId));
        assertEquals(CategoryType.DISCRETIONARY, types.get(childAId));
        assertEquals(CategoryType.DISCRETIONARY, types.get(childBId));
        assertEquals(CategoryType.MANDATORY, types.get(otherId));
    }

    @Test
    void movingParentMovesEntireSubtree() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int parentId = repository.findOrCreateCategory("Parent Group", "Parent Group", null, 1, CategoryType.DISCRETIONARY);
        int childAId = repository.findOrCreateCategory(
            "Child A",
            "Parent Group/Child A",
            parentId,
            2,
            CategoryType.DISCRETIONARY
        );
        int childBId = repository.findOrCreateCategory(
            "Child B",
            "Parent Group/Child B",
            parentId,
            3,
            CategoryType.DISCRETIONARY
        );

        YearMonth month = YearMonth.of(2026, Month.APRIL);
        repository.ensureMonthlyClassificationsFromDefaults(month);

        service.updateMonthlyClassificationGroup(month, parentId, CategoryType.MANDATORY);

        Map<Integer, CategoryType> types = repository.getMonthlyClassifications(month);
        assertEquals(CategoryType.MANDATORY, types.get(parentId));
        assertEquals(CategoryType.MANDATORY, types.get(childAId));
        assertEquals(CategoryType.MANDATORY, types.get(childBId));
    }

    @Test
    void deletingMonthDataClearsAllMonthScopedTablesForMonth() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int categoryId = repository.findOrCreateCategory("Fuel", "Fuel", null, 1, CategoryType.MANDATORY);

        YearMonth month = YearMonth.of(2026, Month.MAY);
        repository.upsertMonthlyActual(month, categoryId, 100.0);
        repository.upsertMonthlyBudget(month, categoryId, 125.0);
        repository.setMonthlyClassification(month, categoryId, CategoryType.DISCRETIONARY);
        repository.upsertMonthlyBalanceOverride(month, categoryId, 55.0);
        repository.setMonthlyHidden(month, categoryId, true);

        service.deleteMonthData(month);

        assertTrue(repository.getMonthlyActuals(month).isEmpty());
        assertTrue(repository.getMonthlyBudgets(month).isEmpty());
        assertTrue(repository.getMonthlyClassifications(month).isEmpty());
        assertTrue(repository.getMonthlyBalanceOverrides(month).isEmpty());
        assertTrue(repository.getMonthlyHiddenCategoryIds(month).isEmpty());
        assertFalse(repository.getAvailableMonths().contains(month));
        assertTrue(repository.getAllCategories().isEmpty());
    }

    @Test
    void deletingAllMonthsDataClearsAllMonthScopedDataAndCategories() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int categoryId = repository.findOrCreateCategory("Rent", "Rent", null, 1, CategoryType.MANDATORY);
        YearMonth month = YearMonth.of(2026, Month.JUNE);
        repository.upsertMonthlyActual(month, categoryId, 1400.0);
        repository.upsertMonthlyBudget(month, categoryId, 1500.0);
        repository.setMonthlyClassification(month, categoryId, CategoryType.MANDATORY);
        repository.upsertMonthlyBalanceOverride(month, categoryId, 250.0);
        repository.setMonthlyHidden(month, categoryId, true);

        service.deleteAllMonthsData();

        assertTrue(repository.getMonthlyActuals(month).isEmpty());
        assertTrue(repository.getMonthlyBudgets(month).isEmpty());
        assertTrue(repository.getMonthlyClassifications(month).isEmpty());
        assertTrue(repository.getMonthlyBalanceOverrides(month).isEmpty());
        assertTrue(repository.getMonthlyHiddenCategoryIds(month).isEmpty());
        assertTrue(repository.getAvailableMonths().isEmpty());
        assertTrue(repository.getAllCategories().isEmpty());
    }

    @Test
    void hiddenLeafIsExcludedFromVisibleSnapshotAndTotals() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int autoId = repository.findOrCreateCategory("Auto", "Auto", null, 1, CategoryType.MANDATORY);
        int fuelId = repository.findOrCreateCategory("Fuel", "Auto/Fuel", autoId, 2, CategoryType.MANDATORY);
        int maintenanceId = repository.findOrCreateCategory("Maintenance", "Auto/Maintenance", autoId, 3, CategoryType.MANDATORY);

        YearMonth month = YearMonth.of(2026, Month.MARCH);
        repository.upsertMonthlyBudget(month, fuelId, 120.0);
        repository.upsertMonthlyActual(month, fuelId, 70.0);
        repository.upsertMonthlyBudget(month, maintenanceId, 80.0);
        repository.upsertMonthlyActual(month, maintenanceId, 50.0);

        service.updateCategoryHiddenState(month, fuelId, true);

        BudgetLine parent = findLine(service.loadMonth(month, false).mandatory(), autoId);
        assertEquals(80.0, parent.budgetAmount());
        assertEquals(50.0, parent.actualAmount());
        assertEquals(30.0, parent.difference());

        boolean hiddenLeafPresent = service.loadMonth(month, false).mandatory().stream()
            .anyMatch(line -> line.categoryId() == fuelId);
        assertFalse(hiddenLeafPresent);
    }

    @Test
    void hiddenLeafAppearsWhenIncludingHiddenAndMarkedHidden() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int parentId = repository.findOrCreateCategory("Auto", "Auto", null, 1, CategoryType.MANDATORY);
        int fuelId = repository.findOrCreateCategory("Fuel", "Auto/Fuel", parentId, 2, CategoryType.MANDATORY);

        YearMonth month = YearMonth.of(2026, Month.APRIL);
        repository.upsertMonthlyBudget(month, fuelId, 150.0);
        repository.upsertMonthlyActual(month, fuelId, 90.0);
        service.updateCategoryHiddenState(month, fuelId, true);

        BudgetLine hiddenLine = service.loadMonth(month, true).mandatory().stream()
            .filter(line -> line.categoryId() == fuelId)
            .findFirst()
            .orElse(null);

        assertNotNull(hiddenLine);
        assertTrue(hiddenLine.hidden());
    }

    @Test
    void monthBalanceOverrideIsUsedAndCarriesForwardToFutureMonths() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int categoryId = repository.findOrCreateCategory("Fuel", "Fuel", null, 1, CategoryType.MANDATORY);

        YearMonth january = YearMonth.of(2026, Month.JANUARY);
        YearMonth february = YearMonth.of(2026, Month.FEBRUARY);
        YearMonth march = YearMonth.of(2026, Month.MARCH);

        repository.upsertMonthlyBudget(january, categoryId, 100.0);
        repository.upsertMonthlyActual(january, categoryId, 90.0);

        repository.upsertMonthlyBudget(february, categoryId, 120.0);
        repository.upsertMonthlyActual(february, categoryId, 100.0);

        service.updateBalance(february, categoryId, 500.0);

        repository.upsertMonthlyBudget(march, categoryId, 80.0);
        repository.upsertMonthlyActual(march, categoryId, 30.0);

        double februaryBalance = findLine(service.loadMonth(february).mandatory(), categoryId).balance();
        double marchBalance = findLine(service.loadMonth(march).mandatory(), categoryId).balance();

        assertEquals(500.0, februaryBalance);
        assertEquals(550.0, marchBalance);
    }

    @Test
    void parentBalanceIsSumOfLeafBalancesOnlyForSelectedMonth() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int parentId = repository.findOrCreateCategory("Auto", "Auto", null, 1, CategoryType.MANDATORY);
        int fuelId = repository.findOrCreateCategory("Fuel", "Auto/Fuel", parentId, 2, CategoryType.MANDATORY);
        int maintenanceId = repository.findOrCreateCategory(
            "Maintenance",
            "Auto/Maintenance",
            parentId,
            3,
            CategoryType.MANDATORY
        );

        YearMonth january = YearMonth.of(2026, Month.JANUARY);
        YearMonth february = YearMonth.of(2026, Month.FEBRUARY);

        repository.upsertMonthlyBudget(january, fuelId, 100.0);
        repository.upsertMonthlyActual(january, fuelId, 60.0);
        repository.upsertMonthlyBudget(january, maintenanceId, 80.0);
        repository.upsertMonthlyActual(january, maintenanceId, 70.0);

        service.updateBalance(january, parentId, 999.0);

        repository.upsertMonthlyBudget(february, fuelId, 120.0);
        repository.upsertMonthlyActual(february, fuelId, 100.0);
        service.updateBalance(february, maintenanceId, 500.0);

        double fuelBalance = findLine(service.loadMonth(february).mandatory(), fuelId).balance();
        double maintenanceBalance = findLine(service.loadMonth(february).mandatory(), maintenanceId).balance();
        double parentBalance = findLine(service.loadMonth(february).mandatory(), parentId).balance();

        assertEquals(60.0, fuelBalance);
        assertEquals(500.0, maintenanceBalance);
        assertEquals(560.0, parentBalance);
    }

    @Test
    void rollupWithoutChildrenBehavesLikeLeafForBalanceCarryAndOverride() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        BudgetService service = new BudgetService(repository);

        int categoryId = repository.findOrCreateCategory("Auto", "Auto", null, 1, CategoryType.MANDATORY);
        repository.markRollup(categoryId);

        YearMonth january = YearMonth.of(2026, Month.JANUARY);
        YearMonth february = YearMonth.of(2026, Month.FEBRUARY);
        YearMonth march = YearMonth.of(2026, Month.MARCH);

        repository.upsertMonthlyBudget(january, categoryId, 200.0);
        repository.upsertMonthlyActual(january, categoryId, 50.0);

        service.updateBalance(february, categoryId, 300.0);

        repository.upsertMonthlyBudget(march, categoryId, 100.0);
        repository.upsertMonthlyActual(march, categoryId, 60.0);

        double januaryBalance = findLine(service.loadMonth(january).mandatory(), categoryId).balance();
        double februaryBalance = findLine(service.loadMonth(february).mandatory(), categoryId).balance();
        double marchBalance = findLine(service.loadMonth(march).mandatory(), categoryId).balance();

        assertEquals(150.0, januaryBalance);
        assertEquals(300.0, februaryBalance);
        assertEquals(340.0, marchBalance);
    }

    private BudgetLine findLine(java.util.List<BudgetLine> lines, int categoryId) {
        return lines.stream()
            .filter(line -> line.categoryId() == categoryId)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Category not found in snapshot: " + categoryId));
    }

    private void resetDatabase() {
        try (Connection connection = Database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM monthly_classifications");
            statement.execute("DELETE FROM monthly_budgets");
            statement.execute("DELETE FROM monthly_actuals");
            statement.execute("DELETE FROM monthly_balance_overrides");
            statement.execute("DELETE FROM monthly_hidden_categories");
            statement.execute("DELETE FROM categories");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset test database", e);
        }
    }
}
