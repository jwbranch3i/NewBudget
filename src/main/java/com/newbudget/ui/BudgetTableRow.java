package com.newbudget.ui;

import com.newbudget.model.BudgetLine;
import com.newbudget.model.CategoryType;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class BudgetTableRow {
    private final IntegerProperty categoryId;
    private final StringProperty category;
    private final IntegerProperty depth;
    private final DoubleProperty actualAmount;
    private final DoubleProperty budgetAmount;
    private final DoubleProperty difference;
    private final DoubleProperty balance;
    private final CategoryType type;
    private final boolean rollup;

    public BudgetTableRow(BudgetLine line) {
        this.categoryId = new SimpleIntegerProperty(line.categoryId());
        this.category = new SimpleStringProperty(line.category());
        this.depth = new SimpleIntegerProperty(line.depth());
        this.actualAmount = new SimpleDoubleProperty(line.actualAmount());
        this.budgetAmount = new SimpleDoubleProperty(line.budgetAmount());
        this.difference = new SimpleDoubleProperty(line.difference());
        this.balance = new SimpleDoubleProperty(line.balance());
        this.type = line.type();
        this.rollup = line.rollup();
    }

    public int getCategoryId() {
        return categoryId.get();
    }

    public String getCategory() {
        return category.get();
    }

    public int getDepth() {
        return depth.get();
    }

    public double getActualAmount() {
        return actualAmount.get();
    }

    public double getBudgetAmount() {
        return budgetAmount.get();
    }

    public void setBudgetAmount(double value) {
        budgetAmount.set(value);
    }

    public double getDifference() {
        return difference.get();
    }

    public double getBalance() {
        return balance.get();
    }

    public CategoryType getType() {
        return type;
    }

    public boolean isRollup() {
        return rollup;
    }

    public DoubleProperty budgetAmountProperty() {
        return budgetAmount;
    }
}
