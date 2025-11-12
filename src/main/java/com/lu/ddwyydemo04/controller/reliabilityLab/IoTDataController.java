package com.lu.ddwyydemo04.controller.reliabilityLab;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.dao.DeviceCommandDao;
import com.lu.ddwyydemo04.Service.DeviceCacheService;
import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import com.lu.ddwyydemo04.pojo.DeviceCommand;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class IoTDataController {

    private final ReliabilityLabDataDao reliabilityLabDataDao;
    private final DeviceCommandDao deviceCommandDao;
    private final DeviceCacheService deviceCacheService;

    public IoTDataController(ReliabilityLabDataDao reliabilityLabDataDao,
                           DeviceCommandDao deviceCommandDao,
                           DeviceCacheService deviceCacheService) {
        this.reliabilityLabDataDao = reliabilityLabDataDao;
        this.deviceCommandDao = deviceCommandDao;
        this.deviceCacheService = deviceCacheService;
    }

    /**
     * 接收物联网数据并入库
     * 支持单个对象或对象数组格式的JSON数据
     */
    @PostMapping(value = "/iot/data", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> receiveData(@RequestBody Object payload) {
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
     * 比较数据变化，只在数据有变化时更新数据库和缓存
     */
    private void processAndInsert(Map<String, Object> payload) {
        // 预处理数值字段（两位小数），允许空
        BigDecimal temperature = toScale(payload.get("temperature"));
        BigDecimal humidity = toScale(payload.get("humidity"));
        BigDecimal setTemperature = toScale(payload.get("set_temperature"));
        BigDecimal setHumidity = toScale(payload.get("set_humidity"));

        ReliabilityLabData newData = new ReliabilityLabData();
        String deviceId = asText(payload.get("device_id"));
        newData.setDeviceId(deviceId);
        newData.setTemperature(temperature);
        newData.setHumidity(humidity);
        newData.setSetTemperature(setTemperature);
        newData.setSetHumidity(setHumidity);
        newData.setPowerTemperature(asText(payload.get("power_temperature")));
        newData.setPowerHumidity(asText(payload.get("power_humidity")));
        newData.setRunMode(asText(payload.get("run_mode")));
        newData.setRunStatus(asText(payload.get("run_status")));
        newData.setRunHours(asText(payload.get("run_hours")));
        newData.setRunMinutes(asText(payload.get("run_minutes")));
        newData.setRunSeconds(asText(payload.get("run_seconds")));
        newData.setSetProgramNumber(asText(payload.get("set_program_number")));
        newData.setProgramNumber(asText(payload.get("program_number")));
        newData.setSetRunStatus(asText(payload.get("set_run_status")));
        newData.setTotalSteps(asText(payload.get("total_steps")));
        newData.setRunningStep(asText(payload.get("running_step")));
        newData.setProgramStep(asText(payload.get("program_step")));
        newData.setStepRemainingHours(asText(payload.get("step_remaining_hours")));
        newData.setStepRemainingMinutes(asText(payload.get("step_remaining_minutes")));
        newData.setStepRemainingSeconds(asText(payload.get("step_remaining_seconds")));

        // 检查数据是否与缓存中的数据有变化
        boolean hasChanges = hasDataChanged(deviceId, newData);

        if (hasChanges) {
            // 数据有变化，执行保存操作

            // 1. 插入到reliabilityLabData表（历史记录）
            reliabilityLabDataDao.insert(newData);

            // 2. 更新或插入到temperature_box_latest_data表
            ReliabilityLabData existingData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (existingData != null) {
                // 设备已存在，更新数据
                reliabilityLabDataDao.updateLatestData(newData);
            } else {
                // 设备不存在，插入新数据
                reliabilityLabDataDao.insertLatestData(newData);
            }

            // 3. 更新Redis缓存
            deviceCacheService.updateDeviceCache(deviceId, newData);

            // 4. 清除设备列表缓存，让下次查询时重新生成
            deviceCacheService.getRedisService().delete("device:list");
        } else {
            // 数据没有变化，只更新缓存的访问时间
            deviceCacheService.updateDeviceCache(deviceId, newData);
        }
    }

    /**
     * 比较新数据与缓存中的数据是否有所不同
     */
    private boolean hasDataChanged(String deviceId, ReliabilityLabData newData) {
        if (deviceId == null || deviceId.isEmpty()) {
            return true; // 没有设备ID，认为有变化
        }

        // 从Redis缓存获取现有数据
        ReliabilityLabData existingData = deviceCacheService.getLatestDeviceData(deviceId);

        if (existingData == null) {
            return true; // 缓存中没有数据，认为有变化
        }

        // 比较关键字段是否发生变化
        return !objectsEqual(newData.getTemperature(), existingData.getTemperature()) ||
               !objectsEqual(newData.getHumidity(), existingData.getHumidity()) ||
               !objectsEqual(newData.getSetTemperature(), existingData.getSetTemperature()) ||
               !objectsEqual(newData.getSetHumidity(), existingData.getSetHumidity()) ||
               !stringsEqual(newData.getPowerTemperature(), existingData.getPowerTemperature()) ||
               !stringsEqual(newData.getPowerHumidity(), existingData.getPowerHumidity()) ||
               !stringsEqual(newData.getRunMode(), existingData.getRunMode()) ||
               !stringsEqual(newData.getRunStatus(), existingData.getRunStatus()) ||
               !stringsEqual(newData.getRunHours(), existingData.getRunHours()) ||
               !stringsEqual(newData.getRunMinutes(), existingData.getRunMinutes()) ||
               !stringsEqual(newData.getRunSeconds(), existingData.getRunSeconds()) ||
               !stringsEqual(newData.getSetProgramNumber(), existingData.getSetProgramNumber()) ||
               !stringsEqual(newData.getProgramNumber(), existingData.getProgramNumber()) ||
               !stringsEqual(newData.getSetRunStatus(), existingData.getSetRunStatus()) ||
               !stringsEqual(newData.getTotalSteps(), existingData.getTotalSteps()) ||
               !stringsEqual(newData.getRunningStep(), existingData.getRunningStep()) ||
               !stringsEqual(newData.getProgramStep(), existingData.getProgramStep()) ||
               !stringsEqual(newData.getStepRemainingHours(), existingData.getStepRemainingHours()) ||
               !stringsEqual(newData.getStepRemainingMinutes(), existingData.getStepRemainingMinutes()) ||
               !stringsEqual(newData.getStepRemainingSeconds(), existingData.getStepRemainingSeconds());
    }

    /**
     * 比较两个对象是否相等（处理null值）
     */
    private boolean objectsEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    /**
     * 比较两个字符串是否相等（处理null值和空字符串）
     */
    private boolean stringsEqual(String str1, String str2) {
        if (str1 == null && str2 == null) return true;
        if (str1 == null || str2 == null) return false;
        return str1.trim().equals(str2.trim());
    }

    @GetMapping("/iot/data/latest")
    @ResponseBody
    public Map<String, Object> getLatest(@RequestParam(value = "device_id", required = false) String deviceId) {
        ReliabilityLabData data;
        if (deviceId != null && !deviceId.isEmpty()) {
            // 使用Redis缓存获取设备数据
            data = deviceCacheService.getLatestDeviceData(deviceId);
        } else {
            // 获取最新的一条数据（不使用设备缓存）
            data = reliabilityLabDataDao.selectLatest();
        }
        if (data == null) {
            return new HashMap<>();
        }
        return convertToLatestResponse(data);
    }

    /**
     * 获取每台设备最新的监控数据（使用Redis缓存）
     */
    @GetMapping("/iot/data/devices")
    @ResponseBody
    public List<Map<String, Object>> getDevices() {
        // 先检查缓存
        String cacheKey = "device:list";
        Object cachedData = deviceCacheService.getRedisService().get(cacheKey);

        if (cachedData instanceof List) {
            // 缓存命中，直接返回
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cachedResult = (List<Map<String, Object>>) cachedData;
            return cachedResult;
        }

        // 缓存未命中，从数据库查询
        List<ReliabilityLabData> records = deviceCacheService.getAllLatestDeviceData();
        List<Map<String, Object>> result = new ArrayList<>();
        if (records != null) {
            for (ReliabilityLabData record : records) {
                result.add(convertToDeviceResponse(record));
            }
        }

        // 将结果存入缓存，30分钟过期
        deviceCacheService.getRedisService().set(cacheKey, result, 30, java.util.concurrent.TimeUnit.MINUTES);

        return result;
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

    /**
     * 获取待执行的命令
     * GET /iot/getExcuteCommand?device_id=xxx (可选参数)
     * 返回状态码不是200的命令数据（直接返回device_command表的数据）
     * 每个设备只返回最新的一条数据（按设备ID去重）
     */
    @GetMapping("/iot/getExcuteCommand")
    @ResponseBody
    public ResponseEntity<?> getExecuteCommand(@RequestParam(required = false) String device_id) {
        try {
            // 查询状态码不是200的命令（已按创建时间倒序排列）
            List<DeviceCommand> commands = deviceCommandDao.selectCommandsWithNon200StatusCode(device_id);
            
            // 使用Map按设备ID去重，保留每个设备最新的命令（因为已经按created_at DESC排序）
            Map<String, DeviceCommand> deviceCommandMap = new HashMap<>();
            for (DeviceCommand cmd : commands) {
                if (cmd.getDeviceId() != null && !cmd.getDeviceId().isEmpty()) {
                    // 如果该设备还没有记录，则添加（因为已经按created_at DESC排序，第一个就是最新的）
                    if (!deviceCommandMap.containsKey(cmd.getDeviceId())) {
                        deviceCommandMap.put(cmd.getDeviceId(), cmd);
                    }
                }
            }
            
            List<Map<String, Object>> dataList = new ArrayList<>();
            
            // 将DeviceCommand对象转换为Map格式
            for (DeviceCommand cmd : deviceCommandMap.values()) {
                Map<String, Object> dataMap = convertDeviceCommandToMap(cmd);
                dataList.add(dataMap);
            }
            
            // 返回数组格式
            if (dataList.isEmpty()) {
                return ResponseEntity.ok(new ArrayList<Map<String, Object>>());
            } else {
                return ResponseEntity.ok(dataList);
            }
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "获取命令失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 将 DeviceCommand 转换为 Map 格式
     */
    private Map<String, Object> convertDeviceCommandToMap(DeviceCommand cmd) {
        Map<String, Object> m = new HashMap<>();
        if (cmd == null) return m;
        
        m.put("id", cmd.getId());
        m.put("device_id", cmd.getDeviceId());
        m.put("set_temperature", cmd.getSetTemperature());
        m.put("set_humidity", cmd.getSetHumidity());
        m.put("power_temperature", cmd.getPowerTemperature());
        m.put("power_humidity", cmd.getPowerHumidity());
        m.put("run_mode", cmd.getRunMode());
        m.put("run_status", cmd.getRunStatus());
        m.put("run_hours", cmd.getRunHours());
        m.put("run_minutes", cmd.getRunMinutes());
        m.put("run_seconds", cmd.getRunSeconds());
        m.put("set_program_number", cmd.getSetProgramNumber());
        m.put("set_run_status", cmd.getSetRunStatus());
        m.put("total_steps", cmd.getTotalSteps());
        m.put("running_step", cmd.getRunningStep());
        m.put("step_remaining_hours", cmd.getStepRemainingHours());
        m.put("step_remaining_minutes", cmd.getStepRemainingMinutes());
        m.put("step_remaining_seconds", cmd.getStepRemainingSeconds());
        m.put("status", cmd.getStatus());
        m.put("feedback_status_code", cmd.getFeedbackStatusCode());
        m.put("feedback_message", cmd.getFeedbackMessage());
        m.put("created_at", cmd.getCreatedAt());
        m.put("updated_at", cmd.getUpdatedAt());
        m.put("executed_at", cmd.getExecutedAt());
        
        return m;
    }

    /**
     * 将 ReliabilityLabData 转换为与 /iot/data/latest 相同的格式
     */
    private Map<String, Object> convertToLatestResponse(ReliabilityLabData data) {
        Map<String, Object> m = new HashMap<>();
        if (data == null) return m;
        m.put("device_id", data.getDeviceId());
        m.put("temperature", data.getTemperature());
        m.put("humidity", data.getHumidity());
        m.put("set_temperature", data.getSetTemperature());
        m.put("set_humidity", data.getSetHumidity());
        m.put("power_temperature", data.getPowerTemperature());
        m.put("power_humidity", data.getPowerHumidity());
        m.put("run_mode", data.getRunMode());
        m.put("run_status", data.getRunStatus());
        m.put("run_hours", data.getRunHours());
        m.put("run_minutes", data.getRunMinutes());
        m.put("run_seconds", data.getRunSeconds());
        m.put("set_program_number", data.getSetProgramNumber());
        m.put("set_run_status", data.getSetRunStatus());
        m.put("total_steps", data.getTotalSteps());
        m.put("running_step", data.getRunningStep());
        m.put("step_remaining_hours", data.getStepRemainingHours());
        m.put("step_remaining_minutes", data.getStepRemainingMinutes());
        m.put("step_remaining_seconds", data.getStepRemainingSeconds());
        m.put("created_at", data.getCreatedAt());
        m.put("updated_at", data.getUpdatedAt());
        return m;
    }

    private Map<String, Object> convertToDeviceResponse(ReliabilityLabData data) {
        Map<String, Object> response = convertToLatestResponse(data);
        response.put("id", data.getId());
        return response;
    }

    /**
     * 创建执行命令
     * POST /iot/createCommand
     * 请求体格式与 /iot/data 相同，支持所有命令参数字段
     * {
     *   "device_id": "DEVICE001",
     *   "set_temperature": 25.0,
     *   "set_humidity": 60.0,
     *   "power_temperature": "ON",
     *   ...
     * }
     */
    @PostMapping(value = "/iot/createCommand", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createCommand(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String deviceId = asText(payload.get("device_id"));
            if (deviceId == null || deviceId.isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 创建命令对象
            DeviceCommand command = new DeviceCommand();
            command.setDeviceId(deviceId);
            
            // 解析命令参数字段（与 /iot/data 格式相同）
            command.setSetTemperature(toScale(payload.get("set_temperature")));
            command.setSetHumidity(toScale(payload.get("set_humidity")));
            command.setPowerTemperature(asText(payload.get("power_temperature")));
            command.setPowerHumidity(asText(payload.get("power_humidity")));
            command.setRunMode(asText(payload.get("run_mode")));
            command.setRunStatus(asText(payload.get("run_status")));
            command.setRunHours(asText(payload.get("run_hours")));
            command.setRunMinutes(asText(payload.get("run_minutes")));
            command.setRunSeconds(asText(payload.get("run_seconds")));
            command.setSetProgramNumber(asText(payload.get("set_program_number")));
            command.setSetRunStatus(asText(payload.get("set_run_status")));
            command.setTotalSteps(asText(payload.get("total_steps")));
            command.setRunningStep(asText(payload.get("running_step")));
            command.setStepRemainingHours(asText(payload.get("step_remaining_hours")));
            command.setStepRemainingMinutes(asText(payload.get("step_remaining_minutes")));
            command.setStepRemainingSeconds(asText(payload.get("step_remaining_seconds")));
            
            // command字段已从数据库表中删除，不再设置
            // command.setCommand(toJsonString(payload));
            
            // 默认状态为pending
            command.setStatus("pending");
            
            // 插入数据库
            int result = deviceCommandDao.insert(command);
            
            Map<String, Object> resp = new HashMap<>();
            if (result > 0) {
                resp.put("success", true);
                resp.put("message", "命令创建成功");
                resp.put("id", command.getId());
            } else {
                resp.put("success", false);
                resp.put("message", "命令创建失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "创建命令失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 接收用户反馈的状态码和信息
     * POST /iot/feedbackCommand
     * 请求体格式：
     * {
     *   "id": 1,  // 命令ID（必填）
     *   "status": "success" 或 "failed",  // 执行状态（必填）
     *   "feedback_status_code": "200",  // 反馈状态码（可选）
     *   "feedback_message": "执行成功"  // 反馈信息（可选）
     * }
     */
    @PostMapping(value = "/iot/feedbackCommand", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> feedbackCommand(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            Object idObj = payload.get("id");
            Object statusObj = payload.get("status");
            
            if (idObj == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "命令ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            if (statusObj == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "执行状态不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            Long id;
            try {
                if (idObj instanceof Number) {
                    id = ((Number) idObj).longValue();
                } else {
                    id = Long.parseLong(String.valueOf(idObj));
                }
            } catch (Exception e) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "命令ID格式错误");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            String status = String.valueOf(statusObj);
            if (!"success".equals(status) && !"failed".equals(status)) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "执行状态必须是 success 或 failed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 检查命令是否存在
            DeviceCommand command = deviceCommandDao.selectById(id);
            if (command == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "命令不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 获取反馈信息
            String feedbackStatusCode = asText(payload.get("feedback_status_code"));
            String feedbackMessage = asText(payload.get("feedback_message"));
            
            // 更新反馈信息
            int updateCount = deviceCommandDao.updateFeedback(id, status, feedbackStatusCode, feedbackMessage);
            
            Map<String, Object> resp = new HashMap<>();
            if (updateCount > 0) {
                resp.put("success", true);
                resp.put("message", "反馈信息已更新");
            } else {
                resp.put("success", false);
                resp.put("message", "更新失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "处理反馈失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/iot/cache/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            Map<String, Object> stats = deviceCacheService.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "获取缓存统计失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 清除所有设备缓存
     */
    @PostMapping("/iot/cache/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearCache() {
        try {
            deviceCacheService.clearAllDeviceCache();
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "缓存已清除");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "清除缓存失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
}


