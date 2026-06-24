package com.newbudget.model;

public enum CategoryType {
    INCOME,
    MANDATORY,
    DISCRETIONARY;

    public static CategoryType fromDb(String value) {
        return CategoryType.valueOf(value.toUpperCase());
    }
}
