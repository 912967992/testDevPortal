package com.lu.ddwyydemo04.pojo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DeviceCommand {
    private Long id;
    private String deviceId;
    
    // 命令参数字段（从command JSON字段拆分）
    private BigDecimal setTemperature;
    private BigDecimal setHumidity;
    private String powerTemperature;
    private String powerHumidity;
    private String runMode;
    private String runStatus;
    private String runHours;
    private String runMinutes;
    private String runSeconds;
    private String setProgramNumber;
    private String setRunStatus;
    private String totalSteps;
    private String runningStep;
    private String stepRemainingHours;
    private String stepRemainingMinutes;
    private String stepRemainingSeconds;
    
    // 保留command字段用于兼容
    private String command;
    
    // 状态和反馈字段
    private String status; // pending, executing, success, failed
    private String feedbackStatusCode;
    private String feedbackMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime executedAt;

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

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFeedbackStatusCode() {
        return feedbackStatusCode;
    }

    public void setFeedbackStatusCode(String feedbackStatusCode) {
        this.feedbackStatusCode = feedbackStatusCode;
    }

    public String getFeedbackMessage() {
        return feedbackMessage;
    }

    public void setFeedbackMessage(String feedbackMessage) {
        this.feedbackMessage = feedbackMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    // 命令参数字段的getter和setter
    public BigDecimal getSetTemperature() {
        return setTemperature;
    }

    public void setSetTemperature(BigDecimal setTemperature) {
        this.setTemperature = setTemperature;
    }

    public BigDecimal getSetHumidity() {
        return setHumidity;
    }

    public void setSetHumidity(BigDecimal setHumidity) {
        this.setHumidity = setHumidity;
    }

    public String getPowerTemperature() {
        return powerTemperature;
    }

    public void setPowerTemperature(String powerTemperature) {
        this.powerTemperature = powerTemperature;
    }

    public String getPowerHumidity() {
        return powerHumidity;
    }

    public void setPowerHumidity(String powerHumidity) {
        this.powerHumidity = powerHumidity;
    }

    public String getRunMode() {
        return runMode;
    }

    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }

    public String getRunStatus() {
        return runStatus;
    }

    public void setRunStatus(String runStatus) {
        this.runStatus = runStatus;
    }

    public String getRunHours() {
        return runHours;
    }

    public void setRunHours(String runHours) {
        this.runHours = runHours;
    }

    public String getRunMinutes() {
        return runMinutes;
    }

    public void setRunMinutes(String runMinutes) {
        this.runMinutes = runMinutes;
    }

    public String getRunSeconds() {
        return runSeconds;
    }

    public void setRunSeconds(String runSeconds) {
        this.runSeconds = runSeconds;
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

    public String getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(String totalSteps) {
        this.totalSteps = totalSteps;
    }

    public String getRunningStep() {
        return runningStep;
    }

    public void setRunningStep(String runningStep) {
        this.runningStep = runningStep;
    }

    public String getStepRemainingHours() {
        return stepRemainingHours;
    }

    public void setStepRemainingHours(String stepRemainingHours) {
        this.stepRemainingHours = stepRemainingHours;
    }

    public String getStepRemainingMinutes() {
        return stepRemainingMinutes;
    }

    public void setStepRemainingMinutes(String stepRemainingMinutes) {
        this.stepRemainingMinutes = stepRemainingMinutes;
    }

    public String getStepRemainingSeconds() {
        return stepRemainingSeconds;
    }

    public void setStepRemainingSeconds(String stepRemainingSeconds) {
        this.stepRemainingSeconds = stepRemainingSeconds;
    }

    @Override
    public String toString() {
        return "DeviceCommand{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", setTemperature=" + setTemperature +
                ", setHumidity=" + setHumidity +
                ", powerTemperature='" + powerTemperature + '\'' +
                ", powerHumidity='" + powerHumidity + '\'' +
                ", runMode='" + runMode + '\'' +
                ", runStatus='" + runStatus + '\'' +
                ", command='" + command + '\'' +
                ", status='" + status + '\'' +
                ", feedbackStatusCode='" + feedbackStatusCode + '\'' +
                ", feedbackMessage='" + feedbackMessage + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", executedAt=" + executedAt +
                '}';
    }
}

