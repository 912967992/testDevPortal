package com.lu.ddwyydemo04.controller.reliabilityLab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.MalformedURLException;

@Controller
public class UsageRate {

    @GetMapping("/reliablityLab/usageRate") // 处理页面跳转请求
    public String loginUsageRate() {
        // 返回跳转页面的视图名称
        return "reliablityLab/usageRate";
    }

    @Value("${file.storage.csvpath}")
    private String csvpath;
    private static final String UPLOAD_DIR = "指定的上传目录路径"; // 请替换为你的目标路径

    @PostMapping("/reliablityLab/uploadCSVFiles")
    public ResponseEntity<String> uploadCSVFiles(@RequestParam("files") MultipartFile[] files) {

        File uploadDir = new File(csvpath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        List<File> uploadedFiles = new ArrayList<>();
        StringBuilder usageDetails = new StringBuilder(); // 用于存储使用详情
        double totalUsageTime = 0; // 总使用时间
        long earliestTime = Long.MAX_VALUE; // 最早的时间点
        long latestTime = Long.MIN_VALUE; // 最晚的时间点

        for (MultipartFile file : files) {
            if (!file.isEmpty() && file.getOriginalFilename().endsWith(".csv")) {
                try {
                    File destinationFile = new File(uploadDir, file.getOriginalFilename());
                    file.transferTo(destinationFile);
                    uploadedFiles.add(destinationFile);
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(createErrorResponse("文件上传失败: " + e.getMessage()));
                }
            }
        }

        // 解析文件并计算使用率
        double usageRate = calculateUsageRate(uploadedFiles, usageDetails);
        System.out.println("usageRate:"+usageRate);

        // 生成最终的响应信息，只包含使用率
        String responseMessage = String.format("所有CSV文件上传成功！使用率: %.2f%%", usageRate);

        // 将日志写入到文件中
        String logFileName = "usageDetails_" + System.currentTimeMillis() + ".txt"; // 使用当前时间戳
        String logFilePath = new File(csvpath, logFileName).getPath(); // 保存路径
        writeLogToFile(logFilePath, usageDetails.toString());

        // 构建返回的响应对象，包含下载链接
        Map<String, Object> response = new HashMap<>();
        response.put("message", responseMessage);
        response.put("downloadLink", "/reliablityLab/download/" + logFileName); // 添加下载链接

        try {
            // 使用 ObjectMapper 将响应对象转换为 JSON 字符串
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponse = objectMapper.writeValueAsString(response);
            return ResponseEntity.ok(jsonResponse); // 返回 JSON 响应
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("生成JSON响应失败: " + e.getMessage()));
        }
    }

    private void writeLogToFile(String filePath, String logContent) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(logContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readLogFile(String filePath) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content.toString();
    }

    private String createSuccessResponse(String message) {
        return "{\"message\":\"" + escapeJson(message) + "\"}";
    }

    private String createErrorResponse(String error) {
        return "{\"error\":\"" + escapeJson(error) + "\"}";
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\") // 转义反斜杠
                .replace("\"", "\\\"") // 转义引号
                .replace("\n", "\\n") // 转义换行符
                .replace("\r", "\\r"); // 转义回车符
    }

    private double calculateUsageRate(List<File> files, StringBuilder usageDetails) {
        long totalUsageTime = 0; // 总使用时间（毫秒）
        long earliestTime = Long.MAX_VALUE; // 最早的时间点
        long latestTime = Long.MIN_VALUE; // 最晚的时间点
        int totalUsageEntries = 0; // 总的使用条目数
        int totalFiles = files.size(); // 文件数量

        for (File file : files) {
            // 修改打印方式，将输出写入到 usageDetails 中
            usageDetails.append(String.format("正在解析文件: %s%n", file.getName()));
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                List<String> lastFiveTemperatures = new ArrayList<>(); // 最近的五次设定温度
                List<Long> lastFiveTimestamps = new ArrayList<>(); // 最近的五个时间戳
                boolean inUse = false;
                long usageStart = 0; // 当前使用状态的开始时间

                // 跳过第一行（列名）
                br.readLine();

                while ((line = br.readLine()) != null) {
                    String[] data = line.split(",");
                    if (data.length < 4) {
                        continue; // 跳过无效行
                    }

                    String date = data[0].trim();
                    System.out.println("长度"+date.length());

                    String time;
                    String setTemperature;
                    String dateTime;


                    if(date.length()>10){
                        // 提取时间部分并添加秒数
                        time = date.substring(date.indexOf(" ") + 1) + ":00";
                        // 提取日期部分
                        date = date.substring(0, date.indexOf(" ")).replace("/", "-");
                        setTemperature = data[2].trim();

                    }else{
                        time = data[1].trim();
                        setTemperature = data[3].trim();

                    }

                    dateTime = date + " " + time;

                    System.out.println("date:"+date);
                    System.out.println("time:"+time);

                    // 合并日期和时间并解析为时间戳

                    System.out.println("dateTime:"+dateTime);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    long currentTime = dateFormat.parse(dateTime).getTime();

                    // 更新最早和最晚的时间点
                    earliestTime = Math.min(earliestTime, currentTime);
                    latestTime = Math.max(latestTime, currentTime);

                    // 更新最近五次的设定温度和时间戳
                    if (lastFiveTemperatures.size() == 5) {
                        lastFiveTemperatures.remove(0); // 移除最早的一个
                        lastFiveTimestamps.remove(0); // 移除最早的时间戳
                    }
                    lastFiveTemperatures.add(setTemperature); // 添加当前设定温度
                    lastFiveTimestamps.add(currentTime); // 添加当前时间戳

                    // 检查最近五次温度是否相同
                    boolean allEqual = lastFiveTemperatures.size() == 5 && lastFiveTemperatures.stream().distinct().count() == 1;

                    if (allEqual) {
                        if (!inUse) {
                            // 开始使用状态
                            inUse = true;
                            usageStart = lastFiveTimestamps.get(0); // 使用状态的开始时间为第一个相同温度的时间点

//                            usageDetails.append(String.format("检测到连续五个相同设定温度：%s，开始时间：%s%n", setTemperature,
//                                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(usageStart)));
                        }
                    } else {
                        if (inUse) {
                            // 停止使用状态
                            inUse = false;
                            long usageEnd = lastFiveTimestamps.get(lastFiveTimestamps.size() - 2); // 使用状态的结束时间为倒数第二个相同温度的时间点
                            long usageDuration = usageEnd - usageStart; // 使用时长
                            totalUsageTime += usageDuration; // 更新总使用时间
                            totalUsageEntries++; // 增加使用条目数

                            // 保存使用详情
                            String usageEndTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(usageEnd); // 保存结束时间
                            double usageHours = usageDuration / (1000.0 * 60 * 60); // 转换为小时
                            usageDetails.append(String.format("结束使用状态，设定温度：%s，开始时间：%s，结束时间：%s，使用时长：%.2f 小时%n",
                                    lastFiveTemperatures.get(0), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(usageStart), usageEndTime, usageHours));
                        }
                    }
                }

                // 如果最后的状态是使用中，计算到最后的时间点
                if (inUse) {
                    long usageEnd = lastFiveTimestamps.get(lastFiveTimestamps.size() - 1); // 最后的结束时间
                    long usageDuration = usageEnd - usageStart;
                    totalUsageTime += usageDuration;
                    totalUsageEntries++;

                    // 保存使用详情
                    String usageEndTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(usageEnd); // 保存结束时间
                    double usageHours = usageDuration / (1000.0 * 60 * 60); // 转换为小时
                    usageDetails.append(String.format("结束使用状态，设定温度：%s，开始时间：%s，结束时间：%s，使用时长：%.2f 小时%n",
                            lastFiveTemperatures.get(0), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(usageStart), usageEndTime, usageHours));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 打印最早和最晚的时间点
        String earliestTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(earliestTime);
        String latestTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(latestTime);
        usageDetails.append(String.format("最早时间点：%s%n", earliestTimeStr));
        usageDetails.append(String.format("最晚时间点：%s%n", latestTimeStr));

        // 计算总时长（最晚时间点 - 最早时间点）
        long totalDuration = latestTime - earliestTime;

        // 计算使用率
        double totalUsageRate = totalDuration > 0 ? (double) totalUsageTime / totalDuration * 100 : 0; // 计算使用率
        usageDetails.append(String.format("总使用率：%.2f%% (总使用时长: %.2f 小时 / 总时长: %.2f 小时)%n",
                totalUsageRate, totalUsageTime / (1000.0 * 60 * 60), totalDuration / (1000.0 * 60 * 60)));

        return totalUsageRate;
    }

    @GetMapping("/reliablityLab/download/{fileName:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        System.out.println("fileName:"+fileName);
        Path filePath = Paths.get(csvpath, fileName);
        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



}
