package com.mysqllab;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    public boolean isResultSet;
    public long affectedRows;
    public long lastInsertId;
    public String message = "";
    public List<String> columnNames = new ArrayList<>();
    public List<List<String>> rows = new ArrayList<>();
    public long executionTimeMs;
    public String errorMessage;
    public int errorCode;

    public boolean isSuccess() {
        return errorMessage == null;
    }
}
