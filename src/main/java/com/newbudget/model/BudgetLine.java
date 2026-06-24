package com.newbudget.model;

public record BudgetLine(
    int categoryId,
    String category,
    int depth,
    double actualAmount,
    double budgetAmount,
    double difference,
    double balance,
    CategoryType type,
    boolean rollup
) {
}
