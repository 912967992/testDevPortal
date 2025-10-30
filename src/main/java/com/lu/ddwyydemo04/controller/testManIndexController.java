package com.lu.ddwyydemo04.controller;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.*;
import com.dingtalk.api.response.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lu.ddwyydemo04.Service.AccessTokenService;
import com.lu.ddwyydemo04.Service.TestManIndexService;
import com.lu.ddwyydemo04.exceptions.SessionTimeoutException;
import com.lu.ddwyydemo04.pojo.FileData;

import com.lu.ddwyydemo04.pojo.Samples;
import com.lu.ddwyydemo04.pojo.TestIssues;
import com.lu.ddwyydemo04.pojo.TotalData;
import com.taobao.api.ApiException;
import com.taobao.api.FileItem;
import com.taobao.api.internal.util.WebUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.*;

import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class testManIndexController {
    @Autowired
    private TestManIndexService testManIndexService;

    @Autowired
    private AccessTokenService accessTokenService;

    @Value("${dingtalk.agentid}")
    private String agentid;

    @Value("${file.storage.templatespath}")
    private String templatesPath;

    @Value("${file.storage.savepath}")
    private String savepath;

    @Value("${file.storage.jsonpath}")
    private String jsonpath;

    private static final Logger logger = LoggerFactory.getLogger(testManIndexController.class);

    // 上班时间和下班时间定义
    private static final LocalTime MORNING_START = LocalTime.of(9, 0);
    private static final LocalTime MORNING_END = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 30);
    private static final LocalTime AFTERNOON_END = LocalTime.of(18, 30);
    private static final double WORK_HOURS_PER_DAY = 8.5;


    @GetMapping("/testManIndex") // 处理页面跳转请求
    public String loginTestManIndex() {
        // 返回跳转页面的视图名称
        return "testManIndex";
    }




    @GetMapping("/data") // 定义一个GET请求的接口，路径为 /data
    @ResponseBody
    public Map<String, Integer> getData(@RequestParam(required = false) String username) {
        // 创建一个Map对象，存储测试中、待审核、已完成、总数、逾期和失责的数量
        Map<String, Integer> data = new HashMap<>();
        if(username == null){
            logger.info("searchSamples用户信息不存在");
            // 会话超时，抛出自定义异常
            throw new SessionTimeoutException("会话已超时，请重新登录。");
        }else{
            //如果未创建过报告文件，则需要在这里创建用户进total表，才有数据展示
            int countTotal = testManIndexService.queryCountTotal(username);
            if(countTotal==0){
                TotalData totalData = new TotalData(username,0,0,0,0,0,0);
                testManIndexService.createTotal(totalData);
                logger.info("createTotal创建用户进total成功");

            }
            // 更新 total 表中的数据
            testManIndexService.updateTotal(username);
        }
        //获取用户的total信息并返回到前端展示
        Map<String, Integer> data1 = testManIndexService.getindexPanel(username);
        System.out.println(data1);

        data.put("testing", data1.get("testing")); // 测试中数量
        data.put("pending", data1.get("pending")); // 待审核1数量
        data.put("completed", data1.get("completed")); // 已完成数量
        data.put("total", data1.get("total")); // 总数
        data.put("overdue", data1.get("overdue")); // 逾期数量
        data.put("danger", data1.get("danger")); // 失责数量
        return data;
    }


    @GetMapping("/home/getFileCategories")
    @ResponseBody
    public List<String> getCategories() {
        File directory = new File(templatesPath);
        File[] files = directory.listFiles(File::isDirectory);
        System.out.println(templatesPath);

        List<String> categories = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                categories.add(file.getName());
            }
        }
        System.out.println(categories);
        return categories;
    }

    @GetMapping("/home/getFiles")
    @ResponseBody
    public List<FileData> getFiles(@RequestParam String category) {
        System.out.println("category:"+category);
        List<FileData> fileList = new ArrayList<>();

        // 拼接完整的文件夹路径
        String directoryPath = templatesPath + "/" + category;

        // 获取文件夹下的所有文件和文件夹
        File directory = new File(directoryPath);
        File[] files = directory.listFiles();

        // 遍历文件夹下的所有文件和文件夹，并将它们添加到 fileList 中
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() || file.getName().endsWith(".xlsx")) {
                    fileList.add(new FileData(file.getName(), file.isDirectory() ? "directory" : "file"));
                }
            }
        }
        // 遍历并打印 fileList 中每个元素的文件名
        for (FileData data : fileList) {
            System.out.println("文件名: " + data.getName());
        }
        return fileList;
    }

    @GetMapping("/getTestManPanel")
    @ResponseBody
    public List<Samples> getTestManPanel(@RequestParam(required = false) String username){
        // 检查 username 参数是否传递
        if (username == null || username.isEmpty()) {
            // 处理用户信息不存在的情况，比如返回一个错误响应或重定向到登录页面
            return Collections.emptyList(); // 或者返回适当的错误响应
        } else {
            // 使用 username 查询数据
            logger.info("getTestManPanel查询成功："+testManIndexService.getTestManPanel(username));

            return testManIndexService.getTestManPanel(username);

        }

    }

    @PostMapping("/searchSamples")
    @ResponseBody
    public List<Samples> searchSamples(@RequestBody Map<String, Object> data){
        // 从会话中获取用户信息
        String username = (String) data.get("username");
        if (username == null) {
            // 处理用户信息不存在的情况，比如返回一个错误响应或重定向到登录页面
            logger.info("searchSamples用户信息不存在");
            // 会话超时，抛出自定义异常
            throw new SessionTimeoutException("会话已超时，请重新登录。");
        }

        String keyword = (String) data.get("keyword");
        String sort = (String) data.get("sortFilter");

        if(sort.equals("asc")){
            return testManIndexService.searchSamplesByAsc(username,keyword);
        }else if (sort.equals("desc")){
            return testManIndexService.searchSamplesByDesc(username,keyword);
        }else{
//            return testManIndexService.searchSamples(username,keyword);
            return testManIndexService.searchSamplesByDesc(username,keyword);
        }

    }

    @PostMapping("/testManIndex/getjumpFilepath")
    @ResponseBody
    public String getjumpFilepath(@RequestBody Map<String, Object> data){
        String model = (String) data.get("model");
        String coding = (String) data.get("coding");
        String category = (String) data.get("category");
        String version = (String) data.get("version");
        String big_species = (String) data.get("big_species");
        String small_species = (String) data.get("small_species");
        String high_frequency = (String) data.get("high_frequency");
        String questStats = (String) data.get("questStats");
        String sample_frequencyStr = (String) data.get("sample_frequency");
        Samples sample = new Samples();
        sample.setSample_model(model);
        sample.setSample_coding(coding);
        sample.setSample_category(category);
        sample.setVersion(version);
        sample.setBig_species(big_species);
        sample.setSmall_species(small_species);
        sample.setHigh_frequency(high_frequency);
        sample.setQuestStats(questStats);

        int sample_frequency =  Integer.parseInt(sample_frequencyStr.trim());
        sample.setSample_frequency(sample_frequency);

        return testManIndexService.queryFilepath(sample);
    }

    //替换报告
    @PostMapping("/testManIndex/replaceFilepath")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> replaceFile(@RequestParam("file") MultipartFile file,
                                         @RequestParam("filepath") String filePath) {
        Map<String, Object> response = new HashMap<>();
        System.out.println("filePath:"+filePath);
        try {
            // 创建目标文件对象
            File targetFile = new File(filePath);

            // 检查目标文件是否存在，如果存在则删除
            if (targetFile.exists()) {
                if (!targetFile.delete()) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }

            // 将上传的文件保存到目标路径
            file.transferTo(targetFile);

            String fileName = targetFile.getName();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String jsonFilePath = jsonpath+ File.separator + baseName + ".json";
            System.out.println("json文件是："+jsonFilePath);

            try {
                File jsonFile = new File(jsonFilePath);
                if (jsonFile.exists()) {
                    if (jsonFile.delete()) {
                        logger.info("删除json文件了: " + jsonFilePath);
                    } else {
                        logger.error("删除json文件失败 " + jsonFilePath);
                    }
                } else {
                    logger.info("json文件没有找到 " + jsonFilePath);
                }
            } catch (Exception e) {
                logger.error("在替换报告进行到删除json文件的时候出错：.", e);
            }

            // 添加旧文件路径到响应中
            response.put("oldFilePath", filePath);
            response.put("message", "文件替换成功");
            logger.error("成功替换文件:"+filePath);
            // 返回成功响应
            return ResponseEntity.ok().body(response);

        } catch (IOException e) {
            // 捕获IO异常并返回错误响应
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    @PostMapping("/testManIndex/updateSamples")
    @ResponseBody
    public Map<String, Object> updateSamples(
            @RequestParam("edit_sample_id") int sample_id,
            @RequestParam("edit_model") String editModel,
            @RequestParam("edit_coding") String editCoding,
            @RequestParam("edit_category") String editCategory,
            @RequestParam("edit_questStats") String questStats,
            @RequestParam("edit_big_species") String big_species,
            @RequestParam("edit_small_species") String small_species,
            @RequestParam("edit_high_frequency") String high_frequency,
            @RequestParam("edit_sample_frequency") String editsample_frequency,

            @RequestParam("edit_version") String editVersion,
            @RequestParam("edit_sample_name") String editSampleName,
            @RequestParam("edit_planfinish_time") String editPlanfinishTime,
            @RequestParam("edit_chip_control") String editChipControl,
            @RequestParam("edit_version_software") String editVersionSoftware,
            @RequestParam("edit_version_hardware") String editVersionHardware,
            @RequestParam("edit_supplier") String editsupplier,
            @RequestParam("edit_test_Overseas") String editTestOverseas,
            @RequestParam("edit_priority") String editpriority,
            @RequestParam("edit_sample_remark") String editsample_remark,
            @RequestParam("edit_sample_DQE") String editSampleDQE,
            @RequestParam("edit_sample_Developer") String editSampleDeveloper,
            @RequestParam("edit_sample_leader") String editSampleleader,
            @RequestParam("tester") String tester
            ) {
        Map<String, Object> response = new HashMap<>();
        try {

            // 调用服务类的方法来更新样品信息
            Samples sample = new Samples();
            sample.setSample_id(sample_id);
            sample.setSample_model(editModel);
            sample.setSample_coding(editCoding);
            sample.setSample_category(editCategory);
            sample.setVersion(editVersion);
            sample.setSample_name(editSampleName);
            sample.setPlanfinish_time(editPlanfinishTime);
            sample.setChip_control(editChipControl);
            sample.setVersion_software(editVersionSoftware);
            sample.setVersion_hardware(editVersionHardware);
            sample.setSupplier(editsupplier);
            sample.setTest_Overseas(editTestOverseas); // 确保参数名一致
            sample.setPriority(editpriority);
            sample.setSample_remark(editsample_remark);
            sample.setSample_DQE(editSampleDQE);
            sample.setSample_Developer(editSampleDeveloper);
            sample.setSample_leader(editSampleleader);
            sample.setTester(tester);

            int sample_frequency =  Integer.parseInt(editsample_frequency.trim());
            sample.setSample_frequency(sample_frequency);

            sample.setBig_species(big_species);
            sample.setSmall_species(small_species);
            sample.setHigh_frequency(high_frequency);

            sample.setQuestStats(questStats);

            LocalDateTime createTime =  testManIndexService.queryCreateTime(sample_id);

            // 使用ISO_LOCAL_DATE_TIME来解析带T的格式
            LocalDateTime planfinishTime = LocalDateTime.parse(editPlanfinishTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);


            double planWorkDays = calculateWorkDays(createTime,planfinishTime,0);
            // 将其转换为 0.5 的倍数，向上取整,只算0.5的倍数
            double adjustedWorkDays = Math.ceil(planWorkDays * 2) / 2;
            System.out.println("adjustedWorkDays:"+adjustedWorkDays);;
            sample.setPlanTestDuration(adjustedWorkDays);


            //如果有更新产品名称则需要更新文件名
            String old_name = testManIndexService.querySample_name(sample_id);
            String oldFilePath = testManIndexService.queryFilepath(sample);
            String oldtester = testManIndexService.queryTester(sample_id); //已经添加questStats,20240709


            if(!Objects.equals(oldtester, tester)){
                response.put("message", "更换当前测试人");
            }

            String high_sign = "";
            sample.setFilepath(oldFilePath);
            if(!Objects.equals(old_name, editSampleName)){
                File oldFile = new File(oldFilePath);

                if(high_frequency.equals("是")){
                    high_sign = "高频_";
                }

                // 生成新的文件名字符串
                String newFileName = savepath.replace("/","\\") + "\\" +editModel + " " + editCoding + "_" + editCategory + "_" + editVersion + "_第" + editsample_frequency + "次送样_" + high_sign  + editSampleName + ".xlsx";
                File newFile = new File(newFileName);
                logger.info("尝试重命名文件：oldFilePath=" + oldFilePath + ", newFileName=" + newFileName);

                try {
                    if (oldFile.renameTo(newFile)) {
                        sample.setFilepath(newFileName);
                        logger.info("updateSamples文件重命名成功" + oldFile + " -> " + newFileName);
                        response.put("rename","重命名成功");
                    } else {
                        throw new IOException("文件重命名失败" + oldFile + " -> " + newFileName);
                    }
                } catch (IOException e) {
                    logger.error("重命名文件时出错: " + e.getMessage());
                    response.put("status", "error");
                    response.put("message", "文件重命名失败，请确保文件未被占用并且路径正确: " + e.getMessage());
                    return response;
                }

            }

            //如果有更改当前测试人，则需要添加共同测试人
            String tester_teamwork = testManIndexService.queryTester_teamwork(sample_id);
            boolean containsName = tester_teamwork.contains(tester);
            if(containsName){
                testManIndexService.updateSample(sample);
            }else{
                testManIndexService.updateSampleTeamWork(sample);
                logger.info("添加共同测试人:"+tester);
            }
            response.put("oldFilePath", oldFilePath);
            response.put("status", "success");
            logger.info("updateSamples样品信息更新成功:"+tester);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "样品信息更新失败: " + e.getMessage());
            logger.info("updateSamples样品信息更新失败:"+e.getMessage());
        }

        return response;
    }


    @PostMapping("/testManIndex/finishTest")
    @ResponseBody
    public Map<String, Object> finishTest(@RequestBody Map<String, String> request){
        Map<String, Object> response = new HashMap<>();
        String filepath = request.get("filepath");
        String model = request.get("model");
        String coding = request.get("coding");

        String schedule = request.get("schedule");
        String userInput = request.get("restDays");
        String sample_idStr = request.get("sample_id");
        int sample_id = Integer.parseInt(sample_idStr);

    // 判断是否存在 restDays 参数
        double restDays = 0.0; // 默认值为 0
        if (userInput != null && !userInput.trim().isEmpty()) {
            // 存在输入值，尝试解析
            restDays = parseRestDays(userInput);
        } else {
            System.out.println("未提供 restDays 参数，使用默认值 0.0");
        }

        if (schedule.equals("0")){
            schedule = "1";
            // 设置完成时间为当前的北京时间
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedDateTime = now.format(formatter);

            String problem = getProblemFromJson(filepath,model,coding);

            // 检查问题是否包含缺少列的错误
            if (problem.startsWith("缺少列:")) {
                response.put("status", "error");
                response.put("message", problem); // 返回缺少列的错误信息
                logger.error("提交文件失败：" + filepath + "，" + problem);
                return response;
            }else if(problem.startsWith("需要进入页面查看问题点是否存在才可提交")){
                response.put("status", "error");
                response.put("message", problem); // 返回缺少列的错误信息
                logger.error("提交文件失败：" + filepath + "，" + problem);
                return response;
            }else if(problem.startsWith("工作表'测试问题点汇总'不存在！提交失败，请检查。")){
                response.put("status", "error");
                response.put("message", problem); // 返回缺少列的错误信息
                logger.error("提交文件失败：" + filepath + "，" + problem);
                return response;
            }


            testManIndexService.finishTestWithoutTime(schedule,formattedDateTime,sample_id);

            LocalDateTime createTime =  testManIndexService.queryCreateTime(sample_id);
            LocalDateTime planFinishTime =  testManIndexService.queryPlanFinishTime(sample_id);

            double planWorkDays = calculateWorkDays(createTime,planFinishTime,restDays);
            // 将其转换为 0.5 的倍数，向上取整,只算0.5的倍数
            double adjustedWorkDays = Math.ceil(planWorkDays * 2) / 2;

            double workDays = calculateWorkDays(createTime,now,restDays);
            int setDuration = testManIndexService.setDuration(adjustedWorkDays,workDays,sample_id);
            if(setDuration==1){
                logger.info("提交文件："+filepath + ",测试时长："+workDays + ",预计完成时长："+adjustedWorkDays);
            }

            response.put("message", "文件提交成功，接下来请审核！您的报告预计测试时长为：" + adjustedWorkDays + " 天。实际测试时长为："+workDays + "天。");

        }else if(schedule.equals("1")){
            schedule = "0";
            testManIndexService.finishTest(schedule,sample_id);
            response.put("message", "文件撤回成功，请重新提交！");
            logger.info("撤回审核成功："+filepath);
        }

        response.put("status", "success");


        return response;
    }


    private String getProblemFromJson(String filepath,String model,String coding){
        // 获取文件名并去除扩展名
        File file = new File(filepath);
        String fileName = file.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String jsonFilePath = jsonpath + File.separator + baseName + ".json";

        // 检查JSON文件是否存在
        File jsonFile = new File(jsonFilePath);
        if (!jsonFile.exists()) {
            return "需要进入页面查看问题点是否存在才可提交"; // 返回错误信息
        }
        boolean foundSheet = false; // 标志变量，初始为 false


        try {
            // 创建ObjectMapper实例
            ObjectMapper objectMapper = new ObjectMapper();

            // 读取JSON文件为JsonNode对象
            JsonNode rootNode = objectMapper.readTree(new File(jsonFilePath));

            // 定义目标sheetName
            String targetSheetName = "测试问题点汇总";


            // 用于存储所有数据行
            List<List<String>> dataRows = new ArrayList<>();

            // 遍历JSON数组
            if (rootNode.isArray()) {
                for (JsonNode outerArrayNode : rootNode) {
                    if (outerArrayNode.isArray()) {
                        for (JsonNode innerArrayNode : outerArrayNode) {
                            // 筛选sheetName为目标名称的数据
                            List<JsonNode> matchedNodes = getMatchedNodes(innerArrayNode, targetSheetName);

                            if (!matchedNodes.isEmpty()) {
                                foundSheet = true; // 找到目标工作表
                            }
                            // 提取所有数据行
                            for (JsonNode node : matchedNodes) {
                                if (node.has("row") && node.has("column")) {
                                    int rowIndex = node.get("row").asInt();
                                    int colIndex = node.get("column").asInt();

                                    // 确保rowIndex和colIndex为正数
                                    if (rowIndex > 0 && colIndex > 0) {
                                        // 确保行列表存在
                                        while (dataRows.size() < rowIndex) {
                                            dataRows.add(new ArrayList<>());
                                        }

                                        // 获取对应行数据
                                        List<String> rowData = dataRows.get(rowIndex - 1);
                                        String value = node.get("value").asText().replace("\n", "").trim();

                                        // 确保列列表存在
                                        while (rowData.size() < colIndex) {
                                            rowData.add("");
                                        }

                                        // 如果列索引有效，设置值
                                        if (colIndex > 0) {
                                            rowData.set(colIndex - 1, value);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 如果目标工作表没有找到，则返回相应信息
            if (!foundSheet) {
                return "工作表'测试问题点汇总'不存在！提交失败，请检查。"; // 返回错误信息
            }

            List<Map<String, String>> filteredRows = filterAndPrintRows(dataRows);

            // 定义需要的列名
            List<String> requiredColumns = Arrays.asList(
                    "样品型号", "样品阶段", "版本", "芯片方案", "日期", "测试人员", "测试平台",
                    "显示设备", "其他设备", "问题点", "问题视频或图片", "复现手法", "恢复方法",
                    "复现概率", "缺陷等级", "当前状态", "对比上一版或竞品", "DQE&研发确认",
                    "改善对策（研发回复）", "分析责任人", "改善后风险", "下一版回归测试", "备注"
            );

            // 检查缺少的列名
            Set<String> missingColumns = new HashSet<>();
            for (Map<String, String> rowMap : filteredRows) {
                for (String column : requiredColumns) {
                    if (!rowMap.containsKey(column)) {
                        missingColumns.add(column);
                    }
                }
            }

            // 如果有缺少的列名，返回错误信息
            if (!missingColumns.isEmpty()) {
                return "缺少列: " + String.join(", ", missingColumns); // 返回缺少列的错误信息
            }


            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();
            // 定义格式化器，将 LocalDateTime 格式化为 yyyy-MM-dd HH:mm:ss
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            // 格式化当前时间为字符串
            String created_at = now.format(formatter);
            // 使用相同的格式化器解析字符串
            LocalDateTime parsedDateTime = LocalDateTime.parse(created_at, formatter);
            System.out.println("Parsed LocalDateTime: " + parsedDateTime);
            //要先获取samples的id主键，然后搜索历史版本id，没有的话就是0，有就加1
            int sample_id = testManIndexService.querySampleId(filepath);
            System.out.println("sample_id:"+sample_id);
            int history_id = testManIndexService.queryHistoryid(sample_id);
            System.out.println("history_id:"+history_id);


            for (Map<String, String> rowMap : filteredRows) {
                TestIssues testIssues = new TestIssues();

                String full_model = rowMap.get("样品型号");
                String sample_stage = rowMap.get("样品阶段");
                String issue_version = rowMap.get("版本");
                String chip_solution = rowMap.get("芯片方案");
                String problem_time = rowMap.get("日期"); //此数据是问题点工作表里的只填日期的数据，所以我设置数据库里字段是varchar(8)
                String tester = rowMap.get("测试人员");
                String test_platform = rowMap.get("测试平台");
                String test_device = rowMap.get("显示设备");
                String other_device = rowMap.get("其他设备");
                String problem = rowMap.get("问题点");
                String problem_image_or_video = rowMap.get("问题视频或图片");
                String reproduction_method = rowMap.get("复现手法");
                String recovery_method = rowMap.get("恢复方法");
                String reproduction_probability = rowMap.get("复现概率");
                String defect_level = rowMap.get("缺陷等级");
                String current_status = rowMap.get("当前状态");
                String comparison_with_previous = rowMap.get("对比上一版或竞品");
                String dqe_and_development_confirm = rowMap.get("DQE&研发确认");
                String improvement_plan = rowMap.get("改善对策（研发回复）");
                String responsible_person = rowMap.get("分析责任人");
                String post_improvement_risk = rowMap.get("改善后风险");
                String next_version_regression_test = rowMap.get("下一版回归测试");
                String remark = rowMap.get("备注");

                String real_full_model = model + " "+ coding;
                testIssues.setFull_model(real_full_model); //因为完整编码测试人员填写的可能不一致，所以这里强制用数据库的编码就保证一致
                testIssues.setSample_stage(sample_stage);
                testIssues.setVersion(issue_version);
                testIssues.setChip_solution(chip_solution);
                testIssues.setProblem_time(problem_time);
                testIssues.setTester(tester);
                testIssues.setTest_platform(test_platform);
                testIssues.setTest_device(test_device);
                testIssues.setOther_device(other_device);
                testIssues.setProblem(problem);
                testIssues.setProblem_image_or_video(problem_image_or_video);
                testIssues.setReproduction_method(reproduction_method);
                testIssues.setRecovery_method(recovery_method);
                testIssues.setReproduction_probability(reproduction_probability);
                testIssues.setDefect_level(defect_level);
                testIssues.setCurrent_status(current_status);
                testIssues.setComparison_with_previous(comparison_with_previous);
                testIssues.setDqe_and_development_confirm(dqe_and_development_confirm);
                testIssues.setImprovement_plan(improvement_plan);
                testIssues.setResponsible_person(responsible_person);
                testIssues.setPost_improvement_risk(post_improvement_risk);
                testIssues.setNext_version_regression_test(next_version_regression_test);
                testIssues.setRemark(remark);

                testIssues.setCreated_at(parsedDateTime);
                testIssues.setSample_id(sample_id);
                testIssues.setHistory_id(history_id);

                int insertProblem = testManIndexService.insertTestIssues(testIssues);

                if(insertProblem == 1){
                    logger.info("问题点上传成功："+filepath);
                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "getProblem";
    }

    private static List<JsonNode> getMatchedNodes(JsonNode node, String targetSheetName) {
        List<JsonNode> matchedNodes = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                // 检查是否匹配目标sheetName
                if (item.has("sheetName") && targetSheetName.equals(item.get("sheetName").asText().trim())) {
                    matchedNodes.add(item);
                }
            }
        }
        return matchedNodes;
    }

    private List<Map<String, String>> filterAndPrintRows(List<List<String>> dataRows) {
        // 获取第一行作为列名
        List<String> columnNames = dataRows.get(0);

        // 用于存储过滤后的数据行
        List<Map<String, String>> filteredRows = new ArrayList<>();

        // 从第二行开始处理数据
        for (int i = 1; i < dataRows.size(); i++) {
            List<String> row = dataRows.get(i);

            // 检查第十列是否为空（索引为9）
            if (row.size() >= 10 && !row.get(9).trim().isEmpty()) {
                // 使用一个Map来存储列名和值的对应关系
                Map<String, String> rowMap = new HashMap<>();

                for (int j = 0; j < row.size() && j < columnNames.size(); j++) {
                    // 将列名和值放入Map中
                    rowMap.put(columnNames.get(j), row.get(j));
                }

                // 将这个Map添加到过滤后的数据行列表中
                filteredRows.add(rowMap);
            }
        }

        return filteredRows;
    }




    @PostMapping("/testManIndex/deleteSampleAndIssues")
    @ResponseBody
    public Map<String, Object> deleteFilepath(@RequestBody Map<String, String> request){
        Map<String, Object> response = new HashMap<>();
        String filepath = request.get("filepath");
        String sample_id = request.get("sample_id");
        int sampleId = Integer.parseInt(sample_id);

        logger.info("删除json文件sample_id："+sampleId);
        logger.info("删除json文件："+filepath);
        //删除文件夹里的文件,后续要备份则需要在这里增加备份到别的路径的处理
        try {
            // 创建文件路径的Path对象
            Path fileToDeletePath = Paths.get(filepath);
            // 删除文件
            Files.deleteIfExists(fileToDeletePath);

            // 如果需要备份，可以在这里添加备份的代码
            // backupFile(fileToDeletePath);
            int deleteJudge = testManIndexService.deleteFromTestIssues(sampleId);
            int deleteJudge2 = testManIndexService.deleteFromSamples(sampleId);
            if(deleteJudge2==1){
                // 返回成功响应
                response.put("oldFilePath",filepath);
                response.put("status", "success");
                response.put("message", "删除文件成功");
                logger.info("deleteFilepath successfully filepath:"+filepath);
            }else{
                // 返回成功响应
                response.put("status", "error");
                response.put("message", "数据库删除文件失败");
                logger.info("数据库删除文件失败:"+filepath);
            }


        } catch (IOException e) {
            // IO异常处理
            e.printStackTrace();
            // 返回失败响应
            response.put("status", "error");
            response.put("message", "删除异常: " + e.getMessage());
            logger.info("deleteFilepath fail filepath:"+filepath+"e.getMessage():"+e.getMessage());
        }

        return response;
    }



    @PostMapping("/testManIndex/uploadFileToDingtalk")
    @ResponseBody
    public Map<String, String> uploadFileToDingtalk(@RequestParam("filepath") String filepath,
                                          @RequestParam("dirId") String dirId,
                                          @RequestParam("spaceId") String spaceId,
                                          @RequestParam("receiverId") String receiverId,
                                          @RequestParam("authCode") String authCode) {
        Map<String, String> response = new HashMap<>();
        logger.info("Received request to uploadFileToDingtalk. filepath: {}, dirId: {}, spaceId: {}, receiverId: {}, authCode: {}",
                filepath, dirId, spaceId, receiverId, authCode);
        try {
            String accessToken = accessTokenService.getAccessToken();
            File file = new File(filepath);
            String filename = file.getName();

            String media_id = getMediaId(filepath,accessToken);
            System.out.println("media_id:"+media_id);

            //上传文件到钉盘
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/cspace/add");
            OapiCspaceAddRequest req = new OapiCspaceAddRequest();
            req.setAgentId(agentid);
            req.setCode(authCode);
            req.setFolderId(dirId);
            req.setMediaId(media_id);
            req.setSpaceId(spaceId);
            req.setName(filename);
            req.setOverwrite(true);
            req.setHttpMethod("GET");
            OapiCspaceAddResponse rsp = client.execute(req, accessToken);
            logger.info("Response from DingTalk cspace add: {}", rsp.getBody());
            //发送钉盘文件给用户
            sendDingFileToUser(accessToken,filename,media_id,receiverId);

            response.put("status", "发送成功");
            logger.info("uploadFileToDingtalk successfully.");
        } catch (ApiException e) {
            e.printStackTrace();
            response.put("status", "发送失败");
            logger.info("uploadFileToDingtalk fail.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    public String getMediaId(String filepath,String access_token) throws Exception {
        File file = new File(filepath);
        long fileSize = file.length(); // 获取文件大小

        // 计算分块数
        long chunkSize = 7 * 1024 * 1024; // 7MB
        long chunkNumbers = (fileSize + chunkSize - 1) / chunkSize; // 计算分块数

        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/file/upload/transaction");
        OapiFileUploadTransactionRequest request = new OapiFileUploadTransactionRequest();
        request.setAgentId(agentid);
        request.setFileSize(fileSize);
        request.setChunkNumbers(chunkNumbers);
        request.setHttpMethod("GET");
        OapiFileUploadTransactionResponse response = client.execute(request,access_token);
        String uploadId = response.getUploadId();
        System.out.println("chunkNumbers:"+chunkNumbers);
        System.out.println("response.getBody():"+response.getBody());

//        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");

        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(file, "r");
            for (long i = 0; i < chunkNumbers; i++) {
                long startByte = i * chunkSize;
                byte[] chunkData = new byte[(int) Math.min(chunkSize, fileSize - startByte)];
                randomAccessFile.seek(startByte);
                randomAccessFile.readFully(chunkData);

                // 准备上传请求
                OapiFileUploadChunkRequest chunkRequest = new OapiFileUploadChunkRequest();
                chunkRequest.setAgentId(agentid);
                chunkRequest.setUploadId(uploadId);
                chunkRequest.setChunkSequence(i + 1); // 分块序号从1开始

                DingTalkClient chunkClient = new DefaultDingTalkClient("https://oapi.dingtalk.com/file/upload/chunk?"+WebUtils.buildQuery(chunkRequest.getTextParams(),"utf-8"));

                chunkRequest = new OapiFileUploadChunkRequest();
                // 设置文件内容
                FileItem fileItem = new FileItem("chunk" + i, chunkData);
                chunkRequest.setFile(fileItem);

                // 发送上传请求
                OapiFileUploadChunkResponse chunkResponse = chunkClient.execute(chunkRequest, access_token);
            }
        } finally {
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        DingTalkClient transClient = new DefaultDingTalkClient("https://oapi.dingtalk.com/file/upload/transaction");
        OapiFileUploadTransactionRequest transRequest = new OapiFileUploadTransactionRequest();
        transRequest.setAgentId(agentid);
        transRequest.setFileSize(fileSize);
        transRequest.setChunkNumbers(chunkNumbers);
        transRequest.setUploadId(uploadId);
        transRequest.setHttpMethod("GET");
        OapiFileUploadTransactionResponse transResponse = transClient.execute(transRequest,access_token);
        System.out.println("transResponse:"+transResponse.getBody());

        return transResponse.getMediaId();
    }



    public void sendDingFileToUser(String accesstoken,String filename,String media_id,String userid) throws ApiException, IOException {
        OapiCspaceAddToSingleChatRequest request = new OapiCspaceAddToSingleChatRequest();
        request.setAgentId(agentid);
        request.setUserid(userid);
        request.setMediaId(media_id);
        request.setFileName(filename);
        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/cspace/add_to_single_chat?"+WebUtils.buildQuery(request.getTextParams(),"utf-8"));
        OapiCspaceAddToSingleChatResponse response = client.execute(request, accesstoken);
        logger.info("sendDingFileToUser successfully."+response.getBody());
    }


    @PostMapping("/log/debug")
    @ResponseBody
    public String logDebug(@RequestBody Map<String, String> log) {
        String message = log.get("message");
        logger.info(message);
        return message;
    }

    @PostMapping("/testManIndex/clearJson")
    @ResponseBody
    public String clearJson(@RequestBody Map<String, String> data) {
        String oldFilePath = data.get("oldFilePath");

        // 获取文件名并去除扩展名
        File file = new File(oldFilePath);
        String fileName = file.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String jsonFilePath = jsonpath+ File.separator + baseName + ".json";

        try {
            File jsonFile = new File(jsonFilePath);
            if (jsonFile.exists()) {
                if (jsonFile.delete()) {
                    logger.info("删除json文件了: " + jsonFilePath);
                    return "json文件成功删除";
                } else {
                    logger.error("删除json文件失败 " + jsonFilePath);
                    return "删除json文件失败";
                }
            } else {
                logger.info("json文件没有找到 " + jsonFilePath);
                return "json文件没有找到";
            }
        } catch (Exception e) {
            logger.error("An error occurred while deleting the JSON file.", e);
            return "An error occurred while deleting the JSON file.";
        }
    }


    //计算测试时长testDuration
    // 计算上班时间内的小时数，并换算成工作天数
    // 计算工作天数，只计算工作时间段（早9到12点，下午1:30到6:30）
    // 计算工作天数，只计算工作时间段（早9到12点，下午1:30到6:30）
    public static double calculateWorkDays(LocalDateTime createTime, LocalDateTime finishTime, double restDays) {
        // 计算两个日期之间的总工作小时数
        double totalWorkHours = 0;
        LocalDateTime currentDateTime = createTime;

        while (currentDateTime.toLocalDate().isBefore(finishTime.toLocalDate()) || !currentDateTime.isAfter(finishTime)) {
            // 计算当天工作时间
            LocalDateTime endOfDay = currentDateTime.toLocalDate().atTime(18, 30);
            LocalDateTime startOfDay = currentDateTime.toLocalDate().atTime(9, 0);

            // 如果当天已经超过了完成时间，则调整结束时间
            if (finishTime.isBefore(endOfDay)) {
                endOfDay = finishTime;
            }

            // 计算早上时间段
            LocalDateTime endMorning = currentDateTime.toLocalDate().atTime(12, 0);
            LocalDateTime effectiveEndMorning = (finishTime.isBefore(endMorning) ? finishTime : endMorning);

            if (currentDateTime.isBefore(effectiveEndMorning)) {
                totalWorkHours += calculateTimeInPeriod(currentDateTime, effectiveEndMorning);
            }

            // 计算下午时间段
            LocalDateTime startAfternoon = currentDateTime.toLocalDate().atTime(13, 30);
            LocalDateTime effectiveStartAfternoon = (currentDateTime.isBefore(startAfternoon) ? startAfternoon : currentDateTime);
            LocalDateTime endAfternoon = currentDateTime.toLocalDate().atTime(18, 30);

            if (finishTime.isBefore(endAfternoon)) {
                endAfternoon = finishTime;
            }

            if (effectiveStartAfternoon.isBefore(endAfternoon)) {
                totalWorkHours += calculateTimeInPeriod(effectiveStartAfternoon, endAfternoon);
            }

            // 移动到第二天
            currentDateTime = currentDateTime.toLocalDate().plusDays(1).atStartOfDay().plusHours(9);
        }

        // 将总工作时间转换为工作天数
        double workDays = totalWorkHours / 8;

        // 减去休息天数
        workDays -= restDays;

        // 保留一位小数
        return Math.max(Math.round(workDays * 10) / 10.0, 0); // 确保返回值不小于0
    }

    private static double calculateTimeInPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        // 计算两个时间点之间的小时数
        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        return Math.max(0, minutes / 60.0);
    }

    //将字符串转换为double类型
    public static double parseRestDays(String restDays) throws IllegalArgumentException {
        // 使用正则表达式来验证输入是否是有效的数字（整数或 0.5 的倍数）
        String regex = "^(0|[1-9][0-9]*)(\\.5)?$";
        if (!Pattern.matches(regex, restDays)) {
            throw new IllegalArgumentException("无效的休息天数输入！");
        }

        // 将输入字符串转换为 double
        return Double.parseDouble(restDays);
    }

    @GetMapping("/testManProfile") // 处理页面跳转请求
    public String loginTestManProfile() {
        // 返回跳转页面的视图名称
        return "testManProfile";
    }



}
