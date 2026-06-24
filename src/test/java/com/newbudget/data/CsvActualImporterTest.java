package com.newbudget.data;

import com.newbudget.model.CategoryType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvActualImporterTest {

    @Test
    void importSampleCsvLoadsExpectedMonthAndCategories() {
        Database.initialize();
        resetDatabase();
        BudgetRepository repository = new BudgetRepository();
        CsvActualImporter importer = new CsvActualImporter(repository);

        YearMonth imported = importer.importFile(Path.of("Mar2026.csv"));

        assertEquals(YearMonth.of(2026, 3), imported);
        assertTrue(repository.getAllCategories().stream().anyMatch(c -> c.name().equals("Fuel")));
        assertTrue(repository.getAllCategories().stream().anyMatch(c -> c.defaultType() == CategoryType.INCOME));
        assertTrue(repository.getMonthlyActuals(imported).values().stream().mapToDouble(Double::doubleValue).sum() > 0);
    }

    @Test
    void categoryClassificationIsPersistedPerMonth() {
        Database.initialize();
        resetDatabase();
        BudgetRepository repository = new BudgetRepository();
        CsvActualImporter importer = new CsvActualImporter(repository);

        YearMonth march = importer.importFile(Path.of("Mar2026.csv"));
        YearMonth april = YearMonth.of(2026, 4);
        repository.ensureMonthlyClassificationsFromDefaults(april);

        Integer amazonId = repository.getAllCategories().stream()
            .filter(c -> c.name().equals("Amazon"))
            .map(c -> c.id())
            .findFirst()
            .orElse(null);

        assertNotNull(amazonId);

        repository.setMonthlyClassification(march, amazonId, CategoryType.MANDATORY);
        repository.setMonthlyClassification(april, amazonId, CategoryType.DISCRETIONARY);

        assertEquals(
            CategoryType.MANDATORY,
            repository.getMonthlyClassifications(march).get(amazonId)
        );
        assertEquals(
            CategoryType.DISCRETIONARY,
            repository.getMonthlyClassifications(april).get(amazonId)
        );
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
