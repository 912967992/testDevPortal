package com.lu.ddwyydemo04.controller.reliabilityLab;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Controller
public class IoTDataController {

    private static final int MAX_RECORDS = 1000;
    private final Queue<Map<String, Object>> receivedData = new ConcurrentLinkedQueue<>();
    private final ReliabilityLabDataDao reliabilityLabDataDao;

    public IoTDataController(ReliabilityLabDataDao reliabilityLabDataDao) {
        this.reliabilityLabDataDao = reliabilityLabDataDao;
    }

    /**
     * 接收物联网数据的接口 - 支持所有HTTP方法和内容类型
     */
    @RequestMapping(value = "/iot/data",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> receiveData(
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestHeader Map<String, String> allHeaders,
            HttpServletRequest request) {

        try {
            // 创建数据记录
            Map<String, Object> dataRecord = new HashMap<>();
            dataRecord.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            dataRecord.put("method", request.getMethod());
            dataRecord.put("url", request.getRequestURL().toString());
            dataRecord.put("queryParams", allParams != null ? allParams : new HashMap<>());
            dataRecord.put("headers", allHeaders);
            dataRecord.put("contentType", request.getContentType());
            dataRecord.put("remoteAddr", request.getRemoteAddr());
            dataRecord.put("userAgent", request.getHeader("User-Agent"));

            // 处理请求体数据
            Map<String, Object> bodyData = new HashMap<>();

            // 对于表单数据，参数已经在 allParams 中
            if ("application/x-www-form-urlencoded".equals(request.getContentType()) ||
                    "multipart/form-data".equals(request.getContentType())) {
                bodyData.put("formData", allParams != null ? allParams : new HashMap<>());
            } else {
                // 对于其他类型的数据，尝试读取原始请求体
                try {
                    String body = request.getReader().lines().collect(java.util.stream.Collectors.joining("\n"));
                    if (body != null && !body.trim().isEmpty()) {
                        bodyData.put("rawBody", body);

                        // 尝试解析JSON
                        try {
                            Object jsonData = new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Object.class);
                            bodyData.put("jsonData", jsonData);
                        } catch (Exception e) {
                            // 不是JSON格式，保持原始数据
                        }
                    }
                } catch (Exception e) {
                    bodyData.put("error", "无法读取请求体: " + e.getMessage());
                }
            }

            dataRecord.put("body", bodyData);

            // 添加到队列
            receivedData.offer(dataRecord);

            // 保持队列大小不超过最大值
            while (receivedData.size() > MAX_RECORDS) {
                receivedData.poll();
            }

            // 返回成功响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "数据接收成功");
            response.put("timestamp", dataRecord.get("timestamp"));
            response.put("totalRecords", receivedData.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "数据接收失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 接收 JSON 并入库到 reliabilityLabData
     * 支持单个对象或对象数组格式
     */
    @PostMapping(value = "/iot/data", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> receiveJsonAndStore(@RequestBody Object payload) {
        try {
            int successCount = 0;
            int failCount = 0;
            String lastError = null;

            // 判断是数组还是单个对象
            if (payload instanceof List) {
                // 处理数组格式
                @SuppressWarnings("unchecked")
                List<Object> payloadList = (List<Object>) payload;
                for (Object item : payloadList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        try {
                            processAndInsert(itemMap);
                            successCount++;
                        } catch (Exception e) {
                            failCount++;
                            lastError = e.getMessage();
                        }
                    }
                }
            } else if (payload instanceof Map) {
                // 处理单个对象格式
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = (Map<String, Object>) payload;
                try {
                    processAndInsert(payloadMap);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    lastError = e.getMessage();
                }
            } else {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "不支持的数据格式，期望对象或对象数组");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }

            Map<String, Object> resp = new HashMap<>();
            if (failCount == 0) {
                resp.put("success", true);
                resp.put("message", "数据已入库");
                if (successCount > 1) {
                    resp.put("count", successCount);
                }
            } else {
                resp.put("success", false);
                resp.put("message", String.format("部分数据入库失败: 成功 %d 条, 失败 %d 条", successCount, failCount));
                if (lastError != null) {
                    resp.put("lastError", lastError);
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "入库失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 处理单个数据对象并入库
     */
    private void processAndInsert(Map<String, Object> payload) {
        // 预处理数值字段（两位小数），允许空
        BigDecimal temperature = toScale(payload.get("temperature"));
        BigDecimal humidity = toScale(payload.get("humidity"));
        BigDecimal setTemperature = toScale(payload.get("set_temperature"));
        BigDecimal setHumidity = toScale(payload.get("set_humidity"));

        ReliabilityLabData data = new ReliabilityLabData();
        data.setTemperature(temperature);
        data.setHumidity(humidity);
        data.setSetTemperature(setTemperature);
        data.setSetHumidity(setHumidity);
        data.setPowerTemperature(asText(payload.get("power_temperature")));
        data.setPowerHumidity(asText(payload.get("power_humidity")));
        data.setRunMode(asText(payload.get("run_mode")));
        data.setRunStatus(asText(payload.get("run_status")));
        data.setRunHours(asText(payload.get("run_hours")));
        data.setRunMinutes(asText(payload.get("run_minutes")));
        data.setRunSeconds(asText(payload.get("run_seconds")));
        data.setSetProgramNumber(asText(payload.get("set_program_number")));
        data.setSetRunStatus(asText(payload.get("set_run_status")));
        data.setTotalSteps(asText(payload.get("total_steps")));
        data.setRunningStep(asText(payload.get("running_step")));
        data.setStepRemainingHours(asText(payload.get("step_remaining_hours")));
        data.setStepRemainingMinutes(asText(payload.get("step_remaining_minutes")));
        data.setStepRemainingSeconds(asText(payload.get("step_remaining_seconds")));
        data.setRawPayload(toJsonString(payload));

        reliabilityLabDataDao.insert(data);
    }

    @GetMapping("/iot/data/latest")
    @ResponseBody
    public Map<String, Object> getLatest() {
        ReliabilityLabData d = reliabilityLabDataDao.selectLatest();
        Map<String, Object> m = new HashMap<>();
        if (d == null) return m;
        m.put("temperature", d.getTemperature());
        m.put("humidity", d.getHumidity());
        m.put("set_temperature", d.getSetTemperature());
        m.put("set_humidity", d.getSetHumidity());
        m.put("power_temperature", d.getPowerTemperature());
        m.put("power_humidity", d.getPowerHumidity());
        m.put("run_mode", d.getRunMode());
        m.put("run_status", d.getRunStatus());
        m.put("run_hours", d.getRunHours());
        m.put("run_minutes", d.getRunMinutes());
        m.put("run_seconds", d.getRunSeconds());
        m.put("set_program_number", d.getSetProgramNumber());
        m.put("set_run_status", d.getSetRunStatus());
        m.put("total_steps", d.getTotalSteps());
        m.put("running_step", d.getRunningStep());
        m.put("step_remaining_hours", d.getStepRemainingHours());
        m.put("step_remaining_minutes", d.getStepRemainingMinutes());
        m.put("step_remaining_seconds", d.getStepRemainingSeconds());
        return m;
    }

    private static BigDecimal toScale(Object value) {
        if (value == null) return null;
        try {
            BigDecimal bd = new BigDecimal(String.valueOf(value));
            return bd.setScale(2, BigDecimal.ROUND_HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String toJsonString(Map<String, Object> obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}


