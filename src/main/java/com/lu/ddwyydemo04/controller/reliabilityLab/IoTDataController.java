package com.lu.ddwyydemo04.controller.reliabilityLab;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.dao.DeviceCommandDao;
import com.lu.ddwyydemo04.Service.DeviceCacheService;
import com.lu.ddwyydemo04.Service.RedisService;
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
     * 流程：
     * 1. 对比Redis缓存判断数据是否有变化
     * 2. 有变化：插入历史表 + 更新最新数据表 + 更新Redis缓存
     * 3. 无变化：仅更新Redis缓存（保持访问时间）
     */
    private void processAndInsert(Map<String, Object> payload) {
        // 预处理数值字段（两位小数），允许空
        BigDecimal temperature = toScale(payload.get("temperature"));
        BigDecimal humidity = toScale(payload.get("humidity"));
        BigDecimal setTemperature = toScale(payload.get("set_temperature"));
        BigDecimal setHumidity = toScale(payload.get("set_humidity"));

        // 构建数据对象
        ReliabilityLabData newData = new ReliabilityLabData();
        String deviceId = asText(payload.get("device_id"));
        
        // 验证设备ID
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new RuntimeException("设备ID不能为空");
        }
        
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
        newData.setProgramCycles(asText(payload.get("program_cycles")));
        newData.setProgramTotalCycles(asText(payload.get("program_total_cycles")));
        newData.setStepRemainingHours(asText(payload.get("step_remaining_hours")));
        newData.setStepRemainingMinutes(asText(payload.get("step_remaining_minutes")));
        newData.setStepRemainingSeconds(asText(payload.get("step_remaining_seconds")));
        newData.setSerialStatus(asText(payload.get("serial_status")));
        newData.setModuleConnection(asText(payload.get("module_connection")));

        // ========================================
        // 核心逻辑：对比Redis缓存，判断数据是否有变化
        // ========================================
        boolean hasChanges = hasDataChanged(deviceId, newData);

        if (hasChanges) {
            // ========================================
            // 数据有变化，执行完整的保存流程
            // ========================================
            System.out.println("[数据处理] 设备 " + deviceId + " 数据有变化，开始保存...");

            try {
                // 1. 插入到 reliabilityLabData 表（历史记录表）
                int insertResult = reliabilityLabDataDao.insert(newData);
                System.out.println("[数据处理] 1/3 - reliabilityLabData表插入" + (insertResult > 0 ? "成功" : "失败"));

                // 2. 更新或插入到 temperature_box_latest_data 表（最新数据表）
                ReliabilityLabData existingLatestData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
                if (existingLatestData != null) {
                    // 设备已存在，更新最新数据
                    int updateResult = reliabilityLabDataDao.updateLatestData(newData);
                    System.out.println("[数据处理] 2/3 - temperature_box_latest_data表更新" + (updateResult > 0 ? "成功" : "失败"));
                } else {
                    // 设备不存在，插入新记录
                    int insertLatestResult = reliabilityLabDataDao.insertLatestData(newData);
                    System.out.println("[数据处理] 2/3 - temperature_box_latest_data表插入" + (insertLatestResult > 0 ? "成功" : "失败"));
                }

                // 3. 更新Redis缓存为最新数据
                deviceCacheService.updateDeviceCache(deviceId, newData);
                System.out.println("[数据处理] 3/3 - Redis缓存更新成功");
                
                System.out.println("[数据处理] ✅ 设备 " + deviceId + " 数据保存完成");
            } catch (Exception e) {
                System.err.println("[数据处理] ❌ 设备 " + deviceId + " 数据保存失败: " + e.getMessage());
                throw e;
            }
        } else {
            // ========================================
            // 数据无变化，仅更新Redis缓存（刷新访问时间）
            // ========================================
            System.out.println("[数据处理] 设备 " + deviceId + " 数据无变化，仅刷新缓存时间");
            deviceCacheService.updateDeviceCache(deviceId, newData);
        }
    }

    /**
     * 比较新数据与Redis缓存中的数据是否有所不同
     * @param deviceId 设备ID
     * @param newData 新接收到的数据
     * @return true=数据有变化，false=数据无变化
     */
    private boolean hasDataChanged(String deviceId, ReliabilityLabData newData) {
        if (deviceId == null || deviceId.isEmpty()) {
            System.out.println("[数据对比] 设备ID为空，判定为有变化");
            return true; // 没有设备ID，认为有变化
        }

        // 从Redis缓存获取现有数据
        ReliabilityLabData existingData = deviceCacheService.getLatestDeviceData(deviceId);

        if (existingData == null) {
            System.out.println("[数据对比] 设备 " + deviceId + " 在Redis中无缓存，判定为新设备数据");
            return true; // 缓存中没有数据，认为有变化
        }

        // 比较关键字段是否发生变化
        boolean hasChanges = 
               !objectsEqual(newData.getTemperature(), existingData.getTemperature()) ||
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
               !stringsEqual(newData.getProgramCycles(), existingData.getProgramCycles()) ||
               !stringsEqual(newData.getProgramTotalCycles(), existingData.getProgramTotalCycles()) ||
               !stringsEqual(newData.getStepRemainingHours(), existingData.getStepRemainingHours()) ||
               !stringsEqual(newData.getStepRemainingMinutes(), existingData.getStepRemainingMinutes()) ||
               !stringsEqual(newData.getStepRemainingSeconds(), existingData.getStepRemainingSeconds()) ||
               !stringsEqual(newData.getSerialStatus(), existingData.getSerialStatus()) ||
               !stringsEqual(newData.getModuleConnection(), existingData.getModuleConnection());
        
        if (hasChanges) {
            System.out.println("[数据对比] 设备 " + deviceId + " 数据有变化");
            // 输出变化的字段（用于调试）
            logChangedFields(deviceId, newData, existingData);
        } else {
            System.out.println("[数据对比] 设备 " + deviceId + " 数据无变化");
        }
        
        return hasChanges;
    }
    
    /**
     * 记录变化的字段（调试用）
     */
    private void logChangedFields(String deviceId, ReliabilityLabData newData, ReliabilityLabData existingData) {
        StringBuilder changes = new StringBuilder();
        changes.append("[数据对比] 变化字段: ");
        
        if (!objectsEqual(newData.getTemperature(), existingData.getTemperature())) {
            changes.append(String.format("温度(%s→%s) ", existingData.getTemperature(), newData.getTemperature()));
        }
        if (!objectsEqual(newData.getHumidity(), existingData.getHumidity())) {
            changes.append(String.format("湿度(%s→%s) ", existingData.getHumidity(), newData.getHumidity()));
        }
        if (!stringsEqual(newData.getRunStatus(), existingData.getRunStatus())) {
            changes.append(String.format("运行状态(%s→%s) ", existingData.getRunStatus(), newData.getRunStatus()));
        }
        if (!stringsEqual(newData.getRunMode(), existingData.getRunMode())) {
            changes.append(String.format("运行模式(%s→%s) ", existingData.getRunMode(), newData.getRunMode()));
        }
        if (!stringsEqual(newData.getPowerTemperature(), existingData.getPowerTemperature())) {
            changes.append(String.format("温度功率(%s→%s) ", existingData.getPowerTemperature(), newData.getPowerTemperature()));
        }
        if (!stringsEqual(newData.getPowerHumidity(), existingData.getPowerHumidity())) {
            changes.append(String.format("湿度功率(%s→%s) ", existingData.getPowerHumidity(), newData.getPowerHumidity()));
        }
        
        if (changes.length() > 20) { // 有变化字段
            System.out.println(changes.toString());
        }
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
        // 使用新的统一Hash缓存
        List<ReliabilityLabData> records = deviceCacheService.getAllLatestDeviceData();
        List<Map<String, Object>> result = new ArrayList<>();
        if (records != null) {
            for (ReliabilityLabData record : records) {
                result.add(convertToDeviceResponse(record));
            }
        }

        // 注意：不再手动缓存，因为数据已在Hash缓存中
        // 如需额外缓存处理，可以在DeviceCacheService中实现
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
            // 查询所有命令（已按创建时间倒序排列）
            List<DeviceCommand> commands;
            if (device_id != null && !device_id.trim().isEmpty()) {
                commands = deviceCommandDao.selectByDeviceId(device_id);
            } else {
                commands = deviceCommandDao.selectAll();
            }
            
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
        m.put("valueorprogram", cmd.getValueorprogram());
        m.put("fixed_temp_set", cmd.getFixedTempSet());
        m.put("fixed_hum_set", cmd.getFixedHumSet());
        m.put("set_program_number", cmd.getSetProgramNumber());
        m.put("set_run_status", cmd.getSetRunStatus());
        m.put("set_program_no", cmd.getSetProgramNo());
        m.put("create_at", cmd.getCreateAt());
        m.put("create_by", cmd.getCreateBy());
        m.put("is_finished", cmd.getIsFinished());

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
        m.put("program_cycles", data.getProgramCycles());
        m.put("program_total_cycles", data.getProgramTotalCycles());
        m.put("step_remaining_hours", data.getStepRemainingHours());
        m.put("step_remaining_minutes", data.getStepRemainingMinutes());
        m.put("step_remaining_seconds", data.getStepRemainingSeconds());
        m.put("serial_status", data.getSerialStatus());
        m.put("module_connection", data.getModuleConnection());
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
     * 请求体格式（新表结构）：
     * {
     *   "device_id": "DEVICE001",
     *   "valueorprogram": "1",           // 定值或程式试验判断：0=程式，1=定值
     *   "fixed_temp_set": "25.0",        // 定值温度
     *   "fixed_hum_set": "60.0",         // 定值湿度
     *   "set_program_number": "001",     // 设定运行程式号
     *   "set_run_status": "1",           // 运行状态：0=停止，1=运行，2=暂停
     *   "set_program_no": "001",         // 设置程式号
     *   "create_by": "admin"             // 创建者
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

            // 解析命令参数字段（新表结构）
            command.setValueorprogram(asText(payload.get("valueorprogram")));
            command.setFixedTempSet(asText(payload.get("fixed_temp_set")));
            command.setFixedHumSet(asText(payload.get("fixed_hum_set")));
            command.setSetProgramNumber(asText(payload.get("set_program_number")));
            command.setSetRunStatus(asText(payload.get("set_run_status")));
            command.setSetProgramNo(asText(payload.get("set_program_no")));
            command.setCreateAt(java.time.LocalDateTime.now());
            command.setCreateBy(asText(payload.get("create_by")));
            command.setIsFinished(0); // 新创建的命令默认未完成
            
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
            
            // 在新表结构中，我们不再存储状态反馈信息
            // 直接返回成功响应，保持API兼容性
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "反馈信息已接收（新表结构不再存储状态反馈）");
            
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
     * 查看所有缓存内容（文本格式）
     */
    @GetMapping("/iot/cache/all")
    public ResponseEntity<String> getAllCache() {
        try {
            RedisService redisService = deviceCacheService.getRedisService();

            // 获取所有缓存键
            java.util.Set<String> allKeys = redisService.keys("*");

            // 返回文本格式
            StringBuilder textContent = new StringBuilder();
            textContent.append("=== Redis缓存内容报告 ===\n");
            textContent.append("生成时间: ").append(java.time.LocalDateTime.now()).append("\n");
            textContent.append("总缓存键数量: ").append(allKeys != null ? allKeys.size() : 0).append("\n\n");

            if (allKeys != null && !allKeys.isEmpty()) {
                // 按类型分组
                Map<String, List<String>> cacheGroups = new java.util.LinkedHashMap<>();
                cacheGroups.put("设备数据Hash缓存", new ArrayList<>());
                cacheGroups.put("设备命令缓存", new ArrayList<>());
                cacheGroups.put("钉钉用户缓存", new ArrayList<>());
                cacheGroups.put("钉钉API缓存", new ArrayList<>());
                cacheGroups.put("其他缓存", new ArrayList<>());

                for (String key : allKeys) {
                    Object value = null;
                    String valueType = "unknown";

                    try {
                        // 首先尝试获取为字符串类型
                        value = redisService.get(key);
                        valueType = "string";
                    } catch (Exception e) {
                        // 如果失败，可能是哈希类型或其他类型
                        try {
                            Map<Object, Object> hashValue = redisService.hGetAll(key);
                            if (hashValue != null && !hashValue.isEmpty()) {
                                value = hashValue;
                                valueType = "hash";
                            } else {
                                value = "无法获取的值 (可能是特殊类型)";
                                valueType = "other";
                            }
                        } catch (Exception e2) {
                            value = "无法获取的值: " + e2.getMessage();
                            valueType = "error";
                        }
                    }

                    Long expire = redisService.getExpire(key);

                    StringBuilder itemInfo = new StringBuilder();
                    itemInfo.append("键: ").append(key).append("\n");
                    itemInfo.append("类型: ").append(valueType).append("\n");
                    itemInfo.append("值: ").append(formatValue(value)).append("\n");
                    itemInfo.append("过期时间: ").append(formatExpireTime(expire)).append("\n");
                    itemInfo.append("---\n");

                    if (key.equals("device:data")) {
                        cacheGroups.get("设备数据Hash缓存").add(itemInfo.toString());
                    } else if (key.startsWith("device:command:")) {
                        cacheGroups.get("设备命令缓存").add(itemInfo.toString());
                    } else if (key.startsWith("dingtalk:user:info:")) {
                        // 新增：钉钉用户信息缓存分组
                        if (!cacheGroups.containsKey("钉钉用户缓存")) {
                            cacheGroups.put("钉钉用户缓存", new ArrayList<>());
                        }
                        cacheGroups.get("钉钉用户缓存").add(itemInfo.toString());
                    } else if (key.startsWith("dingtalk:")) {
                        // 新增：其他钉钉相关缓存
                        if (!cacheGroups.containsKey("钉钉API缓存")) {
                            cacheGroups.put("钉钉API缓存", new ArrayList<>());
                        }
                        cacheGroups.get("钉钉API缓存").add(itemInfo.toString());
                    } else {
                        cacheGroups.get("其他缓存").add(itemInfo.toString());
                    }
                }

                // 输出分组内容
                for (Map.Entry<String, List<String>> entry : cacheGroups.entrySet()) {
                    textContent.append("[").append(entry.getKey()).append("]\n");
                    textContent.append("数量: ").append(entry.getValue().size()).append("\n\n");

                    for (String item : entry.getValue()) {
                        textContent.append(item);
                    }
                    textContent.append("\n");
                }
            } else {
                textContent.append("当前没有缓存数据\n");
            }

            return ResponseEntity.ok()
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(textContent.toString());

        } catch (Exception e) {
            String errorText = "获取缓存失败: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(errorText);
        }
    }

    /**
     * 格式化缓存值用于文本显示
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            // 对于复杂对象，尝试转换为JSON格式
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    /**
     * 格式化过期时间
     */
    private String formatExpireTime(Long expireSeconds) {
        if (expireSeconds == null || expireSeconds == -1) {
            return "永不过期";
        } else if (expireSeconds == -2) {
            return "已过期";
        } else if (expireSeconds == 0) {
            return "即将过期";
        } else {
            long hours = expireSeconds / 3600;
            long minutes = (expireSeconds % 3600) / 60;
            long seconds = expireSeconds % 60;

            StringBuilder timeStr = new StringBuilder();
            if (hours > 0) {
                timeStr.append(hours).append("小时 ");
            }
            if (minutes > 0) {
                timeStr.append(minutes).append("分钟 ");
            }
            timeStr.append(seconds).append("秒");
            return timeStr.toString().trim();
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

    /**
     * 强制刷新设备数据（清除缓存并重新从数据库加载）
     */
    @PostMapping("/iot/data/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshDeviceData() {
        try {
            // 清除Redis缓存
            deviceCacheService.clearAllDeviceCache();

            // 重新从数据库加载所有设备数据到缓存
            List<ReliabilityLabData> allLatestData = reliabilityLabDataDao.selectAllLatestData();
            for (ReliabilityLabData data : allLatestData) {
                deviceCacheService.updateDeviceCache(data.getDeviceId(), data);
            }

            // 返回刷新后的数据
            List<Map<String, Object>> result = new ArrayList<>();
            if (allLatestData != null) {
                for (ReliabilityLabData record : allLatestData) {
                    result.add(convertToDeviceResponse(record));
                }
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "数据已从数据库重新加载");
            resp.put("data", result);
            resp.put("count", result.size());

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "刷新数据失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 获取指定设备未完成的命令
     * GET /iot/command/pending?device_id=xxx
     */
    @GetMapping("/iot/command/pending")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPendingCommand(@RequestParam("device_id") String deviceId) {
        try {
            DeviceCommand command = deviceCommandDao.selectPendingCommand(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            
            if (command != null) {
                result.put("command", convertDeviceCommandToMap(command));
            } else {
                result.put("command", null);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "获取未完成命令失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 标记命令为已完成
     * POST /iot/command/finish
     * 请求体: { "id": 123, "is_finished": 1 }
     */
    @PostMapping("/iot/command/finish")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> finishCommand(@RequestBody Map<String, Object> params) {
        try {
            Object idObj = params.get("id");
            Object isFinishedObj = params.get("is_finished");
            
            if (idObj == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "命令ID不能为空");
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
            
            Integer isFinished = 1; // 默认为已完成
            if (isFinishedObj != null) {
                try {
                    if (isFinishedObj instanceof Number) {
                        isFinished = ((Number) isFinishedObj).intValue();
                    } else {
                        isFinished = Integer.parseInt(String.valueOf(isFinishedObj));
                    }
                } catch (Exception e) {
                    // 使用默认值
                }
            }
            
            int rows = deviceCommandDao.updateFinishStatus(id, isFinished);
            
            Map<String, Object> resp = new HashMap<>();
            if (rows > 0) {
                resp.put("success", true);
                resp.put("message", "命令状态更新成功");
            } else {
                resp.put("success", false);
                resp.put("message", "命令状态更新失败，可能命令不存在");
            }
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "标记命令完成失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 添加新设备到监控系统
     * POST /iot/device/add
     * 请求体格式：
     * {
     *   "device_id": "DEVICE001",
     *   "create_by": "admin"
     * }
     * 
     * 说明：在temperature_box_latest_data表中创建一条初始记录，设备启动后会自动上报数据并更新
     */
    @PostMapping(value = "/iot/device/add", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addDevice(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String deviceId = asText(payload.get("device_id"));
            if (deviceId == null || deviceId.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 验证设备ID格式
            if (!deviceId.matches("^[a-zA-Z0-9\\-_\\u4e00-\\u9fa5]+$")) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备ID格式不正确，只能包含字母、数字、中划线、下划线和中文");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 检查设备是否已存在
            ReliabilityLabData existingDevice = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (existingDevice != null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备ID \"" + deviceId + "\" 已存在于监控系统中");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(resp);
            }
            
            // 创建初始设备记录
            ReliabilityLabData newDevice = new ReliabilityLabData();
            newDevice.setDeviceId(deviceId);
            
            // 设置默认值
            newDevice.setTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
            newDevice.setHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
            newDevice.setSetTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
            newDevice.setSetHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
            newDevice.setPowerTemperature("0");
            newDevice.setPowerHumidity("0");
            newDevice.setRunMode("1"); // 默认定值模式
            newDevice.setRunStatus("0"); // 初始状态：0=停止
            newDevice.setRunHours("0");
            newDevice.setRunMinutes("0");
            newDevice.setRunSeconds("0");
            newDevice.setSetProgramNumber("0");
            newDevice.setProgramNumber("0");
            newDevice.setSetRunStatus("0");
            newDevice.setTotalSteps("0");
            newDevice.setRunningStep("0");
            newDevice.setProgramStep("0");
            newDevice.setProgramCycles("0");
            newDevice.setProgramTotalCycles("0");
            newDevice.setStepRemainingHours("0");
            newDevice.setStepRemainingMinutes("0");
            newDevice.setStepRemainingSeconds("0");
            newDevice.setSerialStatus("离线"); // 初始状态为离线
            newDevice.setModuleConnection("连接异常"); // 初始状态为连接异常
            
            // 1. 先插入到 reliabilitylabdata 历史表（记录设备创建时间）
            int insertHistoryResult = reliabilityLabDataDao.insert(newDevice);
            System.out.println("[设备管理] 1/3 - reliabilitylabdata历史表插入" + 
                             (insertHistoryResult > 0 ? "成功" : "失败"));
            
            if (insertHistoryResult <= 0) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备添加失败，历史记录表插入失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            // 2. 再插入到 temperature_box_latest_data 最新数据表
            int insertLatestResult = reliabilityLabDataDao.insertLatestData(newDevice);
            System.out.println("[设备管理] 2/3 - temperature_box_latest_data最新数据表插入" + 
                             (insertLatestResult > 0 ? "成功" : "失败"));
            
            if (insertLatestResult <= 0) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备添加失败，最新数据表插入失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            // 3. 更新Redis缓存
            deviceCacheService.updateDeviceCache(deviceId, newDevice);
            System.out.println("[设备管理] 3/3 - Redis缓存更新成功");
            
            // 所有操作成功
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "设备添加成功");
            resp.put("device_id", deviceId);
            
            String createBy = asText(payload.get("create_by"));
            if (createBy != null && !createBy.trim().isEmpty()) {
                resp.put("create_by", createBy);
            }
            
            System.out.println("[设备管理] ✅ 新设备已完整添加: " + deviceId + 
                             (createBy != null ? " (创建者: " + createBy + ")" : "") +
                             " - 历史表、最新表、缓存均已更新");
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "添加设备失败: " + e.getMessage());
            System.err.println("[设备管理] 添加设备失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 删除设备（从temperature_box_latest_data表和Redis缓存中删除）
     * POST /iot/device/delete
     * 请求体格式：
     * {
     *   "device_id": "DEVICE001"
     * }
     * 
     * 说明：删除temperature_box_latest_data表中的设备记录，并清除Redis缓存
     * 注意：历史数据表(reliabilityLabData)中的数据不会被删除
     */
    @PostMapping(value = "/iot/device/delete", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteDevice(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String deviceId = asText(payload.get("device_id"));
            if (deviceId == null || deviceId.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 检查设备是否存在
            ReliabilityLabData existingDevice = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (existingDevice == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备ID \"" + deviceId + "\" 不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 1. 从temperature_box_latest_data表中删除设备记录
            int deleteResult = reliabilityLabDataDao.deleteLatestDataByDeviceId(deviceId);
            System.out.println("[设备管理] 1/2 - temperature_box_latest_data表删除" + 
                             (deleteResult > 0 ? "成功" : "失败"));
            
            if (deleteResult <= 0) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "删除设备失败，数据库操作失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            // 2. 清除Redis缓存
            deviceCacheService.deleteDeviceCache(deviceId);
            System.out.println("[设备管理] 2/2 - Redis缓存清除成功");
            
            // 所有操作成功
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "设备删除成功");
            resp.put("device_id", deviceId);
            
            System.out.println("[设备管理] ✅ 设备已完全删除: " + deviceId + 
                             " - 最新表、缓存均已清除");
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "删除设备失败: " + e.getMessage());
            System.err.println("[设备管理] 删除设备失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 接收命令执行结果
     * POST /iot/postExcuteResult
     * 请求体格式：
     * {
     *   "device_id": "DEVICE001",
     *   "isFinished": 0或1,
     *   "remark": "备注信息"
     * }
     * 
     * 说明：根据device_id更新该设备最新未完成命令的isFinished状态
     */
    @PostMapping(value = "/iot/postExcuteResult", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> postExecuteResult(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String deviceId = asText(payload.get("device_id"));
            if (deviceId == null || deviceId.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 解析isFinished字段
            Integer isFinished = null;
            Object isFinishedObj = payload.get("isFinished");
            if (isFinishedObj != null) {
                try {
                    if (isFinishedObj instanceof Number) {
                        isFinished = ((Number) isFinishedObj).intValue();
                    } else {
                        isFinished = Integer.parseInt(String.valueOf(isFinishedObj));
                    }
                } catch (Exception e) {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("success", false);
                    resp.put("message", "isFinished格式错误，必须是0或1");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
                }
            } else {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "isFinished不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 获取备注信息（可选）
            String remark = asText(payload.get("remark"));
            
            // 查找该设备最新的未完成命令
            DeviceCommand pendingCommand = deviceCommandDao.selectPendingCommand(deviceId);
            
            if (pendingCommand == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "未找到设备 " + deviceId + " 的待执行命令");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 更新命令的完成状态
            int rows = deviceCommandDao.updateFinishStatus(pendingCommand.getId(), isFinished);
            
            Map<String, Object> resp = new HashMap<>();
            if (rows > 0) {
                resp.put("success", true);
                resp.put("message", "命令执行状态已更新");
                resp.put("command_id", pendingCommand.getId());
                resp.put("device_id", deviceId);
                resp.put("isFinished", isFinished);
                if (remark != null && !remark.trim().isEmpty()) {
                    resp.put("remark", remark);
                }
                
                System.out.println("[命令执行反馈] 设备: " + deviceId + ", 命令ID: " + pendingCommand.getId() + 
                                 ", 状态: " + (isFinished == 1 ? "已完成" : "未完成") +
                                 (remark != null ? ", 备注: " + remark : ""));
            } else {
                resp.put("success", false);
                resp.put("message", "更新命令状态失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "处理执行结果失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
}


