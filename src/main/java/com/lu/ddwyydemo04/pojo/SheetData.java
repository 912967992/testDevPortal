package com.lu.ddwyydemo04.pojo;

import java.util.ArrayList;
import java.util.List;

public class SheetData {
    private String name;
    private List<List<String>> data;

    public SheetData(String name) {
        this.name = name;
        this.data = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void addRowData(List<String> rowData) {
        this.data.add(rowData);
    }

    public List<List<String>> getData() {
        return data;
    }
}
