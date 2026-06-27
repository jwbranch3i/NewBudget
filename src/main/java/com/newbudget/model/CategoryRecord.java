package com.newbudget.model;

public record CategoryRecord(
    int id,
    String name,
    String path,
    Integer parentId,
    int sortOrder,
    CategoryType defaultType,
    boolean rollup,
    boolean hidden
) {
}
