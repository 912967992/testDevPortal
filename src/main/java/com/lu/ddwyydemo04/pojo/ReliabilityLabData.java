package com.lu.ddwyydemo04.pojo;

import java.math.BigDecimal;

public class ReliabilityLabData {
    private Long id;
    private String deviceId;
    private BigDecimal temperature;
    private BigDecimal humidity;
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
    private String rawPayload;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public BigDecimal getHumidity() { return humidity; }
    public void setHumidity(BigDecimal humidity) { this.humidity = humidity; }
    public BigDecimal getSetTemperature() { return setTemperature; }
    public void setSetTemperature(BigDecimal setTemperature) { this.setTemperature = setTemperature; }
    public BigDecimal getSetHumidity() { return setHumidity; }
    public void setSetHumidity(BigDecimal setHumidity) { this.setHumidity = setHumidity; }
    public String getPowerTemperature() { return powerTemperature; }
    public void setPowerTemperature(String powerTemperature) { this.powerTemperature = powerTemperature; }
    public String getPowerHumidity() { return powerHumidity; }
    public void setPowerHumidity(String powerHumidity) { this.powerHumidity = powerHumidity; }
    public String getRunMode() { return runMode; }
    public void setRunMode(String runMode) { this.runMode = runMode; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    public String getRunHours() { return runHours; }
    public void setRunHours(String runHours) { this.runHours = runHours; }
    public String getRunMinutes() { return runMinutes; }
    public void setRunMinutes(String runMinutes) { this.runMinutes = runMinutes; }
    public String getRunSeconds() { return runSeconds; }
    public void setRunSeconds(String runSeconds) { this.runSeconds = runSeconds; }
    public String getSetProgramNumber() { return setProgramNumber; }
    public void setSetProgramNumber(String setProgramNumber) { this.setProgramNumber = setProgramNumber; }
    public String getSetRunStatus() { return setRunStatus; }
    public void setSetRunStatus(String setRunStatus) { this.setRunStatus = setRunStatus; }
    public String getTotalSteps() { return totalSteps; }
    public void setTotalSteps(String totalSteps) { this.totalSteps = totalSteps; }
    public String getRunningStep() { return runningStep; }
    public void setRunningStep(String runningStep) { this.runningStep = runningStep; }
    public String getStepRemainingHours() { return stepRemainingHours; }
    public void setStepRemainingHours(String stepRemainingHours) { this.stepRemainingHours = stepRemainingHours; }
    public String getStepRemainingMinutes() { return stepRemainingMinutes; }
    public void setStepRemainingMinutes(String stepRemainingMinutes) { this.stepRemainingMinutes = stepRemainingMinutes; }
    public String getStepRemainingSeconds() { return stepRemainingSeconds; }
    public void setStepRemainingSeconds(String stepRemainingSeconds) { this.stepRemainingSeconds = stepRemainingSeconds; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    @Override
    public String toString() {
        return "ReliabilityLabData{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", setTemperature=" + setTemperature +
                ", setHumidity=" + setHumidity +
                ", powerTemperature='" + powerTemperature + '\'' +
                ", powerHumidity='" + powerHumidity + '\'' +
                ", runMode='" + runMode + '\'' +
                ", runStatus='" + runStatus + '\'' +
                ", runHours='" + runHours + '\'' +
                ", runMinutes='" + runMinutes + '\'' +
                ", runSeconds='" + runSeconds + '\'' +
                ", setProgramNumber='" + setProgramNumber + '\'' +
                ", setRunStatus='" + setRunStatus + '\'' +
                ", totalSteps='" + totalSteps + '\'' +
                ", runningStep='" + runningStep + '\'' +
                ", stepRemainingHours='" + stepRemainingHours + '\'' +
                ", stepRemainingMinutes='" + stepRemainingMinutes + '\'' +
                ", stepRemainingSeconds='" + stepRemainingSeconds + '\'' +
                ", rawPayload='" + rawPayload + '\'' +
                '}';
    }
}








