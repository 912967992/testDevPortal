package com.lu.ddwyydemo04.controller.reliabilityLab;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.dao.DeviceCommandDao;
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

    public IoTDataController(ReliabilityLabDataDao reliabilityLabDataDao, DeviceCommandDao deviceCommandDao) {
        this.reliabilityLabDataDao = reliabilityLabDataDao;
        this.deviceCommandDao = deviceCommandDao;
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
     */
    private void processAndInsert(Map<String, Object> payload) {
        // 预处理数值字段（两位小数），允许空
        BigDecimal temperature = toScale(payload.get("temperature"));
        BigDecimal humidity = toScale(payload.get("humidity"));
        BigDecimal setTemperature = toScale(payload.get("set_temperature"));
        BigDecimal setHumidity = toScale(payload.get("set_humidity"));

        ReliabilityLabData data = new ReliabilityLabData();
        data.setDeviceId(asText(payload.get("device_id")));
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
        // raw_payload 字段已移除，不再设置
        // data.setRawPayload(toJsonString(payload));

        reliabilityLabDataDao.insert(data);
    }

    @GetMapping("/iot/data/latest")
    @ResponseBody
    public Map<String, Object> getLatest(@RequestParam(value = "device_id", required = false) String deviceId) {
        ReliabilityLabData data;
        if (deviceId != null && !deviceId.isEmpty()) {
            data = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
        } else {
            data = reliabilityLabDataDao.selectLatest();
        }
        if (data == null) {
            return new HashMap<>();
        }
        return convertToLatestResponse(data);
    }

    /**
     * 获取每台设备最新的监控数据
     */
    @GetMapping("/iot/data/devices")
    @ResponseBody
    public List<Map<String, Object>> getDevices() {
        List<ReliabilityLabData> records = reliabilityLabDataDao.selectLatestPerDevice();
        List<Map<String, Object>> result = new ArrayList<>();
        if (records != null) {
            for (ReliabilityLabData record : records) {
                result.add(convertToDeviceResponse(record));
            }
        }
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
}


