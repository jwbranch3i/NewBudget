package com.newbudget.service;

import com.newbudget.data.BudgetRepository;
import com.newbudget.data.Database;
import com.newbudget.model.CategoryType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Month;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private void resetDatabase() {
        try (Connection connection = Database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM monthly_classifications");
            statement.execute("DELETE FROM monthly_budgets");
            statement.execute("DELETE FROM monthly_actuals");
            statement.execute("DELETE FROM categories");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset test database", e);
        }
    }
}
