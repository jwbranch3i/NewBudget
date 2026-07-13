package com.newbudget.service;

import com.newbudget.model.BudgetLine;
import com.newbudget.model.CategoryRecord;
import com.newbudget.model.CategoryType;
import com.newbudget.model.MonthSnapshot;
import com.newbudget.data.BudgetRepository;

import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeSet;

public class BudgetService {
    private final BudgetRepository repository;

    public BudgetService(BudgetRepository repository) {
        this.repository = repository;
    }

    public MonthSnapshot loadMonth(YearMonth month) {
        return loadMonth(month, true);
    }

    public MonthSnapshot loadMonth(YearMonth month, boolean includeHidden) {
        repository.ensureMonthlyClassificationsFromDefaults(month);

        List<CategoryRecord> categories = repository.getAllCategories();
        Map<Integer, Double> monthlyActuals = repository.getMonthlyActuals(month);
        Map<Integer, Double> monthlyBudgets = repository.getMonthlyBudgets(month);
        Map<Integer, Double> effectiveBalances = computeEffectiveBalances(month, categories);
        Map<Integer, CategoryType> monthlyTypes = repository.getMonthlyClassifications(month);
        Set<Integer> hiddenCategoryIds = repository.getMonthlyHiddenCategoryIds(month);

        Map<Integer, Node> nodesById = new HashMap<>();
        for (CategoryRecord category : categories) {
            CategoryType effectiveType = monthlyTypes.getOrDefault(category.id(), category.defaultType());
            boolean hidden = hiddenCategoryIds.contains(category.id());
            nodesById.put(category.id(), new Node(category, effectiveType, hidden));
        }

        List<Node> roots = new ArrayList<>();
        for (Node node : nodesById.values()) {
            Integer parentId = node.category.parentId();
            if (parentId == null) {
                roots.add(node);
            } else {
                Node parent = nodesById.get(parentId);
                if (parent != null) {
                    parent.children.add(node);
                }
            }
        }

        Comparator<Node> byOrder = Comparator.comparingInt(n -> n.category.sortOrder());
        roots.sort(byOrder);
        for (Node node : nodesById.values()) {
            node.children.sort(byOrder);
        }

        for (Node root : roots) {
            calculateNode(root, monthlyActuals, monthlyBudgets, effectiveBalances, includeHidden);
        }

        List<BudgetLine> incomeLines = new ArrayList<>();
        List<BudgetLine> mandatoryLines = new ArrayList<>();
        List<BudgetLine> discretionaryLines = new ArrayList<>();

        for (Node root : roots) {
            collectLines(root, 0, CategoryType.INCOME, incomeLines, includeHidden, false);
            collectLines(root, 0, CategoryType.MANDATORY, mandatoryLines, includeHidden, false);
            collectLines(root, 0, CategoryType.DISCRETIONARY, discretionaryLines, includeHidden, false);
        }

        return new MonthSnapshot(month, incomeLines, mandatoryLines, discretionaryLines);
    }

    public void updateBudget(YearMonth month, int categoryId, double budgetAmount) {
        repository.upsertMonthlyBudget(month, categoryId, budgetAmount);
    }

    public void updateBalance(YearMonth month, int categoryId, double balanceAmount) {
        if (repository.isMasterCategory(categoryId) && repository.categoryHasChildren(categoryId)) {
            double currentBudget = repository.getMonthlyBudgets(month).getOrDefault(categoryId, 0.0);
            double childActualTotal = calculateChildActualTotal(month, categoryId);
            double storedMasterBalance = balanceAmount - currentBudget + childActualTotal;
            repository.upsertMonthlyBalanceOverride(month, categoryId, storedMasterBalance);
            return;
        }

        repository.upsertMonthlyBalanceOverride(month, categoryId, balanceAmount);
    }

    public void updateMasterCategory(int categoryId, boolean master) {
        repository.setMasterCategory(categoryId, master);
    }

    public void updateMonthlyClassification(YearMonth month, int categoryId, CategoryType type) {
        CategoryType defaultType = repository.getCategoryDefaultType(categoryId);
        if (defaultType == CategoryType.INCOME) {
            return;
        }
        if (type == CategoryType.INCOME) {
            return;
        }
        repository.setMonthlyClassification(month, categoryId, type);
    }

    public void updateMonthlyClassificationGroup(YearMonth month, int categoryId, CategoryType targetType) {
        if (targetType == CategoryType.INCOME) {
            return;
        }

        repository.ensureMonthlyClassificationsFromDefaults(month);
        List<CategoryRecord> categories = repository.getAllCategories();

        Map<Integer, CategoryRecord> categoriesById = new HashMap<>();
        Map<Integer, List<Integer>> childrenByParentId = new HashMap<>();
        for (CategoryRecord category : categories) {
            categoriesById.put(category.id(), category);
            if (category.parentId() != null) {
                childrenByParentId.computeIfAbsent(category.parentId(), ignored -> new ArrayList<>()).add(category.id());
            }
        }

        CategoryRecord selected = categoriesById.get(categoryId);
        if (selected == null) {
            return;
        }

        int groupRootId = resolveGroupRootId(selected, categoriesById, childrenByParentId);
        List<Integer> groupCategoryIds = collectSubtreeCategoryIds(groupRootId, childrenByParentId);

        for (Integer id : groupCategoryIds) {
            CategoryRecord category = categoriesById.get(id);
            if (category != null && category.defaultType() != CategoryType.INCOME) {
                repository.setMonthlyClassification(month, id, targetType);
            }
        }
    }

    public void updateCategoryHiddenState(YearMonth month, int categoryId, boolean hidden) {
        List<CategoryRecord> categories = repository.getAllCategories();
        Map<Integer, List<Integer>> childrenByParentId = new HashMap<>();
        for (CategoryRecord category : categories) {
            if (category.parentId() != null) {
                childrenByParentId.computeIfAbsent(category.parentId(), ignored -> new ArrayList<>()).add(category.id());
            }
        }

        for (Integer id : collectSubtreeCategoryIds(categoryId, childrenByParentId)) {
            repository.setMonthlyHidden(month, id, hidden);
        }
    }

    public int addCategory(YearMonth month, String name, Integer parentId, CategoryType sectionType) {
        if (month == null) {
            throw new IllegalArgumentException("Month is required.");
        }

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be blank.");
        }

        CategoryType categoryType;
        String path;
        if (parentId == null) {
            if (sectionType == null) {
                throw new IllegalArgumentException("Category section is required.");
            }
            categoryType = sectionType;
            path = trimmedName;
        } else {
            CategoryRecord parent = repository.getCategoryById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent category not found."));
            repository.ensureMonthlyClassificationsFromDefaults(month);
            CategoryType parentType = repository.getMonthlyClassifications(month)
                .getOrDefault(parent.id(), parent.defaultType());
            if (parentType == CategoryType.INCOME) {
                throw new IllegalArgumentException("New categories must be Mandatory or Discretionary.");
            }
            categoryType = parentType;
            path = parent.path() + "/" + trimmedName;
        }

        int categoryId = repository.createCategory(trimmedName, path, parentId, categoryType);
        if (parentId != null) {
            repository.markRollup(parentId);
        }

        TreeSet<YearMonth> seedMonths = new TreeSet<>(repository.getAvailableMonthsOnOrAfter(month));
        seedMonths.add(month);
        for (YearMonth seedMonth : seedMonths) {
            repository.setMonthlyClassification(seedMonth, categoryId, categoryType);
            if (!repository.getMonthlyBudgets(seedMonth).containsKey(categoryId)) {
                repository.upsertMonthlyBudget(seedMonth, categoryId, 0.0);
            }
        }

        return categoryId;
    }

    public void deleteCategory(int categoryId) {
        repository.deleteLeafCategory(categoryId);
    }

    public void deleteMonthData(YearMonth month) {
        repository.deleteMonthData(month);
    }

    public void deleteAllMonthsData() {
        repository.deleteAllMonthsData();
    }

    private int resolveGroupRootId(
        CategoryRecord selected,
        Map<Integer, CategoryRecord> categoriesById,
        Map<Integer, List<Integer>> childrenByParentId
    ) {
        boolean selectedHasChildren = !childrenByParentId.getOrDefault(selected.id(), Collections.emptyList()).isEmpty();
        if (selectedHasChildren || selected.parentId() == null) {
            return selected.id();
        }

        CategoryRecord parent = categoriesById.get(selected.parentId());
        return parent == null ? selected.id() : parent.id();
    }

    private List<Integer> collectSubtreeCategoryIds(int rootId, Map<Integer, List<Integer>> childrenByParentId) {
        List<Integer> ids = new ArrayList<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(rootId);

        while (!stack.isEmpty()) {
            int current = stack.pop();
            ids.add(current);
            List<Integer> children = childrenByParentId.getOrDefault(current, Collections.emptyList());
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }

        return ids;
    }

    private Totals calculateNode(
        Node node,
        Map<Integer, Double> monthlyActuals,
        Map<Integer, Double> monthlyBudgets,
        Map<Integer, Double> effectiveBalances,
        boolean includeHidden
    ) {
        if (!includeHidden && node.hidden) {
            node.actual = 0.0;
            node.budget = 0.0;
            node.difference = 0.0;
            node.balance = 0.0;
            return new Totals(0.0, 0.0, 0.0);
        }

        double actual = monthlyActuals.getOrDefault(node.category.id(), 0.0);
        double ownBudget = monthlyBudgets.getOrDefault(node.category.id(), 0.0);
        double ownBalance = effectiveBalances.getOrDefault(node.category.id(), 0.0);
        double childBalance = 0.0;
        double childActual = 0.0;
        double childBudget = 0.0;

        for (Node child : node.children) {
            Totals childTotals = calculateNode(child, monthlyActuals, monthlyBudgets, effectiveBalances, includeHidden);
            childActual += childTotals.actual;
            childBudget += childTotals.budget;
            childBalance += childTotals.balance;
        }

        double budget;
        double balance;
        if (node.category.master() && !node.children.isEmpty()) {
            actual = childActual;
            budget = ownBudget;
            balance = ownBalance;
        } else {
            actual += childActual;
            budget = ownBudget + childBudget;
            balance = node.children.isEmpty() ? ownBalance : childBalance;
        }

        node.actual = actual;
        node.budget = budget;
        node.difference = budget - actual;
        node.balance = balance;
        return new Totals(actual, budget, balance);
    }

    private Map<Integer, Double> computeEffectiveBalances(YearMonth month, List<CategoryRecord> categories) {
        Map<Integer, Map<YearMonth, Double>> budgetsByMonth = repository.getMonthlyBudgetAmountsUpTo(month);
        Map<Integer, Map<YearMonth, Double>> actualsByMonth = repository.getMonthlyActualAmountsUpTo(month);
        Map<Integer, Map<YearMonth, Double>> overridesByMonth = repository.getMonthlyBalanceOverridesUpTo(month);

        Map<Integer, Node> nodesById = new HashMap<>();
        for (CategoryRecord category : categories) {
            nodesById.put(category.id(), new Node(category, category.defaultType(), false));
        }

        List<Node> roots = new ArrayList<>();
        for (Node node : nodesById.values()) {
            Integer parentId = node.category.parentId();
            if (parentId == null) {
                roots.add(node);
                continue;
            }

            Node parent = nodesById.get(parentId);
            if (parent != null) {
                parent.children.add(node);
            }
        }

        Comparator<Node> byOrder = Comparator.comparingInt(n -> n.category.sortOrder());
        roots.sort(byOrder);
        for (Node node : nodesById.values()) {
            node.children.sort(byOrder);
        }

        TreeSet<Integer> categoryIds = new TreeSet<>();
        categoryIds.addAll(budgetsByMonth.keySet());
        categoryIds.addAll(actualsByMonth.keySet());
        categoryIds.addAll(overridesByMonth.keySet());

        Map<Integer, Double> previousBalances = new HashMap<>();
        java.util.TreeSet<YearMonth> timelineMonths = new java.util.TreeSet<>();
        timelineMonths.add(month);
        collectTimelineMonths(budgetsByMonth, timelineMonths);
        collectTimelineMonths(actualsByMonth, timelineMonths);
        collectTimelineMonths(overridesByMonth, timelineMonths);

        for (YearMonth timelineMonth : timelineMonths) {
            Map<Integer, Double> currentBalances = new HashMap<>(categoryIds.size());
            for (Node root : roots) {
                computeBalanceStateForMonth(root, timelineMonth, budgetsByMonth, actualsByMonth, overridesByMonth, previousBalances, currentBalances);
            }
            previousBalances = currentBalances;
        }

        return previousBalances;
    }

    private void collectLines(
        Node node,
        int depth,
        CategoryType targetType,
        List<BudgetLine> out,
        boolean includeHidden,
        boolean childOfMaster
    ) {
        if (!includeHidden && node.hidden) {
            return;
        }

        if (node.type == targetType) {
            out.add(new BudgetLine(
                node.category.id(),
                node.category.name(),
                depth,
                node.actual,
                node.budget,
                node.difference,
                node.balance,
                node.type,
                node.category.rollup() || !node.children.isEmpty(),
                node.category.master() && !node.children.isEmpty(),
                childOfMaster,
                node.hidden
            ));
        }

        boolean descendantOfMaster = childOfMaster || (node.category.master() && !node.children.isEmpty());
        for (Node child : node.children) {
            collectLines(child, depth + 1, targetType, out, includeHidden, descendantOfMaster);
        }
    }

    private BalanceState computeBalanceStateForMonth(
        Node node,
        YearMonth timelineMonth,
        Map<Integer, Map<YearMonth, Double>> budgetsByMonth,
        Map<Integer, Map<YearMonth, Double>> actualsByMonth,
        Map<Integer, Map<YearMonth, Double>> overridesByMonth,
        Map<Integer, Double> previousBalances,
        Map<Integer, Double> currentBalances
    ) {
        double ownBudget = getMonthlyAmount(budgetsByMonth, node.category.id(), timelineMonth);
        double ownActual = getMonthlyAmount(actualsByMonth, node.category.id(), timelineMonth);
        double childActual = 0.0;
        double childBalance = 0.0;

        for (Node child : node.children) {
            BalanceState childState = computeBalanceStateForMonth(
                child,
                timelineMonth,
                budgetsByMonth,
                actualsByMonth,
                overridesByMonth,
                previousBalances,
                currentBalances
            );
            childActual += childState.actual();
            childBalance += childState.balance();
        }

        double previousBalance = previousBalances.getOrDefault(node.category.id(), 0.0);
        Double override = getMonthlyOverride(overridesByMonth, node.category.id(), timelineMonth);
        double currentBalance;
        if (node.category.master() && !node.children.isEmpty()) {
            currentBalance = override != null
                ? ownBudget + override - childActual
                : previousBalance + ownBudget - childActual;
        } else if (!node.children.isEmpty()) {
            currentBalance = childBalance;
        } else if (override != null) {
            currentBalance = override;
        } else {
            currentBalance = previousBalance + ownBudget - ownActual;
        }

        currentBalances.put(node.category.id(), currentBalance);
        return new BalanceState(ownActual + childActual, currentBalance);
    }

    private double calculateChildActualTotal(YearMonth month, int categoryId) {
        List<CategoryRecord> categories = repository.getAllCategories();
        Map<Integer, List<Integer>> childrenByParentId = new HashMap<>();
        for (CategoryRecord category : categories) {
            if (category.parentId() != null) {
                childrenByParentId.computeIfAbsent(category.parentId(), ignored -> new ArrayList<>()).add(category.id());
            }
        }

        Map<Integer, Double> actuals = repository.getMonthlyActuals(month);
        double childActualTotal = 0.0;
        for (Integer childId : collectSubtreeCategoryIds(categoryId, childrenByParentId)) {
            if (childId != categoryId) {
                childActualTotal += actuals.getOrDefault(childId, 0.0);
            }
        }
        return childActualTotal;
    }

    private void collectTimelineMonths(Map<Integer, Map<YearMonth, Double>> amountsByCategory, Set<YearMonth> timelineMonths) {
        for (Map<YearMonth, Double> amounts : amountsByCategory.values()) {
            timelineMonths.addAll(amounts.keySet());
        }
    }

    private double getMonthlyAmount(Map<Integer, Map<YearMonth, Double>> amountsByCategory, int categoryId, YearMonth month) {
        Map<YearMonth, Double> amounts = amountsByCategory.get(categoryId);
        if (amounts == null) {
            return 0.0;
        }
        return amounts.getOrDefault(month, 0.0);
    }

    private Double getMonthlyOverride(Map<Integer, Map<YearMonth, Double>> overridesByCategory, int categoryId, YearMonth month) {
        Map<YearMonth, Double> overrides = overridesByCategory.get(categoryId);
        if (overrides == null || !overrides.containsKey(month)) {
            return null;
        }
        return overrides.get(month);
    }

    private static class Node {
        private final CategoryRecord category;
        private final CategoryType type;
        private final boolean hidden;
        private final List<Node> children = new ArrayList<>();
        private double actual;
        private double budget;
        private double difference;
        private double balance;

        private Node(CategoryRecord category, CategoryType type, boolean hidden) {
            this.category = category;
            this.type = type;
            this.hidden = hidden;
        }
    }

    private record Totals(double actual, double budget, double balance) {
    }

    private record BalanceState(double actual, double balance) {
    }
}
