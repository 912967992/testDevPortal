package com.lu.ddwyydemo04.pojo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class QuestData {
    private String test_schedule;

    private String test_number;
    private String project_manager;
    private String supplier;
    private String product_category;
    private String product_model;
    private String product_name;
    private int quantity;
    private String sample_type;
    private Date due_date;
    private String sender;
    private Date send_date;
    private String send_cycle;
    private String send_remark;
    private String test_purpose;

    public QuestData(String test_number) {
        test_number = test_number;
    }

    public String getTest_schedule() {
        return test_schedule;
    }

    public void setTest_schedule(String test_schedule) {
        this.test_schedule = test_schedule;
    }


    public String getTestnumber() {
        return test_number;
    }

    public void setTestNumber(String test_number) {
        this.test_number = test_number;
    }

    public String getProject_manager() {
        return project_manager;
    }

    public void setProject_manager(String project_manager) {
        this.project_manager = project_manager;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getProduct_category() {
        return product_category;
    }

    public void setProduct_category(String product_category) {
        this.product_category = product_category;
    }

    public String getProduct_model() {
        return product_model;
    }

    public void setProduct_model(String product_model) {
        this.product_model = product_model;
    }

    public String getProduct_name() {
        return product_name;
    }

    public void setProduct_name(String product_name) {
        this.product_name = product_name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getSample_type() {
        return sample_type;
    }

    public void setSample_type(String sample_type) {
        this.sample_type = sample_type;
    }

    public Date getDue_date() {
        return due_date;
    }

    public void setDue_date(Date due_date) {
        this.due_date = due_date;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public Date getSend_date() {
        return send_date;
    }

    public void setSend_date(Date send_date) {
        this.send_date = send_date;
    }

    public String getSend_cycle() {
        return send_cycle;
    }

    public void setSend_cycle(String send_cycle) {
        this.send_cycle = send_cycle;
    }

    public String getSend_remark() {
        return send_remark;
    }

    public void setSend_remark(String send_remark) {
        this.send_remark = send_remark;
    }

    public String getTest_purpose() {
        return test_purpose;
    }

    public void setTest_purpose(String test_purpose) {
        this.test_purpose = test_purpose;
    }

    // 获取格式化后的日期字符串
    private String formatDate(Date date) {
        if (date != null) {
            LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        return null;
    }

    // 获取格式化后的 send_date 日期字符串
    public String getFormattedSendDate() {
        return formatDate(send_date);
    }

    // 获取格式化后的 due_date 日期字符串
    public String getFormattedDueDate() {
        return formatDate(due_date);
    }

}
