package com.newbudget.data;

import com.newbudget.model.CategoryType;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CsvActualImporter {
    private static final Pattern MONTH_RANGE = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})\\s+through\\s+(\\d{1,2}/\\d{1,2}/\\d{4})");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");

    private final BudgetRepository repository;

    public CsvActualImporter(BudgetRepository repository) {
        this.repository = repository;
    }

    public YearMonth importFile(Path csvPath) {
        List<String[]> rows = readRows(csvPath);
        YearMonth month = detectMonth(rows)
            .orElseThrow(() -> new IllegalArgumentException("Unable to detect month from CSV file"));

        repository.clearMonthActuals(month);
        repository.resetRollups();

        Section currentSection = Section.NONE;
        Integer sectionBaseDepth = null;
        int sortOrder = 1;

        Map<Integer, String> pathByDepth = new HashMap<>();
        Map<Integer, Integer> categoryByDepth = new HashMap<>();

        for (String[] row : rows) {
            String rawCategory = readColumn(row, 1);
            if (rawCategory.isBlank()) {
                continue;
            }

            String categoryText = rawCategory.trim();
            if (categoryText.equalsIgnoreCase("INFLOWS")) {
                currentSection = Section.INCOME;
                sectionBaseDepth = null;
                pathByDepth.clear();
                categoryByDepth.clear();
                continue;
            }
            if (categoryText.equalsIgnoreCase("OUTFLOWS")) {
                currentSection = Section.OUTFLOW;
                sectionBaseDepth = null;
                pathByDepth.clear();
                categoryByDepth.clear();
                continue;
            }
            if (categoryText.equalsIgnoreCase("OVERALL TOTAL")) {
                continue;
            }
            if (categoryText.regionMatches(true, 0, "TOTAL ", 0, 6)) {
                continue;
            }
            if (currentSection == Section.NONE) {
                continue;
            }

            int rawDepth = Math.max(0, countLeadingSpaces(rawCategory) / 4);
            if (sectionBaseDepth == null || rawDepth < sectionBaseDepth) {
                sectionBaseDepth = rawDepth;
            }
            int depth = Math.max(0, rawDepth - sectionBaseDepth);
            String parentPath = depth == 0 ? null : pathByDepth.get(depth - 1);
            Integer parentId = depth == 0 ? null : categoryByDepth.get(depth - 1);

            if (depth > 0 && (parentPath == null || parentId == null)) {
                depth = 0;
                parentPath = null;
                parentId = null;
            }

            String categoryName = categoryText;
            String categoryPath = parentPath == null
                ? categoryName
                : parentPath + " > " + categoryName;
            CategoryType defaultType = currentSection == Section.INCOME
                ? CategoryType.INCOME
                : CategoryType.DISCRETIONARY;

            int categoryId = repository.findOrCreateCategory(
                categoryName,
                categoryPath,
                parentId,
                sortOrder++,
                defaultType
            );

            if (parentId != null) {
                repository.markRollup(parentId);
            }

            pathByDepth.put(depth, categoryPath);
            categoryByDepth.put(depth, categoryId);
            clearDeeperDepths(depth, pathByDepth, categoryByDepth);

            String amountValue = readColumn(row, 2);
            parseAmount(amountValue).ifPresent(amount -> repository.upsertMonthlyActual(month, categoryId, amount));
        }

        repository.ensureMonthlyClassificationsFromDefaults(month);
        return month;
    }

    private List<String[]> readRows(Path path) {
        try (CSVReader csvReader = new CSVReader(Files.newBufferedReader(path))) {
            return csvReader.readAll();
        } catch (IOException | CsvException e) {
            throw new IllegalStateException("Failed to read CSV file", e);
        }
    }

    private Optional<YearMonth> detectMonth(List<String[]> rows) {
        for (String[] row : rows) {
            String line = String.join(" ", row);
            Matcher matcher = MONTH_RANGE.matcher(line);
            if (matcher.find()) {
                LocalDate endDate = LocalDate.parse(matcher.group(2), DATE_FORMAT);
                return Optional.of(YearMonth.of(endDate.getYear(), endDate.getMonth()));
            }
        }
        return Optional.empty();
    }

    private String readColumn(String[] row, int index) {
        if (row.length <= index || row[index] == null) {
            return "";
        }
        return row[index];
    }

    private Optional<Double> parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.replace(",", "").trim();
        try {
            return Optional.of(Double.parseDouble(normalized));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private int countLeadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private void clearDeeperDepths(int depth, Map<Integer, String> pathByDepth, Map<Integer, Integer> categoryByDepth) {
        int nextDepth = depth + 1;
        while (pathByDepth.containsKey(nextDepth)) {
            pathByDepth.remove(nextDepth);
            categoryByDepth.remove(nextDepth);
            nextDepth++;
        }
    }

    private enum Section {
        NONE,
        INCOME,
        OUTFLOW
    }
}
