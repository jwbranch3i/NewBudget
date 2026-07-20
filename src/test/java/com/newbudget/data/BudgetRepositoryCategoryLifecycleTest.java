package com.newbudget.data;

import com.newbudget.model.CategoryType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Month;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetRepositoryCategoryLifecycleTest {

    @Test
    void deleteLeafCategoryCascadesMonthScopedRows() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        int categoryId = repository.createCategory("Coffee", "Coffee", null, CategoryType.DISCRETIONARY);

        YearMonth march = YearMonth.of(2026, Month.MARCH);
        YearMonth april = YearMonth.of(2026, Month.APRIL);

        repository.upsertMonthlyActual(march, categoryId, 17.0);
        repository.upsertMonthlyBudget(march, categoryId, 30.0);
        repository.setMonthlyClassification(march, categoryId, CategoryType.DISCRETIONARY);
        repository.setMonthlyClassification(april, categoryId, CategoryType.DISCRETIONARY);
        repository.upsertMonthlyBalanceOverride(april, categoryId, 44.0);
        repository.setMonthlyHidden(april, categoryId, true);
        int accountId = repository.createAccount("To John");
        repository.assignCategoryToAccount(accountId, categoryId);

        repository.deleteLeafCategory(categoryId);

        assertTrue(repository.getCategoryById(categoryId).isEmpty());
        assertFalse(repository.getMonthlyActuals(march).containsKey(categoryId));
        assertFalse(repository.getMonthlyBudgets(march).containsKey(categoryId));
        assertFalse(repository.getMonthlyClassifications(march).containsKey(categoryId));
        assertFalse(repository.getMonthlyClassifications(april).containsKey(categoryId));
        assertFalse(repository.getMonthlyBalanceOverrides(april).containsKey(categoryId));
        assertFalse(repository.getMonthlyHiddenCategoryIds(april).contains(categoryId));
        assertTrue(repository.getAssignedAccountForCategory(categoryId).isEmpty());
    }

    @Test
    void createAccountRejectsDuplicateNameCaseInsensitive() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        repository.createAccount("To John");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> repository.createAccount("to john")
        );

        assertEquals("An account with that name already exists.", exception.getMessage());
    }

    @Test
    void rollupFlagClearsAfterRemovingLastChild() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        int parentId = repository.createCategory("Auto", "Auto", null, CategoryType.MANDATORY);
        int childId = repository.createCategory("Fuel", "Auto/Fuel", parentId, CategoryType.MANDATORY);
        repository.markRollup(parentId);

        assertTrue(repository.getCategoryById(parentId).orElseThrow().rollup());

        repository.deleteLeafCategory(childId);

        assertFalse(repository.getCategoryById(parentId).orElseThrow().rollup());
    }

    @Test
    void duplicateSiblingNameIsRejectedCaseInsensitive() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        int parentId = repository.createCategory("Home", "Home", null, CategoryType.MANDATORY);
        repository.createCategory("Utilities", "Home/Utilities", parentId, CategoryType.MANDATORY);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> repository.createCategory("utilities", "Home/utilities", parentId, CategoryType.MANDATORY)
        );

        assertEquals("A category with that name already exists here.", exception.getMessage());
    }

    @Test
    void createCategoryStoresProvidedPathWithParentDelimiter() {
        Database.initialize();
        resetDatabase();

        BudgetRepository repository = new BudgetRepository();
        int parentId = repository.createCategory("Travel", "Travel", null, CategoryType.DISCRETIONARY);
        int childId = repository.createCategory("Flights", "Travel/Flights", parentId, CategoryType.DISCRETIONARY);

        assertEquals("Travel/Flights", repository.getCategoryById(childId).orElseThrow().path());
    }

    private void resetDatabase() {
        try (Connection connection = Database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM monthly_classifications");
            statement.execute("DELETE FROM monthly_budgets");
            statement.execute("DELETE FROM monthly_actuals");
            statement.execute("DELETE FROM monthly_balance_overrides");
            statement.execute("DELETE FROM monthly_hidden_categories");
            statement.execute("DELETE FROM account_category_assignments");
            statement.execute("DELETE FROM accounts");
            statement.execute("DELETE FROM categories");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset test database", e);
        }
    }
}
