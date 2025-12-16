package com.lu.ddwyydemo04.pojo;

import java.time.LocalDateTime;

/**
 * 设备信息实体类
 * 用于存储设备的品类、型号、测试人员等扩展信息
 */
public class DeviceInfo {
    // 状态常量
    public static final String STATUS_WAITING = "WAITING";    // 预约等候
    public static final String STATUS_TESTING = "TESTING";    // 测试中
    public static final String STATUS_COMPLETED = "COMPLETED"; // 测试完成
    public static final String STATUS_CANCELLED = "CANCELLED"; // 已取消
    
    // 测试结果常量
    public static final String TEST_RESULT_PASS = "PASS";        // 通过
    public static final String TEST_RESULT_FAIL = "FAIL";        // 失败
    public static final String TEST_RESULT_PARTIAL_OK = "PARTIAL_OK"; // 部分OK
    public static final String TEST_RESULT_FINISHED = "Finished"; // 已完成
    
    private Long id;
    private String deviceId; // 设备ID，关联到设备
    private String category; // 品类
    private String model; // 型号
    private String tester; // 测试人员
    private String status; // 状态：WAITING(预约等候)、TESTING(测试中)、COMPLETED(测试完成)、CANCELLED(已取消)
    private String testResult; // 测试结果：PASS(通过)、FAIL(失败)、PARTIAL_OK(部分OK)、Finished(已完成)
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTester() {
        return tester;
    }

    public void setTester(String tester) {
        this.tester = tester;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTestResult() {
        return testResult;
    }

    public void setTestResult(String testResult) {
        this.testResult = testResult;
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

    @Override
    public String toString() {
        return "DeviceInfo{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", category='" + category + '\'' +
                ", model='" + model + '\'' +
                ", tester='" + tester + '\'' +
                ", status='" + status + '\'' +
                ", testResult='" + testResult + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}













