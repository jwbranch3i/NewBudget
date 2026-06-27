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
        Map<Integer, Double> effectiveBalances = computeEffectiveBalances(month);
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
            collectLines(root, 0, CategoryType.INCOME, incomeLines, includeHidden);
            collectLines(root, 0, CategoryType.MANDATORY, mandatoryLines, includeHidden);
            collectLines(root, 0, CategoryType.DISCRETIONARY, discretionaryLines, includeHidden);
        }

        return new MonthSnapshot(month, incomeLines, mandatoryLines, discretionaryLines);
    }

    public void updateBudget(YearMonth month, int categoryId, double budgetAmount) {
        repository.upsertMonthlyBudget(month, categoryId, budgetAmount);
    }

    public void updateBalance(YearMonth month, int categoryId, double balanceAmount) {
        repository.upsertMonthlyBalanceOverride(month, categoryId, balanceAmount);
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
        double budget = monthlyBudgets.getOrDefault(node.category.id(), 0.0);
        double ownBalance = effectiveBalances.getOrDefault(node.category.id(), 0.0);
        double childBalance = 0.0;

        for (Node child : node.children) {
            Totals childTotals = calculateNode(child, monthlyActuals, monthlyBudgets, effectiveBalances, includeHidden);
            actual += childTotals.actual;
            budget += childTotals.budget;
            childBalance += childTotals.balance;
        }

        double balance = node.children.isEmpty() ? ownBalance : childBalance;

        node.actual = actual;
        node.budget = budget;
        node.difference = budget - actual;
        node.balance = balance;
        return new Totals(actual, budget, balance);
    }

    private Map<Integer, Double> computeEffectiveBalances(YearMonth month) {
        Map<Integer, Map<YearMonth, Double>> budgetsByMonth = repository.getMonthlyBudgetAmountsUpTo(month);
        Map<Integer, Map<YearMonth, Double>> actualsByMonth = repository.getMonthlyActualAmountsUpTo(month);
        Map<Integer, Map<YearMonth, Double>> overridesByMonth = repository.getMonthlyBalanceOverridesUpTo(month);

        TreeSet<Integer> categoryIds = new TreeSet<>();
        categoryIds.addAll(budgetsByMonth.keySet());
        categoryIds.addAll(actualsByMonth.keySet());
        categoryIds.addAll(overridesByMonth.keySet());

        Map<Integer, Double> balances = new HashMap<>();
        for (Integer categoryId : categoryIds) {
            NavigableMap<YearMonth, Double> diffsByMonth = new java.util.TreeMap<>();
            mergeMonthlyAmounts(diffsByMonth, budgetsByMonth.get(categoryId), 1.0);
            mergeMonthlyAmounts(diffsByMonth, actualsByMonth.get(categoryId), -1.0);

            NavigableMap<YearMonth, Double> overrides = toNavigableMap(overridesByMonth.get(categoryId));

            java.util.TreeSet<YearMonth> timelineMonths = new java.util.TreeSet<>();
            timelineMonths.addAll(diffsByMonth.keySet());
            timelineMonths.addAll(overrides.keySet());

            double runningBalance = 0.0;
            for (YearMonth timelineMonth : timelineMonths) {
                if (overrides.containsKey(timelineMonth)) {
                    runningBalance = overrides.get(timelineMonth);
                } else {
                    runningBalance += diffsByMonth.getOrDefault(timelineMonth, 0.0);
                }
            }

            balances.put(categoryId, runningBalance);
        }

        return balances;
    }

    private void mergeMonthlyAmounts(
        NavigableMap<YearMonth, Double> target,
        Map<YearMonth, Double> amounts,
        double factor
    ) {
        if (amounts == null || amounts.isEmpty()) {
            return;
        }

        for (Map.Entry<YearMonth, Double> entry : amounts.entrySet()) {
            target.merge(entry.getKey(), entry.getValue() * factor, Double::sum);
        }
    }

    private NavigableMap<YearMonth, Double> toNavigableMap(Map<YearMonth, Double> values) {
        if (values == null || values.isEmpty()) {
            return new java.util.TreeMap<>();
        }
        return new java.util.TreeMap<>(values);
    }

    private void collectLines(Node node, int depth, CategoryType targetType, List<BudgetLine> out, boolean includeHidden) {
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
                node.hidden
            ));
        }

        for (Node child : node.children) {
            collectLines(child, depth + 1, targetType, out, includeHidden);
        }
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
}
