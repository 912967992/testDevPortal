package com.lu.ddwyydemo04.controller.DQE;

import com.lu.ddwyydemo04.Service.DQE.DQEproblemMoudleService;
import com.lu.ddwyydemo04.dao.DQE.DQEDao;
import com.lu.ddwyydemo04.pojo.Samples;
import com.lu.ddwyydemo04.pojo.TestIssues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DQEproblemMoudleController {
    @Autowired
    private DQEproblemMoudleService dqeproblemMoudleService;


    @GetMapping("/problemMoudle/searchTestissues") // 处理页面跳转请求
    @ResponseBody
    public List<TestIssues> searchTestissues() {
        List<TestIssues> resultTestissues = dqeproblemMoudleService.searchTestissues();
        System.out.println(resultTestissues);
        return resultTestissues;
    }

    @PostMapping("/problemMoudle/editClickBtn") // 处理页面跳转请求
    @ResponseBody
    public List<TestIssues> editClickBtn(@RequestBody int sampleId) {

        List<TestIssues> selectTestissues = dqeproblemMoudleService.selectTestIssuesFromSampleid(sampleId);
        System.out.println("selectTestissues:"+selectTestissues);
        // 示例: 返回一个简单的响应
        return selectTestissues;
    }

    @GetMapping("/problemMoudle/searchSamplesDQE") // 处理页面跳转请求
    @ResponseBody
    public List<Samples> searchSamplesDQE() {
        List<Samples> resultSamples = dqeproblemMoudleService.searchSamplesDQE();
        System.out.println(resultSamples);
        return resultSamples;
    }

    @GetMapping("/problemMoudle/addNewRow") // 处理页面跳转请求
    @ResponseBody
    public List<Map<String, Object>> addNewRow(@RequestParam int sampleId) {
        // 获取需要的样本数据，直接从 SQL 查询返回的 Map
        List<Map<String, Object>> samples = dqeproblemMoudleService.addNewRow(sampleId);

        // 打印 SQL 语句返回的字段
        for (Map<String, Object> sample : samples) {
            System.out.println("full_model: " + sample.get("full_model"));
            System.out.println("sample_category: " + sample.get("sample_category"));
            System.out.println("version: " + sample.get("version"));
            System.out.println("max_history_id: " + sample.get("max_history_id"));
        }
        System.out.println(samples);

        // 直接返回查询结果（不需要再转换或提取）
        return samples;
    }


    //问题点模块保存问题点修改的方法
    @PostMapping("/problemMoudle/saveAllData")
    @ResponseBody
    public ResponseEntity<String> saveAllData(@RequestBody List<Map<String, Object>> allData) {
        try {
            System.out.println("接收到的数据量: " + allData.size()); // 打印接收到的数据量
            for (Map<String, Object> row : allData) {
                System.out.println("处理行数据: " + row); // 打印每一行的数据
            }

            return ResponseEntity.ok("保存成功"); // 返回成功消息
        } catch (Exception e) {
            e.printStackTrace(); // 打印异常信息
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("保存失败: " + e.getMessage());
        }
    }





}
