package com.lu.ddwyydemo04.pojo;

// 将MergedCellInfo定义为一个独立的公共类或者作为一个静态内部类
public class MergedCellInfo {
    private String sheetName;
    public void setValue(String value) {
        this.value = value;
    }

    public void setRowspan(int rowspan) {
        this.rowspan = rowspan;
    }

    public void setColspan(int colspan) {
        this.colspan = colspan;
    }

    private String value;
    private int rowspan;
    private int colspan;

    private int row; // 单元格所在行

    public int getCol_width() {
        return col_width;
    }

    public void setCol_width(int col_width) {
        this.col_width = col_width;
    }

    private int col_width; //单元格的大致列宽（此数据用来判断前端输入数据的长度）
    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    private String color;

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    private int column; // 单元格所在列

    public MergedCellInfo(String sheetName,String value, int rowspan, int colspan, int row, int column,String color,int col_width) {
        this.sheetName = sheetName;
        this.value = value;
        this.rowspan = rowspan;
        this.colspan = colspan;
        this.row = row;
        this.column = column;
        this.color = color;
        this.col_width = col_width;
    }

    // Getter方法是必须的，以便Jackson可以序列化这些属性
    public String getValue() {
        return value;
    }

    public int getRowspan() {
        return rowspan;
    }

    @Override
    public String toString() {
        return "MergedCellInfo{" +
                "sheetName='" + sheetName + '\'' +
                "value='" + value + '\'' +
                ", rowspan=" + rowspan +
                ", colspan=" + colspan +
                ", row=" + row +
                ", column=" + column +
                ", color=" + color +
                ", col_width=" + col_width +
                '}';
    }

    public int getColspan() {
        return colspan;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }
}
