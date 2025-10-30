//package com.lu.ddwyydemo04.controller.reliabilityLab;
//
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.ResponseBody;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Controller
//public class testEnvironment {
//
//    @GetMapping("/passback/receiveData") // 处理页面跳转请求
//    public String loginUsageRate() {
//        // 返回跳转页面的视图名称
//        return "reliablityLab/receiveData";
//    }
//
//    @PostMapping("/receiveData")
//    @ResponseBody
//    public ResponseEntity<Map<String, Object>> receiveData(@RequestBody Map<String, Object> requestData) {
//
//        // 确保所有字段都能接收，即使它们的值为 null
//        String sampleId = (String) requestData.get("sample_id");
//        String sampleCategory = (String) requestData.get("sample_category");
//        String sampleModel = (String) requestData.get("sample_model");
//        String sampleCoding = (String) requestData.get("sample_coding");
//        String materialCode = (String) requestData.get("materialCode");
//        String sampleFrequency = (String) requestData.get("sample_frequency");
//        String sampleName = (String) requestData.get("sample_name");
//        String version = (String) requestData.get("version");
//        String priority = (String) requestData.get("priority");
//        String sampleLeader = (String) requestData.get("sample_leader");
//        String supplier = (String) requestData.get("supplier");
//        String testProjectCategory = (String) requestData.get("testProjectCategory");
//        String testProjects = (String) requestData.get("testProjects");
//        String schedule = (String) requestData.get("schedule");
//
//        // 这里可以添加业务逻辑，例如存储数据到数据库
//        System.out.println("Received data: " + requestData);
//
//        // 构造返回报文
//        Map<String, Object> response = new HashMap<>();
//        response.put("status", "success");
//        response.put("message", "数据接收成功");
//
//        Map<String, String> data = new HashMap<>();
//        data.put("sample_id", sampleId);
//        response.put("data", data);
//
//        return ResponseEntity.ok(response);
//    }
//
//
//
//    @GetMapping("/getReceivedData")
//    public ResponseEntity<List<Map<String, Object>>> getReceivedData() {
//        List<Map<String, Object>> dataList = jdbcTemplate.queryForList("SELECT * FROM test_data");
//        return ResponseEntity.ok(dataList);
//    }
//
//
//}
