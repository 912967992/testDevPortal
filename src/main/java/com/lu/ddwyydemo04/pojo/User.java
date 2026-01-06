package com.lu.ddwyydemo04.pojo;

/**
 * 用户信息实体类
 * 对应数据库 users 表
 */
public class User {
    private Long id;
    private String userId;          // 钉钉用户ID
    private String username;         // 用户名
    private Long deptId;            // 部门ID
    private Long majorDeptId;       // 主部门ID
    private String departmentName;   // 部门名称
    private String position;         // 职位（title）
    private String jobNumber;        // 工号
    private String hireDate;        // 入职日期

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getMajorDeptId() {
        return majorDeptId;
    }

    public void setMajorDeptId(Long majorDeptId) {
        this.majorDeptId = majorDeptId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getJobNumber() {
        return jobNumber;
    }

    public void setJobNumber(String jobNumber) {
        this.jobNumber = jobNumber;
    }

    public String getHireDate() {
        return hireDate;
    }

    public void setHireDate(String hireDate) {
        this.hireDate = hireDate;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", deptId=" + deptId +
                ", majorDeptId=" + majorDeptId +
                ", departmentName='" + departmentName + '\'' +
                ", position='" + position + '\'' +
                ", jobNumber='" + jobNumber + '\'' +
                ", hireDate='" + hireDate + '\'' +
                '}';
    }
}























