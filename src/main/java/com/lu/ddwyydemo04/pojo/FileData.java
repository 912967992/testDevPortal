package com.lu.ddwyydemo04.pojo;

public class FileData {
    private String name;
    private String type;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FileData(String name, String type) {
        this.name = name;
        this.type = type;
    }

}
