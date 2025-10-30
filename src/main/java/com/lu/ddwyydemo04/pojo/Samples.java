package com.lu.ddwyydemo04.pojo;

import java.time.LocalDateTime;

public class Samples {
    private Integer sample_id;
    private String sample_model;
    private String sample_coding;

    private String full_model;
    private String sample_name;
    private String sample_category;
    private String chip_control;
    private String version_software;
    private String version_hardware;
    private String version;
    private String test_number;
    private String supplier;
    private String big_species;  //产品大类：如蓝牙类，视频类，音视频类等
    private String small_species; //产品小类：如翻页笔，摇控器等品类细分的
    private LocalDateTime create_time;
    private LocalDateTime finish_time;
    private String result_judge;
//    private Integer signed;
    private String test_Overseas;
    private String sample_schedule;
    private String sample_DQE;
    private String sample_Developer;
    private String tester;
    private String tester_teamwork; //共同测试人

    private String planfinish_time;
    private String filepath;

    private String danger;

    private String sample_leader;

    private String priority; //优先级
    private String sample_remark; //优先级

    private int sample_frequency; //送样次数

    private int sample_quantity;//送样数量

    private String high_frequency;//是否高频：是/否

    private String questStats; //任务属性
    private double testDuration; //实际测试时长
    private double planTestDuration; //预计测试时长

    public Integer getSample_id() {
        return sample_id;
    }

    public void setSample_id(Integer sample_id) {
        this.sample_id = sample_id;
    }

    public String getSample_model() {
        return sample_model;
    }

    public void setSample_model(String sample_model) {
        this.sample_model = sample_model;
    }

    public String getSample_coding() {
        return sample_coding;
    }

    public void setSample_coding(String sample_coding) {
        this.sample_coding = sample_coding;
    }

    public String getSample_name() {
        return sample_name;
    }

    public void setSample_name(String sample_name) {
        this.sample_name = sample_name;
    }

    public String getSample_category() {
        return sample_category;
    }

    public void setSample_category(String sample_category) {
        this.sample_category = sample_category;
    }

    public String getChip_control() {
        return chip_control;
    }

    public void setChip_control(String chip_control) {
        this.chip_control = chip_control;
    }

    public String getVersion_software() {
        return version_software;
    }

    public void setVersion_software(String version_software) {
        this.version_software = version_software;
    }

    public String getVersion_hardware() {
        return version_hardware;
    }

    public void setVersion_hardware(String version_hardware) {
        this.version_hardware = version_hardware;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTest_number() {
        return test_number;
    }

    public void setTest_number(String test_number) {
        this.test_number = test_number;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }



    public LocalDateTime getCreate_time() {
        return create_time;
    }

    public void setCreate_time(LocalDateTime create_time) {
        this.create_time = create_time;
    }

    public LocalDateTime getFinish_time() {
        return finish_time;
    }

    public void setFinish_time(LocalDateTime finish_time) {
        this.finish_time = finish_time;
    }

    public String getResult_judge() {
        return result_judge;
    }

    public void setResult_judge(String result_judge) {
        this.result_judge = result_judge;
    }

//    public Integer getSigned() {
//        return signed;
//    }

//    public void setSigned(Integer signed) {
//        this.signed = signed;
//    }

    public String getTest_Overseas() {
        return test_Overseas;
    }

    public void setTest_Overseas(String test_Overseas) {
        this.test_Overseas = test_Overseas;
    }

    public String getSample_schedule() {
        return sample_schedule;
    }

    public void setSample_schedule(String sample_schedule) {
        this.sample_schedule = sample_schedule;
    }

    public String getSample_DQE() {
        return sample_DQE;
    }

    public void setSample_DQE(String sample_DQE) {
        this.sample_DQE = sample_DQE;
    }

    public String getSample_Developer() {
        return sample_Developer;
    }

    public void setSample_Developer(String sample_Developer) {
        this.sample_Developer = sample_Developer;
    }

    public String getTester() {
        return tester;
    }

    public void setTester(String tester) {
        this.tester = tester;
    }

    public String getPlanfinish_time() {
        return planfinish_time;
    }

    public void setPlanfinish_time(String planfinish_time) {
        this.planfinish_time = planfinish_time;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public String getFull_model() {
        return full_model;
    }

    public void setFull_model(String full_model) {
        this.full_model = full_model;
    }

    public String getTester_teamwork() {
        return tester_teamwork;
    }

    public void setTester_teamwork(String tester_teamwork) {
        this.tester_teamwork = tester_teamwork;
    }

    public String getDanger() {
        return danger;
    }

    public void setDanger(String danger) {
        this.danger = danger;
    }



    public String getSample_leader() {
        return sample_leader;
    }

    public void setSample_leader(String sample_leader) {
        this.sample_leader = sample_leader;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSample_remark() {
        return sample_remark;
    }

    public void setSample_remark(String sample_remark) {
        this.sample_remark = sample_remark;
    }

    public int getSample_frequency() {
        return sample_frequency;
    }

    public void setSample_frequency(int sample_frequency) {
        this.sample_frequency = sample_frequency;
    }

    public int getSample_quantity() {
        return sample_quantity;
    }

    public void setSample_quantity(int sample_quantity) {
        this.sample_quantity = sample_quantity;
    }


    public String getBig_species() {
        return big_species;
    }

    public void setBig_species(String big_species) {
        this.big_species = big_species;
    }

    public String getSmall_species() {
        return small_species;
    }

    public void setSmall_species(String small_species) {
        this.small_species = small_species;
    }

    public String getHigh_frequency() {
        return high_frequency;
    }

    public void setHigh_frequency(String high_frequency) {
        this.high_frequency = high_frequency;
    }

    public String getQuestStats() {
        return questStats;
    }

    public void setQuestStats(String questStats) {
        this.questStats = questStats;
    }

    public double getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(double testDuration) {
        this.testDuration = testDuration;
    }

    public double getPlanTestDuration() {
        return planTestDuration;
    }

    public void setPlanTestDuration(double planTestDuration) {
        this.planTestDuration = planTestDuration;
    }

    @Override
    public String toString() {
        return "Samples{" +
                "sample_id=" + sample_id +
                ", sample_model='" + sample_model + '\'' +
                ", sample_coding='" + sample_coding + '\'' +
                ", full_model='" + full_model + '\'' +
                ", sample_name='" + sample_name + '\'' +
                ", sample_category='" + sample_category + '\'' +
                ", chip_control='" + chip_control + '\'' +
                ", version_software='" + version_software + '\'' +
                ", version_hardware='" + version_hardware + '\'' +
                ", version='" + version + '\'' +
                ", test_number='" + test_number + '\'' +
                ", supplier='" + supplier + '\'' +
                ", create_time=" + create_time +
                ", finish_time=" + finish_time +
                ", result_judge='" + result_judge + '\'' +
//                ", signed=" + signed +
                ", test_Overseas='" + test_Overseas + '\'' +
                ", sample_schedule='" + sample_schedule + '\'' +
                ", sample_DQE='" + sample_DQE + '\'' +
                ", sample_Developer='" + sample_Developer + '\'' +
                ", tester='" + tester + '\'' +
                ", tester_teamwork='" + tester_teamwork + '\'' +
                ", planfinish_time='" + planfinish_time + '\'' +
                ", danger='" + danger + '\'' +
                ", filepath='" + filepath + '\'' +
                ", sample_leader='" + sample_leader + '\'' +
                ", priority='" + priority + '\'' +
                ", sample_remark ='" + sample_remark + '\'' +
                ", sample_frequency ='" + sample_frequency + '\'' +
                ", sample_quantity ='" + sample_quantity + '\'' +
                ", big_species ='" + big_species + '\'' +
                ", small_species ='" + small_species + '\'' +
                ", high_frequency ='" + high_frequency + '\'' +
                ", questStats ='" + questStats + '\'' +
                ", testDuration ='" + testDuration + '\'' +
                ", planTestDuration ='" + planTestDuration + '\'' +
                '}';
    }
}
