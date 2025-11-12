package com.lu.ddwyydemo04.pojo;

import java.time.LocalDateTime;

public class DeviceCommand {
    private Long id;
    private String deviceId;

    // 执行命令字段
    private String valueorprogram; // 定值或程式试验判断：0是程式，1是定值
    private String fixedTempSet; // 定值温度
    private String fixedHumSet; // 定值湿度
    private String setProgramNumber; // 设定运行程式号
    private String setRunStatus; // 定值试验或者程序试验的运行值，0：停止，1：运行，2：暂停
    private String setProgramNo; // 设置程式号

    // 创建信息
    private LocalDateTime createAt; // 创建时间
    private String createBy; // 创建者

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getValueorprogram() {
        return valueorprogram;
    }

    public void setValueorprogram(String valueorprogram) {
        this.valueorprogram = valueorprogram;
    }

    public String getFixedTempSet() {
        return fixedTempSet;
    }

    public void setFixedTempSet(String fixedTempSet) {
        this.fixedTempSet = fixedTempSet;
    }

    public String getFixedHumSet() {
        return fixedHumSet;
    }

    public void setFixedHumSet(String fixedHumSet) {
        this.fixedHumSet = fixedHumSet;
    }

    public String getSetProgramNumber() {
        return setProgramNumber;
    }

    public void setSetProgramNumber(String setProgramNumber) {
        this.setProgramNumber = setProgramNumber;
    }

    public String getSetRunStatus() {
        return setRunStatus;
    }

    public void setSetRunStatus(String setRunStatus) {
        this.setRunStatus = setRunStatus;
    }

    public String getSetProgramNo() {
        return setProgramNo;
    }

    public void setSetProgramNo(String setProgramNo) {
        this.setProgramNo = setProgramNo;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    @Override
    public String toString() {
        return "DeviceCommand{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", valueorprogram='" + valueorprogram + '\'' +
                ", fixedTempSet='" + fixedTempSet + '\'' +
                ", fixedHumSet='" + fixedHumSet + '\'' +
                ", setProgramNumber='" + setProgramNumber + '\'' +
                ", setRunStatus='" + setRunStatus + '\'' +
                ", setProgramNo='" + setProgramNo + '\'' +
                ", createAt=" + createAt +
                ", createBy='" + createBy + '\'' +
                '}';
    }
}

