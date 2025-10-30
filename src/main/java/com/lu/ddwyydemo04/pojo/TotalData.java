package com.lu.ddwyydemo04.pojo;

public class TotalData {
    public TotalData(String name, int testing, int pending, int completed, int total, int overdue, int danger) {
        this.name = name;
        this.testing = testing;
        this.pending = pending;
        this.completed = completed;
        this.total = total;
        this.overdue = overdue;
        this.danger = danger;
    }

    private String name;
    private int testing;
    private int pending;
    private int completed;
    private int total;
    private int overdue;
    private int danger;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTesting() {
        return testing;
    }

    public void setTesting(int testing) {
        this.testing = testing;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getCompleted() {
        return completed;
    }

    public void setCompleted(int completed) {
        this.completed = completed;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getOverdue() {
        return overdue;
    }

    public void setOverdue(int overdue) {
        this.overdue = overdue;
    }

    public int getDanger() {
        return danger;
    }

    public void setDanger(int danger) {
        this.danger = danger;
    }
}
