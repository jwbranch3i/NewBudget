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

public class BudgetService {
    private final BudgetRepository repository;

    public BudgetService(BudgetRepository repository) {
        this.repository = repository;
    }

    public MonthSnapshot loadMonth(YearMonth month) {
        repository.ensureMonthlyClassificationsFromDefaults(month);

        List<CategoryRecord> categories = repository.getAllCategories();
        Map<Integer, Double> monthlyActuals = repository.getMonthlyActuals(month);
        Map<Integer, Double> monthlyBudgets = repository.getMonthlyBudgets(month);
        Map<Integer, Double> cumulativeDiff = repository.getCumulativeBudgetMinusActual(month);
        Map<Integer, CategoryType> monthlyTypes = repository.getMonthlyClassifications(month);

        Map<Integer, Node> nodesById = new HashMap<>();
        for (CategoryRecord category : categories) {
            CategoryType effectiveType = monthlyTypes.getOrDefault(category.id(), category.defaultType());
            nodesById.put(category.id(), new Node(category, effectiveType));
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
            calculateNode(root, monthlyActuals, monthlyBudgets, cumulativeDiff);
        }

        List<BudgetLine> incomeLines = new ArrayList<>();
        List<BudgetLine> mandatoryLines = new ArrayList<>();
        List<BudgetLine> discretionaryLines = new ArrayList<>();

        for (Node root : roots) {
            collectLines(root, 0, CategoryType.INCOME, incomeLines);
            collectLines(root, 0, CategoryType.MANDATORY, mandatoryLines);
            collectLines(root, 0, CategoryType.DISCRETIONARY, discretionaryLines);
        }

        return new MonthSnapshot(month, incomeLines, mandatoryLines, discretionaryLines);
    }

    public void updateBudget(YearMonth month, int categoryId, double budgetAmount) {
        repository.upsertMonthlyBudget(month, categoryId, budgetAmount);
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
        Map<Integer, Double> cumulativeDiff
    ) {
        double actual = monthlyActuals.getOrDefault(node.category.id(), 0.0);
        double budget = monthlyBudgets.getOrDefault(node.category.id(), 0.0);
        double balance = cumulativeDiff.getOrDefault(node.category.id(), 0.0);

        for (Node child : node.children) {
            Totals childTotals = calculateNode(child, monthlyActuals, monthlyBudgets, cumulativeDiff);
            actual += childTotals.actual;
            budget += childTotals.budget;
            balance += childTotals.balance;
        }

        node.actual = actual;
        node.budget = budget;
        node.difference = budget - actual;
        node.balance = balance;
        return new Totals(actual, budget, balance);
    }

    private void collectLines(Node node, int depth, CategoryType targetType, List<BudgetLine> out) {
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
                node.category.rollup() || !node.children.isEmpty()
            ));
        }

        for (Node child : node.children) {
            collectLines(child, depth + 1, targetType, out);
        }
    }

    private static class Node {
        private final CategoryRecord category;
        private final CategoryType type;
        private final List<Node> children = new ArrayList<>();
        private double actual;
        private double budget;
        private double difference;
        private double balance;

        private Node(CategoryRecord category, CategoryType type) {
            this.category = category;
            this.type = type;
        }
    }

    private record Totals(double actual, double budget, double balance) {
    }
}
