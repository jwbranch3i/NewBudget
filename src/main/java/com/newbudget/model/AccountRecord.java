package com.newbudget.model;

public record AccountRecord(
    int id,
    String name
) {
    @Override
    public String toString() {
        return name;
    }
}
