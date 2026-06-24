package com.newbudget.model;

import java.time.YearMonth;
import java.util.List;

public record MonthSnapshot(
    YearMonth month,
    List<BudgetLine> income,
    List<BudgetLine> mandatory,
    List<BudgetLine> discretionary
) {
}
