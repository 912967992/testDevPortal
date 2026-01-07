package com.lu.ddwyydemo04.controller.reliabilityLab;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.dao.DeviceCommandDao;
import com.lu.ddwyydemo04.dao.DeviceInfoDao;
import com.lu.ddwyydemo04.dao.UserDao;
import com.lu.ddwyydemo04.Service.DeviceCacheService;
import com.lu.ddwyydemo04.Service.RedisService;
import com.lu.ddwyydemo04.Service.AccessTokenService;
import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import com.lu.ddwyydemo04.pojo.DeviceCommand;
import com.lu.ddwyydemo04.pojo.DeviceInfo;
import com.lu.ddwyydemo04.pojo.User;
import com.taobao.api.ApiException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Controller
public class IoTDataController {

    private final ReliabilityLabDataDao reliabilityLabDataDao;
    private final DeviceCommandDao deviceCommandDao;
    private final DeviceInfoDao deviceInfoDao;
    private final DeviceCacheService deviceCacheService;
    private final UserDao userDao;
    private final AccessTokenService accessTokenService;

    public IoTDataController(ReliabilityLabDataDao reliabilityLabDataDao,
                           DeviceCommandDao deviceCommandDao,
                           DeviceInfoDao deviceInfoDao,
                           DeviceCacheService deviceCacheService,
                           UserDao userDao,
                           AccessTokenService accessTokenService) {
        this.reliabilityLabDataDao = reliabilityLabDataDao;
        this.deviceCommandDao = deviceCommandDao;
        this.deviceInfoDao = deviceInfoDao;
        this.deviceCacheService = deviceCacheService;
        this.userDao = userDao;
        this.accessTokenService = accessTokenService;
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
        
        // 从数据库或缓存获取现有数据，保留created_at字段
        ReliabilityLabData existingData = deviceCacheService.getLatestDeviceData(deviceId);
        if (existingData != null && existingData.getCreatedAt() != null) {
            // 保留原始的created_at
            newData.setCreatedAt(existingData.getCreatedAt());
            System.out.println("[数据处理] 设备 " + deviceId + " 保留原始创建时间: " + existingData.getCreatedAt());
        } else {
            // 如果没有现有数据，设置当前时间为创建时间（新设备）
            newData.setCreatedAt(java.time.LocalDateTime.now());
            System.out.println("[数据处理] 设备 " + deviceId + " 设置创建时间: " + newData.getCreatedAt());
        }
        // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
        // temperature_box_latest_data表的updated_at字段会在SQL中自动设置为NOW()
        // newData.setUpdatedAt(java.time.LocalDateTime.now());
        
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
        // set_program_number 和 program_number 都从 program_number 字段获取
        String programNumber = asText(payload.get("program_number"));
        newData.setSetProgramNumber(programNumber);
        newData.setProgramNumber(programNumber);
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
        // 自动关联当前正在测试的样品ID（更新 sample_id）
        // 同时保留 wait_id（预约等候的样品）
        // ========================================
        DeviceInfo currentSample = deviceInfoDao.selectCurrentTestingSample(deviceId);
        if (currentSample != null) {
            String currentSampleId = String.valueOf(currentSample.getId());
            // 追加到现有的 sample_id 中（使用之前已获取的 existingData）
            if (existingData != null && existingData.getSampleId() != null && !existingData.getSampleId().isEmpty()) {
                // 如果已存在 sample_id，检查是否已包含当前样品ID
                String existingSampleIds = existingData.getSampleId();
                if (!existingSampleIds.contains(currentSampleId)) {
                    newData.setSampleId(existingSampleIds + "," + currentSampleId);
                } else {
                    newData.setSampleId(existingSampleIds);
                }
            } else {
                // 如果不存在，直接设置
                newData.setSampleId(currentSampleId);
            }
            System.out.println("[数据处理] 设备 " + deviceId + " 自动关联样品ID: " + currentSampleId + " (" + currentSample.getCategory() + " - " + currentSample.getModel() + ")");
        } else {
            // 如果没有正在测试的样品，保留现有的 sample_id（使用之前已获取的 existingData）
            if (existingData != null) {
                newData.setSampleId(existingData.getSampleId());
            } else {
                newData.setSampleId(null);
            }
            System.out.println("[数据处理] 设备 " + deviceId + " 当前没有正在测试的样品");
        }
        
        // 保留现有的 wait_id（预约等候的样品）
        if (existingData != null) {
            newData.setWaitId(existingData.getWaitId());
        } else {
            newData.setWaitId(null);
        }

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
                
                // 4. 检查剩余时间变化，处理通知逻辑（10分钟和5分钟两次提醒）
                RedisService redisService = deviceCacheService.getRedisService();
                String notificationKey10min = "device:notification:sent:10min:" + deviceId;
                String notificationKey5min = "device:notification:sent:5min:" + deviceId;
                
                // 判断剩余时间阈值
                boolean wasTenMinutesOrMore = isRemainingTimeTenMinutesOrMore(existingData);
                boolean isLessThanTenMinutes = isRemainingTimeLessThanTenMinutes(newData);
                boolean wasFiveMinutesOrMore = isRemainingTimeFiveMinutesOrMore(existingData);
                boolean isLessThanFiveMinutes = isRemainingTimeLessThanFiveMinutes(newData);
                
                // 10分钟提醒：如果剩余时间从>=10分钟变为<10分钟，发送通知
                if (wasTenMinutesOrMore && isLessThanTenMinutes) {
                    // 检查是否已经发送过10分钟提醒（避免重复发送）
                    if (!redisService.hasKey(notificationKey10min)) {
                        System.out.println("[数据处理] 检测到剩余时间小于10分钟，准备发送10分钟提醒");
                        sendCompletionNotification(deviceId, newData, 10);
                        // 标记已发送10分钟提醒，设置过期时间为24小时（避免重复发送）
                        redisService.set(notificationKey10min, "1", 24, TimeUnit.HOURS);
                    } else {
                        System.out.println("[数据处理] 设备 " + deviceId + " 已发送过10分钟提醒，跳过");
                    }
                }
                // 如果剩余时间从<10分钟恢复到>=10分钟，清除10分钟提醒标记（允许下次再次发送）
                else if (!wasTenMinutesOrMore && !isLessThanTenMinutes) {
                    if (redisService.hasKey(notificationKey10min)) {
                        redisService.delete(notificationKey10min);
                        System.out.println("[数据处理] 设备 " + deviceId + " 剩余时间恢复到10分钟以上，清除10分钟提醒标记");
                    }
                }
                
                // 5分钟提醒：如果剩余时间从>=5分钟变为<5分钟，发送通知
                if (wasFiveMinutesOrMore && isLessThanFiveMinutes) {
                    // 检查是否已经发送过5分钟提醒（避免重复发送）
                    if (!redisService.hasKey(notificationKey5min)) {
                        System.out.println("[数据处理] 检测到剩余时间小于5分钟，准备发送5分钟提醒");
                        sendCompletionNotification(deviceId, newData, 5);
                        // 标记已发送5分钟提醒，设置过期时间为24小时（避免重复发送）
                        redisService.set(notificationKey5min, "1", 24, TimeUnit.HOURS);
                    } else {
                        System.out.println("[数据处理] 设备 " + deviceId + " 已发送过5分钟提醒，跳过");
                    }
                }
                // 如果剩余时间从<5分钟恢复到>=5分钟，清除5分钟提醒标记（允许下次再次发送）
                else if (!wasFiveMinutesOrMore && !isLessThanFiveMinutes) {
                    if (redisService.hasKey(notificationKey5min)) {
                        redisService.delete(notificationKey5min);
                        System.out.println("[数据处理] 设备 " + deviceId + " 剩余时间恢复到5分钟以上，清除5分钟提醒标记");
                    }
                }
                
                System.out.println("[数据处理] ✅ 设备 " + deviceId + " 数据保存完成");
            } catch (Exception e) {
                System.err.println("[数据处理] ❌ 设备 " + deviceId + " 数据保存失败: " + e.getMessage());
                throw e;
            }
        } else {
            // ========================================
            // 数据无变化，但需要更新数据库的 updated_at 字段和 Redis 缓存
            // 这样可以避免定时任务误判为连接异常
            // ========================================
            System.out.println("[数据处理] 设备 " + deviceId + " 数据无变化，刷新数据库更新时间");
            try {
                // 1. 更新 temperature_box_latest_data 表的 updated_at 字段（刷新更新时间，避免被判定为连接异常）
                int updateTimestampResult = reliabilityLabDataDao.updateLatestDataTimestamp(deviceId);
                System.out.println("[数据处理] 1/2 - temperature_box_latest_data表更新时间戳" + (updateTimestampResult > 0 ? "成功" : "失败"));
                
                // 2. 更新Redis缓存为最新数据（包含最新的 updated_at）
                // 重新从数据库获取最新数据（包含更新后的 updated_at）
                ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
                if (latestData != null) {
                    deviceCacheService.updateDeviceCache(deviceId, latestData);
                    System.out.println("[数据处理] 2/2 - Redis缓存更新成功（已包含最新更新时间）");
                } else {
                    // 如果数据库中没有记录，直接更新缓存（这种情况应该很少见）
                    deviceCacheService.updateDeviceCache(deviceId, newData);
                    System.out.println("[数据处理] 2/2 - Redis缓存更新成功（数据库无记录，使用新数据）");
                }
            } catch (Exception e) {
                System.err.println("[数据处理] ❌ 设备 " + deviceId + " 刷新更新时间失败: " + e.getMessage());
                // 即使更新失败，也尝试更新缓存（降级处理）
                try {
                    deviceCacheService.updateDeviceCache(deviceId, newData);
                } catch (Exception cacheException) {
                    System.err.println("[数据处理] ❌ 设备 " + deviceId + " 更新缓存也失败: " + cacheException.getMessage());
                }
            }
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

        // 判断关键字段是否发生变化（这些字段变化需要立即保存，不受时间间隔限制）
        // 注意：运行时间字段（runHours/runMinutes/runSeconds）不参与比较，运行时间变化不会触发插入
        boolean hasCriticalChanges = 
               !objectsEqual(newData.getSampleId(), existingData.getSampleId()) ||
               !objectsEqual(newData.getWaitId(), existingData.getWaitId()) ||
               // 设定温度是用户指令，任何变化都要立即触发保存
               !objectsEqual(newData.getSetTemperature(), existingData.getSetTemperature()) ||
               // 设定湿度是用户指令，任何变化都要立即触发保存
               !objectsEqual(newData.getSetHumidity(), existingData.getSetHumidity()) ||
               !stringsEqual(newData.getRunMode(), existingData.getRunMode()) ||
               !stringsEqual(newData.getRunStatus(), existingData.getRunStatus()) ||
               !stringsEqual(newData.getSetProgramNumber(), existingData.getSetProgramNumber()) ||
               !stringsEqual(newData.getProgramNumber(), existingData.getProgramNumber()) ||
               !stringsEqual(newData.getSetRunStatus(), existingData.getSetRunStatus()) ||
               !stringsEqual(newData.getTotalSteps(), existingData.getTotalSteps()) ||
               !stringsEqual(newData.getRunningStep(), existingData.getRunningStep()) ||
               !stringsEqual(newData.getProgramStep(), existingData.getProgramStep()) ||
               !stringsEqual(newData.getProgramCycles(), existingData.getProgramCycles()) ||
               !stringsEqual(newData.getProgramTotalCycles(), existingData.getProgramTotalCycles()) ||
               !stringsEqual(newData.getModuleConnection(), existingData.getModuleConnection());
               // 注意：运行时间字段（runHours/runMinutes/runSeconds）不参与比较，因为每秒都在变化，会导致频繁写入数据库

        // 判断非关键字段是否发生变化（这些字段变化需要检查时间间隔）
        // 注意：运行时间字段（runHours/runMinutes/runSeconds）不参与比较，运行时间变化不会触发插入
        boolean hasNonCriticalChanges = 
               // 实际温度变化超过1时触发保存
               isValueChanged(newData.getTemperature(), existingData.getTemperature(), 1.0) ||
               // 实际湿度变化超过1时触发保存
               isValueChanged(newData.getHumidity(), existingData.getHumidity(), 1.0) ||
               // 温度功率变化超过1时触发保存
               isPowerValueChanged(newData.getPowerTemperature(), existingData.getPowerTemperature(), 1.0) ||
               // 湿度功率变化超过1时触发保存
               isPowerValueChanged(newData.getPowerHumidity(), existingData.getPowerHumidity(), 1.0);
               // 注意：运行时间字段（runHours/runMinutes/runSeconds）不参与比较，因为每秒都在变化，会导致频繁写入数据库
        
        // 特殊处理：检测剩余时间跨过10分钟或5分钟阈值的情况
        // 当剩余时间跨过阈值时，需要触发保存（插入历史表+更新最新数据表+更新Redis缓存），以便发送通知
        boolean hasRemainingTimeChange = false;
        if (!hasCriticalChanges && !hasNonCriticalChanges) {
            // 检测是否从>=10分钟变为<10分钟（10分钟提醒阈值）
            boolean wasTenMinutesOrMore = isRemainingTimeTenMinutesOrMore(existingData);
            boolean isLessThanTenMinutes = isRemainingTimeLessThanTenMinutes(newData);
            if (wasTenMinutesOrMore && isLessThanTenMinutes) {
                hasRemainingTimeChange = true;
                System.out.println("[数据对比] 设备 " + deviceId + " 剩余时间小于10分钟，触发保存（10分钟提醒阈值）");
            }
            
            // 检测是否从>=5分钟变为<5分钟（5分钟提醒阈值）
            if (!hasRemainingTimeChange) {
                boolean wasFiveMinutesOrMore = isRemainingTimeFiveMinutesOrMore(existingData);
                boolean isLessThanFiveMinutes = isRemainingTimeLessThanFiveMinutes(newData);
                if (wasFiveMinutesOrMore && isLessThanFiveMinutes) {
                    hasRemainingTimeChange = true;
                    System.out.println("[数据对比] 设备 " + deviceId + " 剩余时间小于5分钟，触发保存（5分钟提醒阈值）");
                }
            }
        }
        
        // 如果有关键字段变化或剩余时间变化，立即返回true（需要保存）
        if (hasCriticalChanges || hasRemainingTimeChange) {
            System.out.println("[数据对比] 设备 " + deviceId + " 数据有变化（关键字段或剩余时间）");
            logChangedFields(deviceId, newData, existingData);
            return true;
        }
        
        // 如果只有非关键字段变化，需要检查时间间隔
        if (hasNonCriticalChanges) {
            // 查询设备最后一次插入的时间
            ReliabilityLabData lastInsertedData = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
            if (lastInsertedData != null && lastInsertedData.getCreatedAt() != null) {
                // 计算距离上次插入的时间间隔（秒）
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                java.time.Duration duration = java.time.Duration.between(lastInsertedData.getCreatedAt(), now);
                long secondsSinceLastInsert = duration.getSeconds();
                
                // 如果距离上次插入时间不足5分钟（300秒），不插入
                if (secondsSinceLastInsert < 300) {
                    System.out.println("[数据对比] 设备 " + deviceId + " 数据有变化，但距离上次插入仅 " + secondsSinceLastInsert + " 秒，不足5分钟，跳过插入");
                    return false;
                } else {
                    System.out.println("[数据对比] 设备 " + deviceId + " 数据有变化，距离上次插入 " + secondsSinceLastInsert + " 秒，超过5分钟，允许插入");
                    logChangedFields(deviceId, newData, existingData);
                    return true;
                }
            } else {
                // 如果查询不到上次插入时间，允许插入（可能是新设备）
                System.out.println("[数据对比] 设备 " + deviceId + " 数据有变化，但查询不到上次插入时间，允许插入");
                logChangedFields(deviceId, newData, existingData);
                return true;
            }
        }
        
        // 数据无变化
        System.out.println("[数据对比] 设备 " + deviceId + " 数据无变化");
        return false;
    }
    
    /**
     * 记录变化的字段（调试用）
     */
    private void logChangedFields(String deviceId, ReliabilityLabData newData, ReliabilityLabData existingData) {
        StringBuilder changes = new StringBuilder();
        changes.append("[数据对比] 变化字段: ");
        
        if (!objectsEqual(newData.getSampleId(), existingData.getSampleId())) {
            changes.append(String.format("样品ID(%s→%s) ", existingData.getSampleId(), newData.getSampleId()));
        }
        if (!objectsEqual(newData.getWaitId(), existingData.getWaitId())) {
            changes.append(String.format("预约样品ID(%s→%s) ", existingData.getWaitId(), newData.getWaitId()));
        }
        // 实际温度变化超过1时记录
        if (isValueChanged(newData.getTemperature(), existingData.getTemperature(), 1.0)) {
            changes.append(String.format("温度(%s→%s) ", existingData.getTemperature(), newData.getTemperature()));
        }
        // 实际湿度变化超过1时记录
        if (isValueChanged(newData.getHumidity(), existingData.getHumidity(), 1.0)) {
            changes.append(String.format("湿度(%s→%s) ", existingData.getHumidity(), newData.getHumidity()));
        }
        // 设定温度是用户指令，任何变化都要记录
        if (!objectsEqual(newData.getSetTemperature(), existingData.getSetTemperature())) {
            changes.append(String.format("设定温度(%s→%s) ", existingData.getSetTemperature(), newData.getSetTemperature()));
        }
        // 设定湿度是用户指令，任何变化都要记录
        if (!objectsEqual(newData.getSetHumidity(), existingData.getSetHumidity())) {
            changes.append(String.format("设定湿度(%s→%s) ", existingData.getSetHumidity(), newData.getSetHumidity()));
        }
        if (!stringsEqual(newData.getRunStatus(), existingData.getRunStatus())) {
            changes.append(String.format("运行状态(%s→%s) ", existingData.getRunStatus(), newData.getRunStatus()));
        }
        if (!stringsEqual(newData.getRunMode(), existingData.getRunMode())) {
            changes.append(String.format("运行模式(%s→%s) ", existingData.getRunMode(), newData.getRunMode()));
        }
        // 温度功率变化超过1时记录
        if (isPowerValueChanged(newData.getPowerTemperature(), existingData.getPowerTemperature(), 1.0)) {
            changes.append(String.format("温度功率(%s→%s) ", existingData.getPowerTemperature(), newData.getPowerTemperature()));
        }
        // 湿度功率变化超过1时记录
        if (isPowerValueChanged(newData.getPowerHumidity(), existingData.getPowerHumidity(), 1.0)) {
            changes.append(String.format("湿度功率(%s→%s) ", existingData.getPowerHumidity(), newData.getPowerHumidity()));
        }
        // 记录程序相关字段变化
        if (!stringsEqual(newData.getSetProgramNumber(), existingData.getSetProgramNumber())) {
            changes.append(String.format("设定程序号(%s→%s) ", existingData.getSetProgramNumber(), newData.getSetProgramNumber()));
        }
        if (!stringsEqual(newData.getProgramNumber(), existingData.getProgramNumber())) {
            changes.append(String.format("程序号(%s→%s) ", existingData.getProgramNumber(), newData.getProgramNumber()));
        }
        if (!stringsEqual(newData.getSetRunStatus(), existingData.getSetRunStatus())) {
            changes.append(String.format("设定运行状态(%s→%s) ", existingData.getSetRunStatus(), newData.getSetRunStatus()));
        }
        if (!stringsEqual(newData.getTotalSteps(), existingData.getTotalSteps())) {
            changes.append(String.format("总步数(%s→%s) ", existingData.getTotalSteps(), newData.getTotalSteps()));
        }
        if (!stringsEqual(newData.getRunningStep(), existingData.getRunningStep())) {
            changes.append(String.format("运行步数(%s→%s) ", existingData.getRunningStep(), newData.getRunningStep()));
        }
        if (!stringsEqual(newData.getProgramStep(), existingData.getProgramStep())) {
            changes.append(String.format("程序步数(%s→%s) ", existingData.getProgramStep(), newData.getProgramStep()));
        }
        if (!stringsEqual(newData.getProgramCycles(), existingData.getProgramCycles())) {
            changes.append(String.format("程序循环(%s→%s) ", existingData.getProgramCycles(), newData.getProgramCycles()));
        }
        if (!stringsEqual(newData.getProgramTotalCycles(), existingData.getProgramTotalCycles())) {
            changes.append(String.format("程序总循环(%s→%s) ", existingData.getProgramTotalCycles(), newData.getProgramTotalCycles()));
        }
        if (!stringsEqual(newData.getModuleConnection(), existingData.getModuleConnection())) {
            changes.append(String.format("模块连接(%s→%s) ", existingData.getModuleConnection(), newData.getModuleConnection()));
        }
        
        // 记录运行时间字段的变化（虽然不参与比较，但用于调试）
        if (!stringsEqual(newData.getRunHours(), existingData.getRunHours()) ||
            !stringsEqual(newData.getRunMinutes(), existingData.getRunMinutes()) ||
            !stringsEqual(newData.getRunSeconds(), existingData.getRunSeconds())) {
            changes.append(String.format("[运行时间变化但不触发插入] 运行时间(%s:%s:%s→%s:%s:%s) ", 
                existingData.getRunHours(), existingData.getRunMinutes(), existingData.getRunSeconds(),
                newData.getRunHours(), newData.getRunMinutes(), newData.getRunSeconds()));
        }
        
        // 记录功率值的实际变化（即使小于阈值也记录，用于调试）
        if (newData.getPowerTemperature() != null && existingData.getPowerTemperature() != null) {
            try {
                String newValStr = newData.getPowerTemperature().trim().replace("%", "");
                String existingValStr = existingData.getPowerTemperature().trim().replace("%", "");
                double newVal = Double.parseDouble(newValStr);
                double existingVal = Double.parseDouble(existingValStr);
                double diff = Math.abs(newVal - existingVal);
                if (diff > 0 && diff <= 1.0) {
                    changes.append(String.format("[功率温度小幅变化但不触发插入] 功率温度(%s→%s, 差值%.2f) ", 
                        existingData.getPowerTemperature(), newData.getPowerTemperature(), diff));
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
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

    /**
     * 判断BigDecimal类型的值变化是否超过阈值
     * @param newValue 新值
     * @param existingValue 旧值
     * @param threshold 阈值
     * @return true=变化超过阈值或值不同（null情况），false=变化未超过阈值
     */
    private boolean isValueChanged(BigDecimal newValue, BigDecimal existingValue, double threshold) {
        // 如果两个值都为null，认为没有变化
        if (newValue == null && existingValue == null) {
            return false;
        }
        // 如果其中一个为null，认为有变化
        if (newValue == null || existingValue == null) {
            return true;
        }
        // 计算差值的绝对值
        BigDecimal diff = newValue.subtract(existingValue).abs();
        // 如果差值大于阈值，认为有变化
        return diff.compareTo(BigDecimal.valueOf(threshold)) > 0;
    }

    /**
     * 判断功率值（String类型）变化是否超过阈值
     * @param newValue 新值（可能是"50%"或"50"格式）
     * @param existingValue 旧值（可能是"50%"或"50"格式）
     * @param threshold 阈值
     * @return true=变化超过阈值或值不同（null情况），false=变化未超过阈值
     */
    private boolean isPowerValueChanged(String newValue, String existingValue, double threshold) {
        // 如果两个值都为null，认为没有变化
        if (newValue == null && existingValue == null) {
            return false;
        }
        // 如果其中一个为null，认为有变化
        if (newValue == null || existingValue == null) {
            return true;
        }
        
        try {
            // 去除百分号并转换为数值
            String newValStr = newValue.trim().replace("%", "");
            String existingValStr = existingValue.trim().replace("%", "");
            
            double newVal = Double.parseDouble(newValStr);
            double existingVal = Double.parseDouble(existingValStr);
            
            // 计算差值的绝对值
            double diff = Math.abs(newVal - existingVal);
            // 如果差值大于阈值，认为有变化
            return diff > threshold;
        } catch (NumberFormatException e) {
            // 如果无法解析为数值，使用字符串比较
            return !newValue.trim().equals(existingValue.trim());
        }
    }

    /**
     * 判断剩余时间是否为零（所有字段都为null或"0"）
     * @param data 数据对象
     * @return true=剩余时间为0，false=剩余时间非0
     */
    private boolean isRemainingTimeZero(ReliabilityLabData data) {
        if (data == null) {
            return true;
        }
        String hours = data.getStepRemainingHours();
        String minutes = data.getStepRemainingMinutes();
        String seconds = data.getStepRemainingSeconds();
        
        // 判断是否所有字段都为null、空字符串或"0"
        boolean hoursIsZero = (hours == null || hours.trim().isEmpty() || "0".equals(hours.trim()));
        boolean minutesIsZero = (minutes == null || minutes.trim().isEmpty() || "0".equals(minutes.trim()));
        boolean secondsIsZero = (seconds == null || seconds.trim().isEmpty() || "0".equals(seconds.trim()));
        
        return hoursIsZero && minutesIsZero && secondsIsZero;
    }

    /**
     * 判断剩余时间是否非零（至少有一个字段不为null且不为"0"）
     * @param data 数据对象
     * @return true=剩余时间非0，false=剩余时间为0
     */
    private boolean isRemainingTimeNonZero(ReliabilityLabData data) {
        if (data == null) {
            return false;
        }
        String hours = data.getStepRemainingHours();
        String minutes = data.getStepRemainingMinutes();
        String seconds = data.getStepRemainingSeconds();
        
        // 判断是否至少有一个字段不为null、不为空且不为"0"
        boolean hoursIsNonZero = (hours != null && !hours.trim().isEmpty() && !"0".equals(hours.trim()));
        boolean minutesIsNonZero = (minutes != null && !minutes.trim().isEmpty() && !"0".equals(minutes.trim()));
        boolean secondsIsNonZero = (seconds != null && !seconds.trim().isEmpty() && !"0".equals(seconds.trim()));
        
        return hoursIsNonZero || minutesIsNonZero || secondsIsNonZero;
    }

    /**
     * 计算剩余时间的总秒数
     * @param data 数据对象
     * @return 剩余时间的总秒数，如果数据为null或无法解析则返回-1
     */
    private int getRemainingTimeInSeconds(ReliabilityLabData data) {
        if (data == null) {
            return -1;
        }
        try {
            String hoursStr = data.getStepRemainingHours();
            String minutesStr = data.getStepRemainingMinutes();
            String secondsStr = data.getStepRemainingSeconds();
            
            int hours = 0;
            int minutes = 0;
            int seconds = 0;
            
            if (hoursStr != null && !hoursStr.trim().isEmpty() && !"0".equals(hoursStr.trim())) {
                hours = Integer.parseInt(hoursStr.trim());
            }
            if (minutesStr != null && !minutesStr.trim().isEmpty() && !"0".equals(minutesStr.trim())) {
                minutes = Integer.parseInt(minutesStr.trim());
            }
            if (secondsStr != null && !secondsStr.trim().isEmpty() && !"0".equals(secondsStr.trim())) {
                seconds = Integer.parseInt(secondsStr.trim());
            }
            
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 判断剩余时间是否<=1分钟（60秒）
     * @param data 数据对象
     * @return true=剩余时间<=1分钟，false=剩余时间>1分钟或无法判断
     */
    private boolean isRemainingTimeOneMinuteOrLess(ReliabilityLabData data) {
        int totalSeconds = getRemainingTimeInSeconds(data);
        if (totalSeconds < 0) {
            // 无法解析，如果所有字段都是0或null，则认为<=1分钟
            return isRemainingTimeZero(data);
        }
        return totalSeconds <= 60;
    }

    /**
     * 判断剩余时间是否>1分钟（60秒）
     * @param data 数据对象
     * @return true=剩余时间>1分钟，false=剩余时间<=1分钟或无法判断
     */
    private boolean isRemainingTimeMoreThanOneMinute(ReliabilityLabData data) {
        if (data == null) {
            return false;
        }
        int totalSeconds = getRemainingTimeInSeconds(data);
        if (totalSeconds < 0) {
            // 无法解析，如果所有字段都是0或null，则认为<=1分钟
            return !isRemainingTimeZero(data);
        }
        return totalSeconds > 60;
    }

    /**
     * 判断剩余时间是否<10分钟（600秒）
     * @param data 数据对象
     * @return true=剩余时间<10分钟，false=剩余时间>=10分钟或无法判断
     */
    private boolean isRemainingTimeLessThanTenMinutes(ReliabilityLabData data) {
        int totalSeconds = getRemainingTimeInSeconds(data);
        if (totalSeconds < 0) {
            // 无法解析，如果所有字段都是0或null，则认为<10分钟
            return isRemainingTimeZero(data);
        }
        return totalSeconds < 600; // 10分钟 = 600秒
    }

    /**
     * 判断剩余时间是否>=10分钟（600秒）
     * @param data 数据对象
     * @return true=剩余时间>=10分钟，false=剩余时间<10分钟或无法判断
     */
    private boolean isRemainingTimeTenMinutesOrMore(ReliabilityLabData data) {
        if (data == null) {
            return false;
        }
        int totalSeconds = getRemainingTimeInSeconds(data);
        if (totalSeconds < 0) {
            // 无法解析，如果所有字段都是0或null，则认为<10分钟
            return !isRemainingTimeZero(data);
        }
        return totalSeconds >= 600; // 10分钟 = 600秒
    }

    /**
     * 判断剩余时间是否<5分钟（300秒）
     * @param data 数据对象
     * @return true=剩余时间<5分钟，false=剩余时间>=5分钟或无法判断
     */
    private boolean isRemainingTimeLessThanFiveMinutes(ReliabilityLabData data) {
        int totalSeconds = getRemainingTimeInSeconds(data);
        if (totalSeconds < 0) {
            // 无法解析，如果所有字段都是0或null，则认为<5分钟
            return isRemainingTimeZero(data);
        }
        return totalSeconds < 300; // 5分钟 = 300秒
    }

    /**
     * 判断剩余时间是否>=5分钟（300秒）
     * @param data 数据对象
     * @return true=剩余时间>=5分钟，false=剩余时间<5分钟或无法判断
     */
    private boolean isRemainingTimeFiveMinutesOrMore(ReliabilityLabData data) {
        if (data == null) {
            return false;
        }
        int totalSeconds = getRemainingTimeInSeconds(data);
        if (totalSeconds < 0) {
            // 无法解析，如果所有字段都是0或null，则认为<5分钟
            return !isRemainingTimeZero(data);
        }
        return totalSeconds >= 300; // 5分钟 = 300秒
    }

    /**
     * 当剩余时间达到阈值时，发送完成通知给相关测试人员
     * @param deviceId 设备ID
     * @param deviceData 设备当前数据
     * @param thresholdMinutes 提醒阈值（分钟），10表示10分钟提醒，5表示5分钟提醒
     */
    private void sendCompletionNotification(String deviceId, ReliabilityLabData deviceData, int thresholdMinutes) {
        try {
            System.out.println("[通知发送] 开始处理设备 " + deviceId + " 的剩余时间通知（阈值：" + thresholdMinutes + "分钟）");
            
            // 1. 获取当前测试区域（TESTING状态）和预约等候区域（WAITING状态）的样品
            List<DeviceInfo> allSamples = deviceInfoDao.selectAllByDeviceId(deviceId);
            if (allSamples == null || allSamples.isEmpty()) {
                System.out.println("[通知发送] 设备 " + deviceId + " 没有关联的样品信息，跳过通知");
                return;
            }
            
            // 筛选出测试中（TESTING）和预约等候（WAITING）的样品
            List<DeviceInfo> testingSamples = new ArrayList<>();
            List<DeviceInfo> waitingSamples = new ArrayList<>();
            
            for (DeviceInfo sample : allSamples) {
                if (DeviceInfo.STATUS_TESTING.equals(sample.getStatus())) {
                    testingSamples.add(sample);
                } else if (DeviceInfo.STATUS_WAITING.equals(sample.getStatus())) {
                    waitingSamples.add(sample);
                }
            }
            
            // 2. 收集所有需要通知的测试人员（去重）
            Set<String> testerNames = new HashSet<>();
            for (DeviceInfo sample : testingSamples) {
                if (sample.getTester() != null && !sample.getTester().trim().isEmpty()) {
                    testerNames.add(sample.getTester().trim());
                }
            }
            for (DeviceInfo sample : waitingSamples) {
                if (sample.getTester() != null && !sample.getTester().trim().isEmpty()) {
                    testerNames.add(sample.getTester().trim());
                }
            }
            
            if (testerNames.isEmpty()) {
                System.out.println("[通知发送] 设备 " + deviceId + " 没有找到测试人员信息，跳过通知");
                return;
            }
            
            System.out.println("[通知发送] 找到 " + testerNames.size() + " 位测试人员: " + testerNames);
            
            // 3. 根据测试人员名字查询User表获取userId列表
            List<String> userIdList = new ArrayList<>();
            for (String testerName : testerNames) {
                User user = userDao.selectByUsername(testerName);
                if (user != null && user.getUserId() != null && !user.getUserId().trim().isEmpty()) {
                    userIdList.add(user.getUserId());
                    System.out.println("[通知发送] 找到测试人员 " + testerName + " 的userId: " + user.getUserId());
                } else {
                    System.out.println("[通知发送] ⚠️ 未找到测试人员 " + testerName + " 的用户信息，跳过该用户");
                }
            }
            
            if (userIdList.isEmpty()) {
                System.out.println("[通知发送] ⚠️ 没有找到任何有效的userId，无法发送通知");
                return;
            }
            
            // 4. 构建通知内容
            String userIdListStr = String.join(",", userIdList);
            String title = "温箱测试剩余时间提醒";
            String markdownContent = buildNotificationContent(deviceId, deviceData, testingSamples, waitingSamples, thresholdMinutes);
            
            // 5. 发送通知
            boolean success = accessTokenService.sendDingTalkMarkdownNotification(
                userIdListStr,
                title,
                markdownContent
            );
            
            if (success) {
                System.out.println("[通知发送] ✅ 通知发送成功，接收用户数: " + userIdList.size());
            } else {
                System.out.println("[通知发送] ❌ 通知发送失败");
            }
            
        } catch (ApiException e) {
            System.err.println("[通知发送] ❌ 发送通知异常: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[通知发送] ❌ 处理通知异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 构建通知内容（Markdown格式）
     * @param deviceId 设备ID
     * @param deviceData 设备当前数据
     * @param testingSamples 测试中的样品列表
     * @param waitingSamples 预约等候的样品列表
     * @param thresholdMinutes 提醒阈值（分钟），10表示10分钟提醒，5表示5分钟提醒
     * @return Markdown格式的通知内容
     */
    private String buildNotificationContent(String deviceId, ReliabilityLabData deviceData, 
                                           List<DeviceInfo> testingSamples, List<DeviceInfo> waitingSamples, int thresholdMinutes) {
        StringBuilder content = new StringBuilder();
        
        // 标题（根据阈值显示不同的标题）
        if (thresholdMinutes == 10) {
            content.append("## ⏰ 温箱测试剩余时间提醒（10分钟）\n\n");
        } else if (thresholdMinutes == 5) {
            content.append("## ⚠️ 温箱测试剩余时间提醒（5分钟）\n\n");
        } else {
            content.append("## ⏰ 温箱测试剩余时间提醒\n\n");
        }
        
        // 计算并显示剩余时间
        int totalSeconds = getRemainingTimeInSeconds(deviceData);
        String remainingTimeText = "未知";
        if (totalSeconds >= 0) {
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;
            if (hours > 0) {
                remainingTimeText = String.format("%d小时%d分钟%d秒", hours, minutes, seconds);
            } else if (minutes > 0) {
                remainingTimeText = String.format("%d分钟%d秒", minutes, seconds);
            } else {
                remainingTimeText = String.format("%d秒", seconds);
            }
        }
        content.append("**⏳ 剩余时间**: ").append(remainingTimeText).append("\n\n");
        
        // 根据阈值显示不同的提示信息
        if (thresholdMinutes == 10) {
            content.append("> 💡 提示：设备测试剩余时间不足10分钟，请提前做好准备！\n\n");
        } else if (thresholdMinutes == 5) {
            content.append("> ⚠️ 警告：设备测试剩余时间不足5分钟，请立即关注！\n\n");
        } else {
            content.append("> 💡 提示：设备测试即将完成，请及时关注！\n\n");
        }
        
        // 设备信息
        content.append("**设备ID**: ").append(deviceId).append("\n\n");
        
        // 当前条件数据
        content.append("### 📊 当前条件数据\n\n");
        content.append("| 项目 | 数值 |\n");
        content.append("|------|------|\n");
        
        if (deviceData.getTemperature() != null) {
            content.append("| 温度 | ").append(deviceData.getTemperature()).append("°C |\n");
        }
        if (deviceData.getHumidity() != null) {
            content.append("| 湿度 | ").append(deviceData.getHumidity()).append("% |\n");
        }
        if (deviceData.getSetTemperature() != null) {
            content.append("| 设定温度 | ").append(deviceData.getSetTemperature()).append("°C |\n");
        }
        if (deviceData.getSetHumidity() != null) {
            content.append("| 设定湿度 | ").append(deviceData.getSetHumidity()).append("% |\n");
        }
        if (deviceData.getRunStatus() != null) {
            content.append("| 运行状态 | ").append(convertRunStatus(deviceData.getRunStatus())).append(" |\n");
        }
        if (deviceData.getRunMode() != null) {
            content.append("| 运行模式 | ").append(convertRunMode(deviceData.getRunMode())).append(" |\n");
        }
        
        content.append("\n");
        
        // 测试中的样品信息
        if (!testingSamples.isEmpty()) {
            content.append("### 🧪 当前测试区域\n\n");
            for (DeviceInfo sample : testingSamples) {
                content.append("- ");
                // 格式：型号-品类(测试人员：卢健) 创建时间
                String model = sample.getModel() != null ? sample.getModel() : "";
                String category = sample.getCategory() != null ? sample.getCategory() : "";
                String tester = sample.getTester() != null ? sample.getTester() : "";
                
                // 构建显示文本：型号-品类(测试人员：xxx)
                if (!model.isEmpty() && !category.isEmpty()) {
                    content.append(model).append("-").append(category);
                } else if (!model.isEmpty()) {
                    content.append(model);
                } else if (!category.isEmpty()) {
                    content.append(category);
                }
                
                if (!tester.isEmpty()) {
                    content.append("(测试人员：").append(tester).append(")");
                }
                
                // 添加创建时间
                if (sample.getCreatedAt() != null) {
                    String createdAtStr = sample.getCreatedAt().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    content.append(" ").append(createdAtStr);
                }
                
                content.append("\n");
            }
            content.append("\n");
        }
        
        // 预约等候的样品信息
        if (!waitingSamples.isEmpty()) {
            content.append("### ⏰ 预约等候区域\n\n");
            for (DeviceInfo sample : waitingSamples) {
                content.append("- ");
                // 格式：型号-品类(测试人员：卢健) 创建时间
                String model = sample.getModel() != null ? sample.getModel() : "";
                String category = sample.getCategory() != null ? sample.getCategory() : "";
                String tester = sample.getTester() != null ? sample.getTester() : "";
                
                // 构建显示文本：型号-品类(测试人员：xxx)
                if (!model.isEmpty() && !category.isEmpty()) {
                    content.append(model).append("-").append(category);
                } else if (!model.isEmpty()) {
                    content.append(model);
                } else if (!category.isEmpty()) {
                    content.append(category);
                }
                
                if (!tester.isEmpty()) {
                    content.append("(测试人员：").append(tester).append(")");
                }
                
                // 添加创建时间
                if (sample.getCreatedAt() != null) {
                    String createdAtStr = sample.getCreatedAt().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    content.append(" ").append(createdAtStr);
                }
                
                content.append("\n");
            }
            content.append("\n");
        }
        
        // 时间戳
        content.append("---\n\n");
        content.append("*通知时间: ").append(java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("*\n");
        
        return content.toString();
    }

    /**
     * 转换运行状态值
     * @param runStatus 运行状态值：0=停止, 1=运行, 2=暂停
     * @return 转换后的中文描述
     */
    private String convertRunStatus(String runStatus) {
        if (runStatus == null || runStatus.trim().isEmpty()) {
            return "未知";
        }
        String status = runStatus.trim();
        switch (status) {
            case "0":
                return "停止";
            case "1":
                return "运行";
            case "2":
                return "暂停";
            default:
                return status; // 如果值不在预期范围内，返回原值
        }
    }

    /**
     * 转换运行模式值
     * @param runMode 运行模式值：0=程式试验, 1=定值试验
     * @return 转换后的中文描述
     */
    private String convertRunMode(String runMode) {
        if (runMode == null || runMode.trim().isEmpty()) {
            return "未知";
        }
        String mode = runMode.trim();
        switch (mode) {
            case "0":
                return "程式试验";
            case "1":
                return "定值试验";
            default:
                return mode; // 如果值不在预期范围内，返回原值
        }
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
        m.put("timer_enabled", cmd.getTimerEnabled());
        m.put("timer_time", cmd.getTimerTime());
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
        m.put("sample_id", data.getSampleId()); // 正在测试中的样品ID
        m.put("wait_id", data.getWaitId()); // 预约等候的样品ID
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
        
        // 从 sample_id 和 wait_id 获取样品ID，然后查询 device_info 表获取详细信息
        List<Map<String, Object>> samplesList = new ArrayList<>();
        DeviceInfo firstTestingInfo = null; // 用于兼容旧代码的第一个正在测试中的样品信息
        
        // 1. 从 sample_id 获取正在测试中的样品ID
        if (data.getSampleId() != null && !data.getSampleId().trim().isEmpty()) {
            String[] sampleIds = data.getSampleId().split(",");
            for (String sampleIdStr : sampleIds) {
                sampleIdStr = sampleIdStr.trim();
                if (!sampleIdStr.isEmpty()) {
                    try {
                        Long sampleId = Long.parseLong(sampleIdStr);
                        DeviceInfo info = deviceInfoDao.selectById(sampleId);
                        if (info != null) {
                            Map<String, Object> sample = new HashMap<>();
                            sample.put("id", info.getId());
                            sample.put("deviceId", info.getDeviceId()); // 添加设备ID
                            sample.put("category", info.getCategory());
                            sample.put("model", info.getModel());
                            sample.put("tester", info.getTester());
                            sample.put("status", DeviceInfo.STATUS_TESTING); // 正在测试中
                            samplesList.add(sample);
                            
                            // 记录第一个正在测试中的样品（用于兼容旧代码）
                            if (firstTestingInfo == null) {
                                firstTestingInfo = info;
                            }
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("[样品信息] 无效的样品ID: " + sampleIdStr);
                    }
                }
            }
        }
        
        // 2. 从 wait_id 获取预约等候的样品ID
        if (data.getWaitId() != null && !data.getWaitId().trim().isEmpty()) {
            String[] waitIds = data.getWaitId().split(",");
            for (String waitIdStr : waitIds) {
                waitIdStr = waitIdStr.trim();
                if (!waitIdStr.isEmpty()) {
                    try {
                        Long waitId = Long.parseLong(waitIdStr);
                        DeviceInfo info = deviceInfoDao.selectById(waitId);
                        if (info != null) {
                            Map<String, Object> sample = new HashMap<>();
                            sample.put("id", info.getId());
                            sample.put("deviceId", info.getDeviceId()); // 添加设备ID
                            sample.put("category", info.getCategory());
                            sample.put("model", info.getModel());
                            sample.put("tester", info.getTester());
                            sample.put("status", DeviceInfo.STATUS_WAITING); // 预约等候
                            samplesList.add(sample);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("[样品信息] 无效的预约样品ID: " + waitIdStr);
                    }
                }
            }
        }
        
        response.put("samples", samplesList);
        
        // 兼容旧代码：返回第一个正在测试中的样品的信息（如果没有正在测试的样品则返回null）
        if (firstTestingInfo != null) {
            response.put("category", firstTestingInfo.getCategory());
            response.put("model", firstTestingInfo.getModel());
            response.put("tester", firstTestingInfo.getTester());
        } else {
            // 如果没有正在测试中的样品，返回空值
            response.put("category", null);
            response.put("model", null);
            response.put("tester", null);
        }
        
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
     *   "timer_enabled": "1",            // 定时运行开关：0=关闭，1=打开（可选）
     *   "timer_time": "230",              // 定时运行时间（H*100+M格式，例如230表示2小时30分钟）（可选）
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
            // 定时运行参数（可选）
            String timerEnabled = asText(payload.get("timer_enabled"));
            String timerTime = asText(payload.get("timer_time"));
            command.setTimerEnabled(timerEnabled);
            command.setTimerTime(timerTime);
            System.out.println("[创建命令] 定时运行参数 - timer_enabled: " + timerEnabled + ", timer_time: " + timerTime);
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
            
            // 3. 保存设备信息（品类、型号、测试人员）
            String category = asText(payload.get("category"));
            String model = asText(payload.get("model"));
            String tester = asText(payload.get("tester"));
            
            if (category != null || model != null || tester != null) {
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.setDeviceId(deviceId);
                deviceInfo.setCategory(category);
                deviceInfo.setModel(model);
                deviceInfo.setTester(tester);
                deviceInfo.setStatus(DeviceInfo.STATUS_WAITING); // 添加设备时默认状态为预约等候
                
                int deviceInfoResult = deviceInfoDao.insert(deviceInfo);
                System.out.println("[设备管理] 3/4 - device_info设备信息表插入" + 
                                 (deviceInfoResult > 0 ? "成功" : "失败"));
            }
            
            // 4. 更新Redis缓存（设备数据和样品信息）
            deviceCacheService.updateDeviceCache(deviceId, newDevice);
            // 如果有样品信息，初始化样品缓存
            if (category != null || model != null || tester != null) {
                deviceCacheService.refreshDeviceSamplesCache(deviceId);
            } else {
                // 即使没有样品，也初始化一个空的样品缓存
                deviceCacheService.updateDeviceSamplesCache(deviceId, new ArrayList<>());
            }
            System.out.println("[设备管理] 4/4 - Redis缓存更新成功（设备数据和样品信息）");
            
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
                             " - 历史表、最新表、设备信息表、缓存均已更新");
            
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
     * 获取设备的所有样品信息
     * GET /iot/device/samples?device_id=DEVICE001
     * 从 sample_id 和 wait_id 获取样品ID，然后查询 device_info 表获取详细信息
     */
    @GetMapping(value = "/iot/device/samples")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDeviceSamples(@RequestParam("device_id") String deviceId) {
        try {
            // 从 temperature_box_latest_data 表获取 sample_id 和 wait_id
            ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            List<Map<String, Object>> samplesList = new ArrayList<>();
            
            if (latestData != null) {
                // 1. 从 sample_id 获取正在测试中的样品ID
                if (latestData.getSampleId() != null && !latestData.getSampleId().trim().isEmpty()) {
                    String[] sampleIds = latestData.getSampleId().split(",");
                    for (String sampleIdStr : sampleIds) {
                        sampleIdStr = sampleIdStr.trim();
                        if (!sampleIdStr.isEmpty()) {
                            try {
                                Long sampleId = Long.parseLong(sampleIdStr);
                                DeviceInfo info = deviceInfoDao.selectById(sampleId);
                                if (info != null) {
                                    Map<String, Object> sample = new HashMap<>();
                                    sample.put("id", info.getId());
                                    sample.put("category", info.getCategory());
                                    sample.put("model", info.getModel());
                                    sample.put("tester", info.getTester());
                                    sample.put("status", info.getStatus()); // 使用实际状态
                                    sample.put("test_result", info.getTestResult()); // 添加测试结果字段
                                    sample.put("created_at", info.getCreatedAt());
                                    sample.put("updated_at", info.getUpdatedAt());
                                    samplesList.add(sample);
                                }
                            } catch (NumberFormatException e) {
                                System.err.println("[样品信息] 无效的样品ID: " + sampleIdStr);
                            }
                        }
                    }
                }
                
                // 2. 从 wait_id 获取预约等候的样品ID
                if (latestData.getWaitId() != null && !latestData.getWaitId().trim().isEmpty()) {
                    String[] waitIds = latestData.getWaitId().split(",");
                    for (String waitIdStr : waitIds) {
                        waitIdStr = waitIdStr.trim();
                        if (!waitIdStr.isEmpty()) {
                            try {
                                Long waitId = Long.parseLong(waitIdStr);
                                DeviceInfo info = deviceInfoDao.selectById(waitId);
                                if (info != null) {
                                    Map<String, Object> sample = new HashMap<>();
                                    sample.put("id", info.getId());
                                    sample.put("category", info.getCategory());
                                    sample.put("model", info.getModel());
                                    sample.put("tester", info.getTester());
                                    sample.put("status", info.getStatus()); // 使用实际状态
                                    sample.put("test_result", info.getTestResult()); // 添加测试结果字段
                                    sample.put("created_at", info.getCreatedAt());
                                    sample.put("updated_at", info.getUpdatedAt());
                                    samplesList.add(sample);
                                }
                            } catch (NumberFormatException e) {
                                System.err.println("[样品信息] 无效的预约样品ID: " + waitIdStr);
                            }
                        }
                    }
                }
            }
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("samples", samplesList);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "获取样品信息失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 根据样品ID列表批量查询样品信息（用于OEE分析）
     * POST /iot/samples/batch
     * 请求体格式：
     * {
     *   "sample_ids": [1, 2, 3, ...]
     * }
     */
    @PostMapping(value = "/iot/samples/batch", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSamplesByIds(@RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> sampleIdList = (List<Object>) payload.get("sample_ids");
            
            if (sampleIdList == null || sampleIdList.isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("samples", new ArrayList<>());
                return ResponseEntity.ok(resp);
            }
            
            // 转换为Long列表
            List<Long> sampleIds = new ArrayList<>();
            for (Object id : sampleIdList) {
                try {
                    if (id instanceof Number) {
                        sampleIds.add(((Number) id).longValue());
                    } else {
                        sampleIds.add(Long.parseLong(String.valueOf(id)));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("[批量查询样品] 无效的样品ID: " + id);
                }
            }
            
            if (sampleIds.isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("samples", new ArrayList<>());
                return ResponseEntity.ok(resp);
            }
            
            // 批量查询样品信息
            List<DeviceInfo> deviceInfoList = deviceInfoDao.selectByIds(sampleIds);
            
            // 转换为响应格式
            List<Map<String, Object>> samplesList = new ArrayList<>();
            for (DeviceInfo info : deviceInfoList) {
                Map<String, Object> sample = new HashMap<>();
                sample.put("id", info.getId());
                sample.put("device_id", info.getDeviceId());
                sample.put("category", info.getCategory());
                sample.put("model", info.getModel());
                sample.put("tester", info.getTester());
                sample.put("status", info.getStatus());
                sample.put("test_result", info.getTestResult());
                sample.put("created_at", info.getCreatedAt());
                sample.put("updated_at", info.getUpdatedAt());
                samplesList.add(sample);
            }
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("samples", samplesList);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "批量查询样品信息失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 添加样品信息
     * POST /iot/device/sample/add
     * 请求体格式：
     * {
     *   "device_id": "DEVICE001",
     *   "category": "手机",
     *   "model": "iPhone 15",
     *   "tester": "张三"
     * }
     */
    @PostMapping(value = "/iot/device/sample/add", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addSample(@RequestBody Map<String, Object> payload) {
        try {
            String deviceId = asText(payload.get("device_id"));
            
            // 检查是否为预约等候样品
            Object isWaitingObj = payload.get("is_waiting");
            boolean isWaiting = false;
            if (isWaitingObj != null) {
                if (isWaitingObj instanceof Boolean) {
                    isWaiting = (Boolean) isWaitingObj;
                } else if (isWaitingObj instanceof String) {
                    isWaiting = "true".equalsIgnoreCase((String) isWaitingObj);
                }
            }
            
            // 1. 先在 device_info 表里插入新样品的数据
            DeviceInfo newInfo = new DeviceInfo();
            newInfo.setDeviceId(deviceId);
            newInfo.setCategory(asText(payload.get("category")));
            newInfo.setModel(asText(payload.get("model")));
            newInfo.setTester(asText(payload.get("tester")));
            // 根据是否为预约设置状态：预约为WAITING，否则为TESTING（直接开始测试）
            newInfo.setStatus(isWaiting ? DeviceInfo.STATUS_WAITING : DeviceInfo.STATUS_TESTING);
            
            int insertResult = deviceInfoDao.insert(newInfo);
            if (insertResult <= 0) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品添加失败，无法插入到 device_info 表");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            // 获取新插入的样品ID
            Long sampleId = newInfo.getId();
            if (sampleId == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品添加失败，无法获取样品ID");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            if (isWaiting) {
                // 预约等候样品：插入历史记录 + 更新 wait_id 到 temperature_box_latest_data 表
                System.out.println("[样品管理] 预约等候样品 - device_info表插入成功，样品ID: " + sampleId);
                
                String sampleIdStr = String.valueOf(sampleId);
                
                // 1. 复制当前设备的最新状态，插入一条新记录到 reliabilitylabdata 表
                ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
                if (latestData != null) {
                    // 复制最新记录的所有字段
                    ReliabilityLabData newHistoryRecord = new ReliabilityLabData();
                    newHistoryRecord.setDeviceId(latestData.getDeviceId());
                    newHistoryRecord.setTemperature(latestData.getTemperature());
                    newHistoryRecord.setHumidity(latestData.getHumidity());
                    newHistoryRecord.setSetTemperature(latestData.getSetTemperature());
                    newHistoryRecord.setSetHumidity(latestData.getSetHumidity());
                    newHistoryRecord.setPowerTemperature(latestData.getPowerTemperature());
                    newHistoryRecord.setPowerHumidity(latestData.getPowerHumidity());
                    newHistoryRecord.setRunMode(latestData.getRunMode());
                    newHistoryRecord.setRunStatus(latestData.getRunStatus());
                    newHistoryRecord.setRunHours(latestData.getRunHours());
                    newHistoryRecord.setRunMinutes(latestData.getRunMinutes());
                    newHistoryRecord.setRunSeconds(latestData.getRunSeconds());
                    newHistoryRecord.setSetProgramNumber(latestData.getSetProgramNumber());
                    newHistoryRecord.setProgramNumber(latestData.getProgramNumber());
                    newHistoryRecord.setSetRunStatus(latestData.getSetRunStatus());
                    newHistoryRecord.setTotalSteps(latestData.getTotalSteps());
                    newHistoryRecord.setRunningStep(latestData.getRunningStep());
                    newHistoryRecord.setProgramStep(latestData.getProgramStep());
                    newHistoryRecord.setProgramCycles(latestData.getProgramCycles());
                    newHistoryRecord.setProgramTotalCycles(latestData.getProgramTotalCycles());
                    newHistoryRecord.setStepRemainingHours(latestData.getStepRemainingHours());
                    newHistoryRecord.setStepRemainingMinutes(latestData.getStepRemainingMinutes());
                    newHistoryRecord.setStepRemainingSeconds(latestData.getStepRemainingSeconds());
                    newHistoryRecord.setSerialStatus(latestData.getSerialStatus());
                    newHistoryRecord.setModuleConnection(latestData.getModuleConnection());
                    
                    // 保留 sample_id（正在测试的样品）
                    newHistoryRecord.setSampleId(latestData.getSampleId());
                    
                    // 更新 wait_id：追加新的预约样品ID
                    String existingWaitId = latestData.getWaitId();
                    String updatedWaitId;
                    if (existingWaitId == null || existingWaitId.trim().isEmpty()) {
                        updatedWaitId = sampleIdStr;
                    } else {
                        // 检查是否已包含，如果没有则追加
                        if (!existingWaitId.contains(sampleIdStr)) {
                            updatedWaitId = existingWaitId + "," + sampleIdStr;
                        } else {
                            updatedWaitId = existingWaitId;
                        }
                    }
                    newHistoryRecord.setWaitId(updatedWaitId);
                    
                    // 设置时间戳
                    newHistoryRecord.setCreatedAt(java.time.LocalDateTime.now());
                    // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                    // newHistoryRecord.setUpdatedAt(java.time.LocalDateTime.now());
                    
                    // 插入新记录
                    int insertHistoryResult = reliabilityLabDataDao.insert(newHistoryRecord);
                    System.out.println("[样品管理] 2/4 - reliabilitylabdata历史表插入新记录" + 
                                     (insertHistoryResult > 0 ? "成功" : "失败") + 
                                     "，wait_id: " + updatedWaitId);
                } else {
                    // 如果该设备没有历史数据，创建一个初始记录
                    ReliabilityLabData newHistoryRecord = new ReliabilityLabData();
                    newHistoryRecord.setDeviceId(deviceId);
                    newHistoryRecord.setTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                    newHistoryRecord.setHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                    newHistoryRecord.setSetTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                    newHistoryRecord.setSetHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                    newHistoryRecord.setPowerTemperature("0");
                    newHistoryRecord.setPowerHumidity("0");
                    newHistoryRecord.setRunMode("1");
                    newHistoryRecord.setRunStatus("0");
                    newHistoryRecord.setRunHours("0");
                    newHistoryRecord.setRunMinutes("0");
                    newHistoryRecord.setRunSeconds("0");
                    newHistoryRecord.setSetProgramNumber("0");
                    newHistoryRecord.setProgramNumber("0");
                    newHistoryRecord.setSetRunStatus("0");
                    newHistoryRecord.setTotalSteps("0");
                    newHistoryRecord.setRunningStep("0");
                    newHistoryRecord.setProgramStep("0");
                    newHistoryRecord.setProgramCycles("0");
                    newHistoryRecord.setProgramTotalCycles("0");
                    newHistoryRecord.setStepRemainingHours("0");
                    newHistoryRecord.setStepRemainingMinutes("0");
                    newHistoryRecord.setStepRemainingSeconds("0");
                    newHistoryRecord.setSerialStatus("离线");
                    newHistoryRecord.setModuleConnection("连接异常");
                    newHistoryRecord.setSampleId(null); // 预约等候样品，没有正在测试的样品
                    newHistoryRecord.setWaitId(sampleIdStr); // 设置预约样品ID
                    newHistoryRecord.setCreatedAt(java.time.LocalDateTime.now());
                    // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                    // newHistoryRecord.setUpdatedAt(java.time.LocalDateTime.now());
                    
                    int insertHistoryResult = reliabilityLabDataDao.insert(newHistoryRecord);
                    System.out.println("[样品管理] 2/4 - reliabilitylabdata历史表插入初始记录" + 
                                     (insertHistoryResult > 0 ? "成功" : "失败") + 
                                     "，wait_id: " + sampleIdStr);
                }
                
                // 2. 更新 temperature_box_latest_data 表的 wait_id
                ReliabilityLabData latestDataInTempBox = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
                if (latestDataInTempBox != null) {
                    String existingWaitId = latestDataInTempBox.getWaitId();
                    
                    // 追加到 wait_id
                    String updatedWaitId;
                    if (existingWaitId == null || existingWaitId.trim().isEmpty()) {
                        updatedWaitId = sampleIdStr;
                    } else {
                        // 检查是否已包含，如果没有则追加
                        if (!existingWaitId.contains(sampleIdStr)) {
                            updatedWaitId = existingWaitId + "," + sampleIdStr;
                        } else {
                            updatedWaitId = existingWaitId;
                        }
                    }
                    
                    latestDataInTempBox.setWaitId(updatedWaitId);
                    latestDataInTempBox.setUpdatedAt(java.time.LocalDateTime.now());
                    int updateLatestResult = reliabilityLabDataDao.updateLatestData(latestDataInTempBox);
                    System.out.println("[样品管理] 3/4 - temperature_box_latest_data表更新wait_id" + 
                                     (updateLatestResult > 0 ? "成功" : "失败") + 
                                     "，wait_id: " + updatedWaitId);
                    
                    // 更新Redis缓存
                    if (updateLatestResult > 0) {
                        deviceCacheService.updateDeviceCache(deviceId, latestDataInTempBox);
                    }
                } else {
                    System.out.println("[样品管理] 3/4 - temperature_box_latest_data表无记录，跳过更新wait_id");
                }
                
                // 3. 刷新Redis缓存中的样品信息
                deviceCacheService.refreshDeviceSamplesCache(deviceId);
                System.out.println("[样品管理] 4/4 - Redis样品缓存已刷新");
                
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "预约等候样品添加成功");
                resp.put("id", sampleId);
                resp.put("sample_id", sampleId);
                
                System.out.println("[样品管理] ✅ 预约等候样品已添加: " + deviceId + " (样品ID: " + sampleId + ")");
                return ResponseEntity.ok(resp);
            }
            
            System.out.println("[样品管理] 1/3 - device_info表插入成功，样品ID: " + sampleId);
            
            // 2. 复制当前设备的最新状态，插入一条新记录到 reliabilitylabdata 表
            ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
            if (latestData != null) {
                // 复制最新记录的所有字段
                ReliabilityLabData newHistoryRecord = new ReliabilityLabData();
                newHistoryRecord.setDeviceId(latestData.getDeviceId());
                newHistoryRecord.setTemperature(latestData.getTemperature());
                newHistoryRecord.setHumidity(latestData.getHumidity());
                newHistoryRecord.setSetTemperature(latestData.getSetTemperature());
                newHistoryRecord.setSetHumidity(latestData.getSetHumidity());
                newHistoryRecord.setPowerTemperature(latestData.getPowerTemperature());
                newHistoryRecord.setPowerHumidity(latestData.getPowerHumidity());
                newHistoryRecord.setRunMode(latestData.getRunMode());
                newHistoryRecord.setRunStatus(latestData.getRunStatus());
                newHistoryRecord.setRunHours(latestData.getRunHours());
                newHistoryRecord.setRunMinutes(latestData.getRunMinutes());
                newHistoryRecord.setRunSeconds(latestData.getRunSeconds());
                newHistoryRecord.setSetProgramNumber(latestData.getSetProgramNumber());
                newHistoryRecord.setProgramNumber(latestData.getProgramNumber());
                newHistoryRecord.setSetRunStatus(latestData.getSetRunStatus());
                newHistoryRecord.setTotalSteps(latestData.getTotalSteps());
                newHistoryRecord.setRunningStep(latestData.getRunningStep());
                newHistoryRecord.setProgramStep(latestData.getProgramStep());
                newHistoryRecord.setProgramCycles(latestData.getProgramCycles());
                newHistoryRecord.setProgramTotalCycles(latestData.getProgramTotalCycles());
                newHistoryRecord.setStepRemainingHours(latestData.getStepRemainingHours());
                newHistoryRecord.setStepRemainingMinutes(latestData.getStepRemainingMinutes());
                newHistoryRecord.setStepRemainingSeconds(latestData.getStepRemainingSeconds());
                newHistoryRecord.setSerialStatus(latestData.getSerialStatus());
                newHistoryRecord.setModuleConnection(latestData.getModuleConnection());
                
                // 保留 wait_id（预约等候的样品）
                newHistoryRecord.setWaitId(latestData.getWaitId());
                
                // 设置 sample_id：如果原记录有 sample_id，则追加；否则直接设置
                String sampleIdStr = String.valueOf(sampleId);
                if (latestData.getSampleId() != null && !latestData.getSampleId().isEmpty()) {
                    // 检查是否已包含，如果没有则追加
                    if (!latestData.getSampleId().contains(sampleIdStr)) {
                        newHistoryRecord.setSampleId(latestData.getSampleId() + "," + sampleIdStr);
                    } else {
                        newHistoryRecord.setSampleId(latestData.getSampleId());
                    }
                } else {
                    // 如果原记录没有 sample_id，直接设置
                    newHistoryRecord.setSampleId(sampleIdStr);
                }
                
                // 设置时间戳
                newHistoryRecord.setCreatedAt(java.time.LocalDateTime.now());
                // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                // newHistoryRecord.setUpdatedAt(java.time.LocalDateTime.now());
                
                // 插入新记录
                int insertHistoryResult = reliabilityLabDataDao.insert(newHistoryRecord);
                System.out.println("[样品管理] 2/3 - reliabilitylabdata历史表插入新记录" + 
                                 (insertHistoryResult > 0 ? "成功" : "失败") + 
                                 "，sample_id: " + newHistoryRecord.getSampleId());
            } else {
                // 如果该设备没有历史数据，创建一个初始记录
                ReliabilityLabData newHistoryRecord = new ReliabilityLabData();
                newHistoryRecord.setDeviceId(deviceId);
                newHistoryRecord.setTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                newHistoryRecord.setHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                newHistoryRecord.setSetTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                newHistoryRecord.setSetHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                newHistoryRecord.setPowerTemperature("0");
                newHistoryRecord.setPowerHumidity("0");
                newHistoryRecord.setRunMode("1");
                newHistoryRecord.setRunStatus("0");
                newHistoryRecord.setRunHours("0");
                newHistoryRecord.setRunMinutes("0");
                newHistoryRecord.setRunSeconds("0");
                newHistoryRecord.setSetProgramNumber("0");
                newHistoryRecord.setProgramNumber("0");
                newHistoryRecord.setSetRunStatus("0");
                newHistoryRecord.setTotalSteps("0");
                newHistoryRecord.setRunningStep("0");
                newHistoryRecord.setProgramStep("0");
                newHistoryRecord.setProgramCycles("0");
                newHistoryRecord.setProgramTotalCycles("0");
                newHistoryRecord.setStepRemainingHours("0");
                newHistoryRecord.setStepRemainingMinutes("0");
                newHistoryRecord.setStepRemainingSeconds("0");
                newHistoryRecord.setSerialStatus("离线");
                newHistoryRecord.setModuleConnection("连接异常");
                newHistoryRecord.setSampleId(String.valueOf(sampleId));
                newHistoryRecord.setWaitId(null); // 初始记录没有预约样品
                    newHistoryRecord.setCreatedAt(java.time.LocalDateTime.now());
                    // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                    // newHistoryRecord.setUpdatedAt(java.time.LocalDateTime.now());
                    
                    int insertHistoryResult = reliabilityLabDataDao.insert(newHistoryRecord);
                System.out.println("[样品管理] 2/3 - reliabilitylabdata历史表插入初始记录" + 
                                 (insertHistoryResult > 0 ? "成功" : "失败"));
            }
            
            // 3. 更新 temperature_box_latest_data 表的 sample_id
            ReliabilityLabData latestDataInTempBox = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (latestDataInTempBox != null) {
                String sampleIdStr = String.valueOf(sampleId);
                String existingSampleId = latestDataInTempBox.getSampleId();
                
                // 追加到 sample_id
                String updatedSampleId;
                if (existingSampleId == null || existingSampleId.trim().isEmpty()) {
                    updatedSampleId = sampleIdStr;
                } else {
                    // 检查是否已包含，如果没有则追加
                    if (!existingSampleId.contains(sampleIdStr)) {
                        updatedSampleId = existingSampleId + "," + sampleIdStr;
                    } else {
                        updatedSampleId = existingSampleId;
                    }
                }
                
                latestDataInTempBox.setSampleId(updatedSampleId);
                // 保留 wait_id（如果为null则保持null）
                if (latestDataInTempBox.getWaitId() == null) {
                    latestDataInTempBox.setWaitId(null);
                }
                latestDataInTempBox.setUpdatedAt(java.time.LocalDateTime.now());
                int updateLatestResult = reliabilityLabDataDao.updateLatestData(latestDataInTempBox);
                System.out.println("[样品管理] 3/3 - temperature_box_latest_data表更新sample_id" + 
                                 (updateLatestResult > 0 ? "成功" : "失败") + 
                                 "，sample_id: " + updatedSampleId);
                
                // 更新Redis缓存
                if (updateLatestResult > 0) {
                    deviceCacheService.updateDeviceCache(deviceId, latestDataInTempBox);
                    System.out.println("[样品管理] 3.1/3 - Redis设备数据缓存已更新");
                }
            } else {
                // 如果设备在 temperature_box_latest_data 表中不存在，创建一条初始记录
                ReliabilityLabData initialLatestData = new ReliabilityLabData();
                initialLatestData.setDeviceId(deviceId);
                initialLatestData.setSampleId(String.valueOf(sampleId));
                initialLatestData.setWaitId(null);
                initialLatestData.setTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                initialLatestData.setHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                initialLatestData.setSetTemperature(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                initialLatestData.setSetHumidity(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                initialLatestData.setPowerTemperature("0");
                initialLatestData.setPowerHumidity("0");
                initialLatestData.setRunMode("1");
                initialLatestData.setRunStatus("0");
                initialLatestData.setRunHours("0");
                initialLatestData.setRunMinutes("0");
                initialLatestData.setRunSeconds("0");
                initialLatestData.setSetProgramNumber("0");
                initialLatestData.setProgramNumber("0");
                initialLatestData.setSetRunStatus("0");
                initialLatestData.setTotalSteps("0");
                initialLatestData.setRunningStep("0");
                initialLatestData.setProgramStep("0");
                initialLatestData.setProgramCycles("0");
                initialLatestData.setProgramTotalCycles("0");
                initialLatestData.setStepRemainingHours("0");
                initialLatestData.setStepRemainingMinutes("0");
                initialLatestData.setStepRemainingSeconds("0");
                initialLatestData.setSerialStatus("离线");
                initialLatestData.setModuleConnection("连接异常");
                initialLatestData.setCreatedAt(java.time.LocalDateTime.now());
                initialLatestData.setUpdatedAt(java.time.LocalDateTime.now());
                
                int insertLatestResult = reliabilityLabDataDao.insertLatestData(initialLatestData);
                System.out.println("[样品管理] 3/3 - temperature_box_latest_data表插入初始记录" + 
                                 (insertLatestResult > 0 ? "成功" : "失败") + 
                                 "，sample_id: " + initialLatestData.getSampleId());
                
                // 更新Redis缓存
                if (insertLatestResult > 0) {
                    deviceCacheService.updateDeviceCache(deviceId, initialLatestData);
                    System.out.println("[样品管理] 3.1/3 - Redis设备数据缓存已更新");
                }
            }
            
            // 4. 刷新Redis缓存中的样品信息
            deviceCacheService.refreshDeviceSamplesCache(deviceId);
            System.out.println("[样品管理] 4/4 - Redis样品缓存已刷新");
            
            // 所有操作成功
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "样品添加成功");
            resp.put("id", sampleId);
            resp.put("sample_id", sampleId);
            
            System.out.println("[样品管理] ✅ 新样品已完整添加: " + deviceId + 
                             " (样品ID: " + sampleId + ")" +
                             " - device_info表、reliabilitylabdata表新记录、temperature_box_latest_data表、Redis缓存均已更新");
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "添加样品失败: " + e.getMessage());
            System.err.println("[样品管理] 添加样品失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 更新样品信息
     * POST /iot/device/sample/update
     * 请求体格式：
     * {
     *   "id": 1,
     *   "category": "手机",
     *   "model": "iPhone 15",
     *   "tester": "张三"
     * }
     */
    @PostMapping(value = "/iot/device/sample/update", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateSample(@RequestBody Map<String, Object> payload) {
        try {
            Long id = null;
            Object idObj = payload.get("id");
            if (idObj != null) {
                if (idObj instanceof Number) {
                    id = ((Number) idObj).longValue();
                } else {
                    id = Long.parseLong(idObj.toString());
                }
            }
            
            if (id == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            DeviceInfo existingInfo = deviceInfoDao.selectById(id);
            if (existingInfo == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            existingInfo.setCategory(asText(payload.get("category")));
            existingInfo.setModel(asText(payload.get("model")));
            existingInfo.setTester(asText(payload.get("tester")));
            // 如果传入了status参数，则更新状态；否则保留原有状态（不设置，让update方法中的if判断处理）
            String status = asText(payload.get("status"));
            if (status != null && !status.trim().isEmpty()) {
                existingInfo.setStatus(status);
            }
            // 如果没有传入status，existingInfo.getStatus()会保留原有值，update方法中的if判断会跳过status更新
            
            int updateResult = deviceInfoDao.update(existingInfo);
            if (updateResult > 0) {
                // 刷新Redis缓存中的样品信息
                deviceCacheService.refreshDeviceSamplesCache(existingInfo.getDeviceId());
                System.out.println("[样品管理] Redis样品缓存已刷新: " + existingInfo.getDeviceId());
                
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "样品更新成功");
                return ResponseEntity.ok(resp);
            } else {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品更新失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "更新样品失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 删除样品信息
     * POST /iot/device/sample/delete
     * 请求体格式：
     * {
     *   "id": 1
     * }
     */
    @PostMapping(value = "/iot/device/sample/delete", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteSample(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        try {
            Long id = null;
            Object idObj = payload.get("id");
            if (idObj != null) {
                if (idObj instanceof Number) {
                    id = ((Number) idObj).longValue();
                } else {
                    id = Long.parseLong(idObj.toString());
                }
            }
            
            if (id == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 先查询样品信息，获取 device_id 和 tester
            DeviceInfo sampleInfo = deviceInfoDao.selectById(id);
            if (sampleInfo == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 权限验证：只有测试人员与当前登录用户一致的样品才能删除
            HttpSession session = request.getSession(false);
            String username = null;
            
            // 首先尝试从 session 获取用户名
            if (session != null) {
                username = (String) session.getAttribute("username");
            }
            
            // 如果 session 中没有，尝试从请求参数中获取（兼容前端使用 localStorage 的情况）
            if (username == null || username.trim().isEmpty()) {
                username = asText(payload.get("username"));
            }
            
            // 获取样品的测试人员
            String sampleTester = sampleInfo.getTester();
            
            // 权限检查：只有样品的测试人员与当前登录用户一致时才能删除
            if (username == null || username.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "请先登录系统");
                System.out.println("[样品管理] ❌ 权限验证失败：用户未登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
            }
            
            if (sampleTester != null && !sampleTester.trim().isEmpty() && !sampleTester.equals(username)) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "您只能删除自己创建的样品！当前样品测试人员：" + sampleTester + "，当前登录用户：" + username);
                System.out.println("[样品管理] ❌ 权限验证失败：样品测试人员(" + sampleTester + ")与当前用户(" + username + ")不一致");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
            }
            
            System.out.println("[样品管理] ✅ 权限验证通过，用户名: " + username + "，样品ID: " + id);
            
            String deviceId = sampleInfo.getDeviceId();
            String sampleIdStr = String.valueOf(id);
            
            // 获取删除原因（status）：COMPLETED（已完成）或 CANCELLED（取消）
            String deleteStatus = asText(payload.get("status"));
            if (deleteStatus == null || deleteStatus.trim().isEmpty()) {
                // 如果没有提供status，默认使用COMPLETED（向后兼容）
                deleteStatus = DeviceInfo.STATUS_COMPLETED;
            }
            
            // 验证status值
            if (!DeviceInfo.STATUS_COMPLETED.equals(deleteStatus) && 
                !DeviceInfo.STATUS_CANCELLED.equals(deleteStatus)) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "无效的状态值，必须是 COMPLETED 或 CANCELLED");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 1. 更新 device_info 表的状态字段（不删除记录）
            // 通过更新 status 字段来标记样品状态，而不是删除记录
            // 这样 device_info 记录会保留，只是标记为已完成或已取消
            // 注意：update方法会更新所有字段，所以需要保留原有值
            DeviceInfo updateInfo = new DeviceInfo();
            updateInfo.setId(id);
            updateInfo.setDeviceId(sampleInfo.getDeviceId());
            updateInfo.setCategory(sampleInfo.getCategory()); // 保留原有值
            updateInfo.setModel(sampleInfo.getModel()); // 保留原有值
            updateInfo.setTester(sampleInfo.getTester()); // 保留原有值
            updateInfo.setStatus(deleteStatus); // 更新状态为已完成或已取消
            updateInfo.setUpdatedAt(java.time.LocalDateTime.now()); // 更新为当前时间
            
            int updateResult = deviceInfoDao.update(updateInfo);
            if (updateResult <= 0) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品状态更新失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            System.out.println("[样品管理] 1/3 - device_info表更新成功（标记为" + 
                             (DeviceInfo.STATUS_COMPLETED.equals(deleteStatus) ? "已完成" : "已取消") + 
                             "），样品ID: " + id + "，status: " + updateInfo.getStatus() + "，updated_at: " + updateInfo.getUpdatedAt());
            
            // 2. 复制当前设备的最新状态，插入新记录到 reliabilitylabdata 表，并从 sample_id 或 wait_id 中移除被删除的样品ID
            ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
            boolean needInsertHistory = false;
            if (latestData != null) {
                // 检查样品是在 sample_id 还是 wait_id 中
                boolean inSampleId = latestData.getSampleId() != null && latestData.getSampleId().contains(sampleIdStr);
                boolean inWaitId = latestData.getWaitId() != null && latestData.getWaitId().contains(sampleIdStr);
                
                if (inSampleId || inWaitId) {
                    needInsertHistory = true;
                    // 复制最新记录的所有字段
                    ReliabilityLabData newHistoryRecord = new ReliabilityLabData();
                    newHistoryRecord.setDeviceId(latestData.getDeviceId());
                    newHistoryRecord.setTemperature(latestData.getTemperature());
                    newHistoryRecord.setHumidity(latestData.getHumidity());
                    newHistoryRecord.setSetTemperature(latestData.getSetTemperature());
                    newHistoryRecord.setSetHumidity(latestData.getSetHumidity());
                    newHistoryRecord.setPowerTemperature(latestData.getPowerTemperature());
                    newHistoryRecord.setPowerHumidity(latestData.getPowerHumidity());
                    newHistoryRecord.setRunMode(latestData.getRunMode());
                    newHistoryRecord.setRunStatus(latestData.getRunStatus());
                    newHistoryRecord.setRunHours(latestData.getRunHours());
                    newHistoryRecord.setRunMinutes(latestData.getRunMinutes());
                    newHistoryRecord.setRunSeconds(latestData.getRunSeconds());
                    newHistoryRecord.setSetProgramNumber(latestData.getSetProgramNumber());
                    newHistoryRecord.setProgramNumber(latestData.getProgramNumber());
                    newHistoryRecord.setSetRunStatus(latestData.getSetRunStatus());
                    newHistoryRecord.setTotalSteps(latestData.getTotalSteps());
                    newHistoryRecord.setRunningStep(latestData.getRunningStep());
                    newHistoryRecord.setProgramStep(latestData.getProgramStep());
                    newHistoryRecord.setProgramCycles(latestData.getProgramCycles());
                    newHistoryRecord.setProgramTotalCycles(latestData.getProgramTotalCycles());
                    newHistoryRecord.setStepRemainingHours(latestData.getStepRemainingHours());
                    newHistoryRecord.setStepRemainingMinutes(latestData.getStepRemainingMinutes());
                    newHistoryRecord.setStepRemainingSeconds(latestData.getStepRemainingSeconds());
                    newHistoryRecord.setSerialStatus(latestData.getSerialStatus());
                    newHistoryRecord.setModuleConnection(latestData.getModuleConnection());
                    
                    // 从 sample_id 或 wait_id 中移除被删除的样品ID
                    if (inSampleId) {
                        String updatedSampleId = removeSampleIdFromString(latestData.getSampleId(), sampleIdStr);
                        newHistoryRecord.setSampleId(updatedSampleId);
                        newHistoryRecord.setWaitId(latestData.getWaitId()); // 保留 wait_id
                    }
                    if (inWaitId) {
                        String updatedWaitId = removeSampleIdFromString(latestData.getWaitId(), sampleIdStr);
                        newHistoryRecord.setWaitId(updatedWaitId);
                        newHistoryRecord.setSampleId(latestData.getSampleId()); // 保留 sample_id
                    }
                    
                    // 设置时间戳
                    newHistoryRecord.setCreatedAt(java.time.LocalDateTime.now());
                    // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                    // newHistoryRecord.setUpdatedAt(java.time.LocalDateTime.now());
                    
                    // 插入新记录
                    int insertHistoryResult = reliabilityLabDataDao.insert(newHistoryRecord);
                    System.out.println("[样品管理] 2/3 - reliabilitylabdata历史表插入新记录" + 
                                     (insertHistoryResult > 0 ? "成功" : "失败") + 
                                     "，移除样品ID后的sample_id: " + newHistoryRecord.getSampleId() + 
                                     "，wait_id: " + newHistoryRecord.getWaitId());
                }
            }
            
            if (!needInsertHistory) {
                System.out.println("[样品管理] 2/3 - reliabilitylabdata历史表无记录或sample_id/wait_id中不包含该样品ID，跳过插入");
            }
            
            // 3. 更新 temperature_box_latest_data 表，从 sample_id 或 wait_id 中移除被删除的样品ID
            ReliabilityLabData latestDataInTempBox = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (latestDataInTempBox != null) {
                boolean needUpdate = false;
                
                // 检查并更新 sample_id
                if (latestDataInTempBox.getSampleId() != null && latestDataInTempBox.getSampleId().contains(sampleIdStr)) {
                    String updatedSampleId = removeSampleIdFromString(latestDataInTempBox.getSampleId(), sampleIdStr);
                    latestDataInTempBox.setSampleId(updatedSampleId);
                    needUpdate = true;
                    System.out.println("[样品管理] 3/4 - 从sample_id中移除样品ID: " + sampleIdStr + "，更新后: " + updatedSampleId);
                }
                
                // 检查并更新 wait_id
                if (latestDataInTempBox.getWaitId() != null && latestDataInTempBox.getWaitId().contains(sampleIdStr)) {
                    String updatedWaitId = removeSampleIdFromString(latestDataInTempBox.getWaitId(), sampleIdStr);
                    latestDataInTempBox.setWaitId(updatedWaitId);
                    needUpdate = true;
                    System.out.println("[样品管理] 3/4 - 从wait_id中移除样品ID: " + sampleIdStr + "，更新后: " + updatedWaitId);
                }
                
                if (needUpdate) {
                    latestDataInTempBox.setUpdatedAt(java.time.LocalDateTime.now());
                    int updateLatestResult = reliabilityLabDataDao.updateLatestData(latestDataInTempBox);
                    System.out.println("[样品管理] 3/4 - temperature_box_latest_data最新数据表更新" + 
                                     (updateLatestResult > 0 ? "成功" : "失败"));
                    
                    // 3.1. 更新Redis缓存中的设备数据（确保sample_id和wait_id也被更新）
                    if (updateLatestResult > 0) {
                        deviceCacheService.updateDeviceCache(deviceId, latestDataInTempBox);
                        System.out.println("[样品管理] 3.1/4 - Redis设备数据缓存已更新（sample_id/wait_id已移除）");
                    }
                } else {
                    System.out.println("[样品管理] 3/4 - temperature_box_latest_data表无记录或sample_id/wait_id中不包含该样品ID，跳过更新");
                }
            } else {
                System.out.println("[样品管理] 3/4 - temperature_box_latest_data表无记录，跳过更新");
            }
            
            // 4. 刷新Redis缓存中的样品信息
            deviceCacheService.refreshDeviceSamplesCache(deviceId);
            System.out.println("[样品管理] 4/4 - Redis样品缓存已刷新");
            
            // 所有操作成功
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "样品删除成功");
            
            System.out.println("[样品管理] ✅ 样品测试已结束: " + deviceId + 
                             " (样品ID: " + id + ")" +
                             " - device_info表已更新（标记测试结束）、reliabilitylabdata表新记录、temperature_box_latest_data表、Redis缓存均已更新");
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "删除样品失败: " + e.getMessage());
            System.err.println("[样品管理] 删除样品失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 从 sample_id 字符串中移除指定的样品ID
     * 支持格式：单个ID "1"，多个ID "1,22,33"
     * @param sampleIdStr 原始的 sample_id 字符串
     * @param removeId 要移除的样品ID字符串
     * @return 移除后的 sample_id 字符串，如果移除后为空则返回 null
     */
    private String removeSampleIdFromString(String sampleIdStr, String removeId) {
        if (sampleIdStr == null || sampleIdStr.trim().isEmpty()) {
            return null;
        }
        
        // 如果只有一个ID且就是要移除的ID
        if (sampleIdStr.equals(removeId)) {
            return null;
        }
        
        // 处理多个ID的情况
        String[] ids = sampleIdStr.split(",");
        java.util.List<String> remainingIds = new java.util.ArrayList<>();
        for (String id : ids) {
            id = id.trim();
            if (!id.isEmpty() && !id.equals(removeId)) {
                remainingIds.add(id);
            }
        }
        
        if (remainingIds.isEmpty()) {
            return null;
        }
        
        return String.join(",", remainingIds);
    }
    
    /**
     * 更新设备信息（品类、型号、测试人员）- 兼容旧接口，现在用于添加第一个样品
     * POST /iot/device/updateInfo
     * 请求体格式：
     * {
     *   "device_id": "DEVICE001",
     *   "category": "手机",
     *   "model": "iPhone 15",
     *   "tester": "张三"
     * }
     * 
     * 说明：兼容旧接口，现在会添加一个新样品而不是更新
     */
    @PostMapping(value = "/iot/device/updateInfo", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateDeviceInfo(@RequestBody Map<String, Object> payload) {
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
            
            // 获取要添加的样品信息
            String category = asText(payload.get("category"));
            String model = asText(payload.get("model"));
            String tester = asText(payload.get("tester"));
            
            // 直接添加新样品（支持一个设备多个样品）
            DeviceInfo newInfo = new DeviceInfo();
            newInfo.setDeviceId(deviceId);
            newInfo.setCategory(category);
            newInfo.setModel(model);
            newInfo.setTester(tester);
            newInfo.setStatus(DeviceInfo.STATUS_WAITING); // 默认状态为预约等候
            int insertResult = deviceInfoDao.insert(newInfo);
            
            if (insertResult > 0) {
                System.out.println("[设备管理] ✅ 样品添加成功: " + deviceId);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "样品添加成功");
                resp.put("device_id", deviceId);
                resp.put("id", newInfo.getId());
                return ResponseEntity.ok(resp);
            } else {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品添加失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "更新设备信息失败: " + e.getMessage());
            System.err.println("[设备管理] 更新设备信息失败: " + e.getMessage());
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
     * 注意：
     * 1. 历史数据表(reliabilityLabData)中的数据不会被删除
     * 2. 样品信息表(device_info)中的数据不会被删除
     * 3. 只有"卢健"或"戴杏华"用户有权限执行此操作
     */
    @PostMapping(value = "/iot/device/delete", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteDevice(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        try {
            // 权限验证：只有"卢健"或"戴杏华"可以删除设备
            HttpSession session = request.getSession(false);
            String username = null;
            
            // 首先尝试从 session 获取用户名
            if (session != null) {
                username = (String) session.getAttribute("username");
            }
            
            // 如果 session 中没有，尝试从请求参数中获取（兼容前端使用 localStorage 的情况）
            if (username == null || username.trim().isEmpty()) {
                username = asText(payload.get("username"));
            }
            
            // 允许删除设备的用户名单
            List<String> allowedUsers = Arrays.asList("卢健", "戴杏华");
            
            if (username == null || username.trim().isEmpty() || !allowedUsers.contains(username)) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "您没有删除设备的权限。只有" + String.join("或", allowedUsers) + "可以删除设备。");
                System.out.println("[设备管理] ❌ 权限验证失败，用户名: " + (username != null && !username.trim().isEmpty() ? username : "未提供"));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
            }
            
            System.out.println("[设备管理] ✅ 权限验证通过，用户名: " + username);
            
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
            
            // 1. 从temperature_box_latest_data表中删除设备记录（只删除最新数据表）
            int deleteResult = reliabilityLabDataDao.deleteLatestDataByDeviceId(deviceId);
            System.out.println("[设备管理] 1/2 - temperature_box_latest_data表删除" + 
                             (deleteResult > 0 ? "成功" : "失败") + " (设备ID: " + deviceId + ", 操作人: " + username + ")");
            
            if (deleteResult <= 0) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "删除设备失败，数据库操作失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
            }
            
            // 2. 清除Redis缓存（包括设备数据和样品信息）
            deviceCacheService.deleteDeviceCache(deviceId);
            deviceCacheService.deleteDeviceSamplesCache(deviceId);
            System.out.println("[设备管理] 2/2 - Redis缓存清除成功（设备数据和样品信息）");
            
            // 所有操作成功
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "设备删除成功");
            resp.put("device_id", deviceId);
            
            System.out.println("[设备管理] ✅ 设备已从最新数据表删除: " + deviceId + 
                             " (操作人: " + username + ") - 已清除Redis缓存。注意：历史数据表和样品信息表的数据保持不变。");
            
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
    
    /**
     * 获取历史数据记录（用于OEE分析）
     * GET /iot/data/history
     * 请求参数：
     * - device_id: 设备ID（可选，不传则查询所有设备）
     * - time_range: 时间范围（today/week/month/quarter/year/custom）
     * - start_date: 开始日期（当time_range=custom时使用，格式：yyyy-MM-dd）
     * - end_date: 结束日期（当time_range=custom时使用，格式：yyyy-MM-dd）
     * - page: 页码（默认1）
     * - page_size: 每页数量（默认20）
     * 
     * 返回格式：
     * {
     *   "total": 100,
     *   "records": [...]
     * }
     */
    /**
     * 获取设备指令数据用于性能率计算
     * GET /iot/data/commands?device_id=xxx&time_range=month&start_date=xxx&end_date=xxx
     */
    @GetMapping("/iot/data/commands")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getCommandsData(
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "time_range", defaultValue = "month") String timeRange,
            @RequestParam(value = "start_date", required = false) String startDate,
            @RequestParam(value = "end_date", required = false) String endDate) {
        try {
            // 计算时间范围
            java.time.LocalDateTime startDateTime = null;
            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.now();
            
            if ("custom".equals(timeRange)) {
                // 自定义时间范围
                if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
                    try {
                        startDateTime = java.time.LocalDate.parse(startDate).atStartOfDay();
                        endDateTime = java.time.LocalDate.parse(endDate).atTime(23, 59, 59);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ArrayList<>());
                    }
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ArrayList<>());
                }
            } else {
                // 预设时间范围
                switch (timeRange) {
                    case "today":
                        startDateTime = java.time.LocalDate.now().atStartOfDay();
                        break;
                    case "week":
                        startDateTime = java.time.LocalDateTime.now().minusWeeks(1);
                        break;
                    case "month":
                        startDateTime = java.time.LocalDateTime.now().minusMonths(1);
                        break;
                    case "quarter":
                        startDateTime = java.time.LocalDateTime.now().minusMonths(3);
                        break;
                    case "year":
                        startDateTime = java.time.LocalDateTime.now().minusYears(1);
                        break;
                    default:
                        startDateTime = java.time.LocalDateTime.now().minusMonths(1);
                }
            }
            
            // 查询设备指令数据
            String startTimeStr = startDateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String endTimeStr = endDateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            List<DeviceCommand> commands = deviceCommandDao.selectByDeviceIdAndTimeRange(
                deviceId, startTimeStr, endTimeStr
            );
            
            // 转换为响应格式
            List<Map<String, Object>> result = new ArrayList<>();
            for (DeviceCommand cmd : commands) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", cmd.getId());
                data.put("device_id", cmd.getDeviceId());
                data.put("run_status", cmd.getSetRunStatus());
                data.put("is_finished", cmd.getIsFinished());
                data.put("created_at", cmd.getCreateAt());
                data.put("command_time", cmd.getCreateAt());
                result.add(data);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    @GetMapping("/iot/data/history")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHistoryData(
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "time_range", defaultValue = "month") String timeRange,
            @RequestParam(value = "start_date", required = false) String startDate,
            @RequestParam(value = "end_date", required = false) String endDate,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        try {
            // 计算时间范围
            java.time.LocalDateTime startDateTime = null;
            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.now();
            
            if ("custom".equals(timeRange)) {
                // 自定义时间范围
                if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
                    try {
                        startDateTime = java.time.LocalDate.parse(startDate).atStartOfDay();
                        endDateTime = java.time.LocalDate.parse(endDate).atTime(23, 59, 59);
                    } catch (Exception e) {
                        Map<String, Object> resp = new HashMap<>();
                        resp.put("success", false);
                        resp.put("message", "日期格式错误，应为 yyyy-MM-dd");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
                    }
                } else {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("success", false);
                    resp.put("message", "自定义时间范围需要提供 start_date 和 end_date");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
                }
            } else {
                // 预设时间范围
                switch (timeRange) {
                    case "today":
                        startDateTime = java.time.LocalDate.now().atStartOfDay();
                        break;
                    case "week":
                        startDateTime = java.time.LocalDateTime.now().minusWeeks(1);
                        break;
                    case "month":
                        startDateTime = java.time.LocalDateTime.now().minusMonths(1);
                        break;
                    case "quarter":
                        startDateTime = java.time.LocalDateTime.now().minusMonths(3);
                        break;
                    case "year":
                        startDateTime = java.time.LocalDateTime.now().minusYears(1);
                        break;
                    default:
                        startDateTime = java.time.LocalDateTime.now().minusMonths(1);
                }
            }
            
            // 从reliabilitylabdata历史表查询数据
            int offset = (page - 1) * pageSize;
            
            // 查询总数
            int total = reliabilityLabDataDao.countHistoryData(deviceId, startDateTime, endDateTime);
            
            // 查询分页数据
            List<ReliabilityLabData> historyData = reliabilityLabDataDao.selectHistoryData(
                deviceId, startDateTime, endDateTime, offset, pageSize
            );
            
            // 转换为响应格式
            List<Map<String, Object>> records = new ArrayList<>();
            for (ReliabilityLabData data : historyData) {
                records.add(convertToDeviceResponse(data));
            }
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("total", total);
            response.put("records", records);
            response.put("page", page);
            response.put("page_size", pageSize);
            response.put("total_pages", (int) Math.ceil((double) total / pageSize));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "查询历史数据失败: " + e.getMessage());
            resp.put("total", 0);
            resp.put("records", new ArrayList<>());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 将预约等候的样品转入测试阶段
     * POST /iot/sample/startTesting
     * 请求体格式：
     * {
     *   "sample_id": "123",
     *   "device_id": "DEVICE001"
     * }
     * 
     * 功能：
     * 1. 更新device_info表的status字段：从WAITING改为TESTING
     * 2. 更新temperature_box_latest_data表的wait_id字段：移除该样品ID
     * 3. 更新temperature_box_latest_data表的sample_id字段：添加该样品ID
     * 4. 向reliabilitylabdata表插入一条历史记录，记录样品转入测试的时间点
     * 5. 刷新Redis缓存
     */
    @PostMapping(value = "/iot/sample/startTesting", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startTestingSample(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String sampleIdStr = asText(payload.get("sample_id"));
            String deviceId = asText(payload.get("device_id"));
            
            if (sampleIdStr == null || sampleIdStr.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品ID和设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 解析样品ID
            Long sampleId = Long.parseLong(sampleIdStr);
            
            // 1. 更新device_info表的status字段：从WAITING改为TESTING
            DeviceInfo sample = deviceInfoDao.selectById(sampleId);
            if (sample == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 更新状态
            DeviceInfo updateInfo = new DeviceInfo();
            updateInfo.setId(sampleId);
            updateInfo.setDeviceId(sample.getDeviceId());
            updateInfo.setCategory(sample.getCategory());
            updateInfo.setModel(sample.getModel());
            updateInfo.setTester(sample.getTester());
            updateInfo.setStatus(DeviceInfo.STATUS_TESTING);
            updateInfo.setUpdatedAt(java.time.LocalDateTime.now());
            deviceInfoDao.update(updateInfo);
            
            // 2. 更新temperature_box_latest_data表的wait_id和sample_id字段
            ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (latestData == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "设备最新数据不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 从wait_id中移除该样品ID
            String waitId = latestData.getWaitId();
            String newWaitId = null;
            if (waitId != null && !waitId.trim().isEmpty()) {
                List<String> waitIdList = new ArrayList<>(Arrays.asList(waitId.split(",")));
                waitIdList.removeIf(id -> id.trim().equals(sampleIdStr));
                if (!waitIdList.isEmpty()) {
                    newWaitId = String.join(",", waitIdList);
                }
            }
            
            // 添加到sample_id中
            String sampleIdField = latestData.getSampleId();
            String newSampleId;
            if (sampleIdField == null || sampleIdField.trim().isEmpty()) {
                newSampleId = sampleIdStr;
            } else {
                List<String> sampleIdList = new ArrayList<>(Arrays.asList(sampleIdField.split(",")));
                if (!sampleIdList.contains(sampleIdStr)) {
                    sampleIdList.add(sampleIdStr);
                }
                newSampleId = String.join(",", sampleIdList);
            }
            
            // 更新数据库
            latestData.setWaitId(newWaitId);
            latestData.setSampleId(newSampleId);
            latestData.setUpdatedAt(java.time.LocalDateTime.now());
            reliabilityLabDataDao.updateLatestData(latestData);
            
            // 3. 向reliabilitylabdata表插入一条历史记录，记录样品转入测试的时间点
            ReliabilityLabData historyRecord = new ReliabilityLabData();
            historyRecord.setDeviceId(latestData.getDeviceId());
            historyRecord.setSampleId(newSampleId);
            historyRecord.setWaitId(newWaitId);
            historyRecord.setTemperature(latestData.getTemperature());
            historyRecord.setHumidity(latestData.getHumidity());
            historyRecord.setSetTemperature(latestData.getSetTemperature());
            historyRecord.setSetHumidity(latestData.getSetHumidity());
            historyRecord.setPowerTemperature(latestData.getPowerTemperature());
            historyRecord.setPowerHumidity(latestData.getPowerHumidity());
            historyRecord.setRunMode(latestData.getRunMode());
            historyRecord.setRunStatus(latestData.getRunStatus());
            historyRecord.setRunHours(latestData.getRunHours());
            historyRecord.setRunMinutes(latestData.getRunMinutes());
            historyRecord.setRunSeconds(latestData.getRunSeconds());
            historyRecord.setSetProgramNumber(latestData.getSetProgramNumber());
            historyRecord.setProgramNumber(latestData.getProgramNumber());
            historyRecord.setSetRunStatus(latestData.getSetRunStatus());
            historyRecord.setTotalSteps(latestData.getTotalSteps());
            historyRecord.setRunningStep(latestData.getRunningStep());
            historyRecord.setProgramStep(latestData.getProgramStep());
            historyRecord.setProgramCycles(latestData.getProgramCycles());
            historyRecord.setProgramTotalCycles(latestData.getProgramTotalCycles());
            historyRecord.setStepRemainingHours(latestData.getStepRemainingHours());
            historyRecord.setStepRemainingMinutes(latestData.getStepRemainingMinutes());
            historyRecord.setStepRemainingSeconds(latestData.getStepRemainingSeconds());
            historyRecord.setSerialStatus(latestData.getSerialStatus());
            historyRecord.setModuleConnection(latestData.getModuleConnection());
            historyRecord.setCreatedAt(java.time.LocalDateTime.now());
            // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
            // historyRecord.setUpdatedAt(java.time.LocalDateTime.now());
            reliabilityLabDataDao.insert(historyRecord);
            
            // 4. 刷新Redis缓存
            deviceCacheService.updateDeviceCache(deviceId, latestData);
            deviceCacheService.refreshDeviceSamplesCache(deviceId);
            
            // 返回成功响应
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "样品已成功转入测试阶段");
            
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "转入测试失败: " + e.getMessage());
            System.err.println("[样品管理] ❌ 转入测试失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 取消预约等候的样品测试
     * POST /iot/sample/cancelTesting
     * 请求体格式：
     * {
     *   "sample_id": "123",
     *   "device_id": "DEVICE001"
     * }
     * 
     * 功能：
     * 1. 更新device_info表的status字段：从WAITING改为CANCELLED
     * 2. 更新temperature_box_latest_data表的wait_id字段：移除该样品ID
     * 3. 向reliabilitylabdata表插入一条历史记录，记录样品取消的时间点
     * 4. 刷新Redis缓存
     */
    @PostMapping(value = "/iot/sample/cancelTesting", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelTestingSample(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String sampleIdStr = asText(payload.get("sample_id"));
            String deviceId = asText(payload.get("device_id"));
            
            if (sampleIdStr == null || sampleIdStr.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品ID和设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 解析样品ID
            Long sampleId = Long.parseLong(sampleIdStr);
            
            // 1. 更新device_info表的status字段：从WAITING改为CANCELLED
            DeviceInfo sample = deviceInfoDao.selectById(sampleId);
            if (sample == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 检查样品状态是否为WAITING
            if (!DeviceInfo.STATUS_WAITING.equals(sample.getStatus())) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "只能取消预约等候状态的样品");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 更新状态
            DeviceInfo updateInfo = new DeviceInfo();
            updateInfo.setId(sampleId);
            updateInfo.setDeviceId(sample.getDeviceId());
            updateInfo.setCategory(sample.getCategory());
            updateInfo.setModel(sample.getModel());
            updateInfo.setTester(sample.getTester());
            updateInfo.setStatus(DeviceInfo.STATUS_CANCELLED);
            updateInfo.setUpdatedAt(java.time.LocalDateTime.now());
            deviceInfoDao.update(updateInfo);
            
            // 2. 更新temperature_box_latest_data表的wait_id字段：移除该样品ID
            ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (latestData != null) {
                // 从wait_id中移除该样品ID
                String waitId = latestData.getWaitId();
                String newWaitId = null;
                if (waitId != null && !waitId.trim().isEmpty()) {
                    List<String> waitIdList = new ArrayList<>(Arrays.asList(waitId.split(",")));
                    waitIdList.removeIf(id -> id.trim().equals(sampleIdStr));
                    if (!waitIdList.isEmpty()) {
                        newWaitId = String.join(",", waitIdList);
                    }
                }
                
                // 更新数据库
                latestData.setWaitId(newWaitId);
                latestData.setUpdatedAt(java.time.LocalDateTime.now());
                reliabilityLabDataDao.updateLatestData(latestData);
                
                // 3. 向reliabilitylabdata表插入一条历史记录，记录样品取消的时间点
                ReliabilityLabData historyRecord = new ReliabilityLabData();
                historyRecord.setDeviceId(latestData.getDeviceId());
                historyRecord.setSampleId(latestData.getSampleId());
                historyRecord.setWaitId(newWaitId);
                historyRecord.setTemperature(latestData.getTemperature());
                historyRecord.setHumidity(latestData.getHumidity());
                historyRecord.setSetTemperature(latestData.getSetTemperature());
                historyRecord.setSetHumidity(latestData.getSetHumidity());
                historyRecord.setPowerTemperature(latestData.getPowerTemperature());
                historyRecord.setPowerHumidity(latestData.getPowerHumidity());
                historyRecord.setRunMode(latestData.getRunMode());
                historyRecord.setRunStatus(latestData.getRunStatus());
                historyRecord.setRunHours(latestData.getRunHours());
                historyRecord.setRunMinutes(latestData.getRunMinutes());
                historyRecord.setRunSeconds(latestData.getRunSeconds());
                historyRecord.setSetProgramNumber(latestData.getSetProgramNumber());
                historyRecord.setProgramNumber(latestData.getProgramNumber());
                historyRecord.setSetRunStatus(latestData.getSetRunStatus());
                historyRecord.setTotalSteps(latestData.getTotalSteps());
                historyRecord.setRunningStep(latestData.getRunningStep());
                historyRecord.setProgramStep(latestData.getProgramStep());
                historyRecord.setProgramCycles(latestData.getProgramCycles());
                historyRecord.setProgramTotalCycles(latestData.getProgramTotalCycles());
                historyRecord.setStepRemainingHours(latestData.getStepRemainingHours());
                historyRecord.setStepRemainingMinutes(latestData.getStepRemainingMinutes());
                historyRecord.setStepRemainingSeconds(latestData.getStepRemainingSeconds());
                historyRecord.setSerialStatus(latestData.getSerialStatus());
                historyRecord.setModuleConnection(latestData.getModuleConnection());
                historyRecord.setCreatedAt(java.time.LocalDateTime.now());
                // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                // historyRecord.setUpdatedAt(java.time.LocalDateTime.now());
                reliabilityLabDataDao.insert(historyRecord);
                
                // 4. 刷新Redis缓存
                deviceCacheService.updateDeviceCache(deviceId, latestData);
                deviceCacheService.refreshDeviceSamplesCache(deviceId);
            }
            
            // 返回成功响应
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "样品已成功取消测试预约");
            
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "取消测试失败: " + e.getMessage());
            System.err.println("[样品管理] ❌ 取消测试失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 完成测试中的样品
     * POST /iot/sample/completeTesting
     * 请求体格式：
     * {
     *   "sample_id": "123",
     *   "device_id": "DEVICE001"
     * }
     * 
     * 功能：
     * 1. 更新device_info表的status字段：从TESTING改为COMPLETED
     * 2. 更新temperature_box_latest_data表的sample_id字段：移除该样品ID
     * 3. 向reliabilitylabdata表插入一条历史记录，记录样品完成测试的时间点
     * 4. 刷新Redis缓存
     */
    @PostMapping(value = "/iot/sample/completeTesting", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeTestingSample(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String sampleIdStr = asText(payload.get("sample_id"));
            String deviceId = asText(payload.get("device_id"));
            
            if (sampleIdStr == null || sampleIdStr.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品ID和设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 解析样品ID
            Long sampleId = Long.parseLong(sampleIdStr);
            
            // 1. 更新device_info表的status字段：从TESTING改为COMPLETED
            DeviceInfo sample = deviceInfoDao.selectById(sampleId);
            if (sample == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 检查样品状态是否为TESTING
            if (!DeviceInfo.STATUS_TESTING.equals(sample.getStatus())) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "只能完成测试中状态的样品");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 获取测试结果
            String testResult = asText(payload.get("test_result"));
            if (testResult == null || testResult.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "测试结果不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 验证测试结果值
            if (!DeviceInfo.TEST_RESULT_PASS.equals(testResult) && 
                !DeviceInfo.TEST_RESULT_FAIL.equals(testResult) && 
                !DeviceInfo.TEST_RESULT_PARTIAL_OK.equals(testResult) &&
                !DeviceInfo.TEST_RESULT_FINISHED.equals(testResult)) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "测试结果值无效，必须是 PASS、FAIL、PARTIAL_OK 或 Finished");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 更新状态和测试结果
            DeviceInfo updateInfo = new DeviceInfo();
            updateInfo.setId(sampleId);
            updateInfo.setDeviceId(sample.getDeviceId());
            updateInfo.setCategory(sample.getCategory());
            updateInfo.setModel(sample.getModel());
            updateInfo.setTester(sample.getTester());
            updateInfo.setStatus(DeviceInfo.STATUS_COMPLETED);
            updateInfo.setTestResult(testResult);
            updateInfo.setUpdatedAt(java.time.LocalDateTime.now());
            deviceInfoDao.update(updateInfo);
            
            // 2. 更新temperature_box_latest_data表的sample_id字段：移除该样品ID
            ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (latestData != null) {
                // 从sample_id中移除该样品ID
                String sampleIdField = latestData.getSampleId();
                String newSampleId = null;
                if (sampleIdField != null && !sampleIdField.trim().isEmpty()) {
                    List<String> sampleIdList = new ArrayList<>(Arrays.asList(sampleIdField.split(",")));
                    sampleIdList.removeIf(id -> id.trim().equals(sampleIdStr));
                    if (!sampleIdList.isEmpty()) {
                        newSampleId = String.join(",", sampleIdList);
                    }
                }
                
                // 更新数据库
                latestData.setSampleId(newSampleId);
                latestData.setUpdatedAt(java.time.LocalDateTime.now());
                reliabilityLabDataDao.updateLatestData(latestData);
                
                // 3. 向reliabilitylabdata表插入一条历史记录，记录样品完成测试的时间点
                ReliabilityLabData historyRecord = new ReliabilityLabData();
                historyRecord.setDeviceId(latestData.getDeviceId());
                historyRecord.setSampleId(newSampleId);
                historyRecord.setWaitId(latestData.getWaitId());
                historyRecord.setTemperature(latestData.getTemperature());
                historyRecord.setHumidity(latestData.getHumidity());
                historyRecord.setSetTemperature(latestData.getSetTemperature());
                historyRecord.setSetHumidity(latestData.getSetHumidity());
                historyRecord.setPowerTemperature(latestData.getPowerTemperature());
                historyRecord.setPowerHumidity(latestData.getPowerHumidity());
                historyRecord.setRunMode(latestData.getRunMode());
                historyRecord.setRunStatus(latestData.getRunStatus());
                historyRecord.setRunHours(latestData.getRunHours());
                historyRecord.setRunMinutes(latestData.getRunMinutes());
                historyRecord.setRunSeconds(latestData.getRunSeconds());
                historyRecord.setSetProgramNumber(latestData.getSetProgramNumber());
                historyRecord.setProgramNumber(latestData.getProgramNumber());
                historyRecord.setSetRunStatus(latestData.getSetRunStatus());
                historyRecord.setTotalSteps(latestData.getTotalSteps());
                historyRecord.setRunningStep(latestData.getRunningStep());
                historyRecord.setProgramStep(latestData.getProgramStep());
                historyRecord.setProgramCycles(latestData.getProgramCycles());
                historyRecord.setProgramTotalCycles(latestData.getProgramTotalCycles());
                historyRecord.setStepRemainingHours(latestData.getStepRemainingHours());
                historyRecord.setStepRemainingMinutes(latestData.getStepRemainingMinutes());
                historyRecord.setStepRemainingSeconds(latestData.getStepRemainingSeconds());
                historyRecord.setSerialStatus(latestData.getSerialStatus());
                historyRecord.setModuleConnection(latestData.getModuleConnection());
                historyRecord.setCreatedAt(java.time.LocalDateTime.now());
                // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                // historyRecord.setUpdatedAt(java.time.LocalDateTime.now());
                reliabilityLabDataDao.insert(historyRecord);
                
                // 4. 刷新Redis缓存
                deviceCacheService.updateDeviceCache(deviceId, latestData);
                deviceCacheService.refreshDeviceSamplesCache(deviceId);
            }
            
            // 返回成功响应
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "样品已成功标记为已完成测试");
            
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "完成测试失败: " + e.getMessage());
            System.err.println("[样品管理] ❌ 完成测试失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
    
    /**
     * 取消测试中的样品
     * POST /iot/sample/cancelTestingForTesting
     * 请求体格式：
     * {
     *   "sample_id": "123",
     *   "device_id": "DEVICE001"
     * }
     * 
     * 功能：
     * 1. 更新device_info表的status字段：从TESTING改为CANCELLED
     * 2. 更新temperature_box_latest_data表的sample_id字段：移除该样品ID
     * 3. 向reliabilitylabdata表插入一条历史记录，记录样品取消测试的时间点
     * 4. 刷新Redis缓存
     */
    @PostMapping(value = "/iot/sample/cancelTestingForTesting", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelTestingSampleForTesting(@RequestBody Map<String, Object> payload) {
        try {
            // 验证必填字段
            String sampleIdStr = asText(payload.get("sample_id"));
            String deviceId = asText(payload.get("device_id"));
            
            if (sampleIdStr == null || sampleIdStr.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品ID和设备ID不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 解析样品ID
            Long sampleId = Long.parseLong(sampleIdStr);
            
            // 1. 更新device_info表的status字段：从TESTING改为CANCELLED
            DeviceInfo sample = deviceInfoDao.selectById(sampleId);
            if (sample == null) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "样品不存在");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
            }
            
            // 检查样品状态是否为TESTING
            if (!DeviceInfo.STATUS_TESTING.equals(sample.getStatus())) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "只能取消测试中状态的样品");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }
            
            // 更新状态
            DeviceInfo updateInfo = new DeviceInfo();
            updateInfo.setId(sampleId);
            updateInfo.setDeviceId(sample.getDeviceId());
            updateInfo.setCategory(sample.getCategory());
            updateInfo.setModel(sample.getModel());
            updateInfo.setTester(sample.getTester());
            updateInfo.setStatus(DeviceInfo.STATUS_CANCELLED);
            updateInfo.setUpdatedAt(java.time.LocalDateTime.now());
            deviceInfoDao.update(updateInfo);
            
            // 2. 更新temperature_box_latest_data表的sample_id字段：移除该样品ID
            ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
            if (latestData != null) {
                // 从sample_id中移除该样品ID
                String sampleIdField = latestData.getSampleId();
                String newSampleId = null;
                if (sampleIdField != null && !sampleIdField.trim().isEmpty()) {
                    List<String> sampleIdList = new ArrayList<>(Arrays.asList(sampleIdField.split(",")));
                    sampleIdList.removeIf(id -> id.trim().equals(sampleIdStr));
                    if (!sampleIdList.isEmpty()) {
                        newSampleId = String.join(",", sampleIdList);
                    }
                }
                
                // 更新数据库
                latestData.setSampleId(newSampleId);
                latestData.setUpdatedAt(java.time.LocalDateTime.now());
                reliabilityLabDataDao.updateLatestData(latestData);
                
                // 3. 向reliabilitylabdata表插入一条历史记录，记录样品取消测试的时间点
                ReliabilityLabData historyRecord = new ReliabilityLabData();
                historyRecord.setDeviceId(latestData.getDeviceId());
                historyRecord.setSampleId(newSampleId);
                historyRecord.setWaitId(latestData.getWaitId());
                historyRecord.setTemperature(latestData.getTemperature());
                historyRecord.setHumidity(latestData.getHumidity());
                historyRecord.setSetTemperature(latestData.getSetTemperature());
                historyRecord.setSetHumidity(latestData.getSetHumidity());
                historyRecord.setPowerTemperature(latestData.getPowerTemperature());
                historyRecord.setPowerHumidity(latestData.getPowerHumidity());
                historyRecord.setRunMode(latestData.getRunMode());
                historyRecord.setRunStatus(latestData.getRunStatus());
                historyRecord.setRunHours(latestData.getRunHours());
                historyRecord.setRunMinutes(latestData.getRunMinutes());
                historyRecord.setRunSeconds(latestData.getRunSeconds());
                historyRecord.setSetProgramNumber(latestData.getSetProgramNumber());
                historyRecord.setProgramNumber(latestData.getProgramNumber());
                historyRecord.setSetRunStatus(latestData.getSetRunStatus());
                historyRecord.setTotalSteps(latestData.getTotalSteps());
                historyRecord.setRunningStep(latestData.getRunningStep());
                historyRecord.setProgramStep(latestData.getProgramStep());
                historyRecord.setProgramCycles(latestData.getProgramCycles());
                historyRecord.setProgramTotalCycles(latestData.getProgramTotalCycles());
                historyRecord.setStepRemainingHours(latestData.getStepRemainingHours());
                historyRecord.setStepRemainingMinutes(latestData.getStepRemainingMinutes());
                historyRecord.setStepRemainingSeconds(latestData.getStepRemainingSeconds());
                historyRecord.setSerialStatus(latestData.getSerialStatus());
                historyRecord.setModuleConnection(latestData.getModuleConnection());
                historyRecord.setCreatedAt(java.time.LocalDateTime.now());
                // 注意：reliabilitylabdata表已删除updated_at字段，因此不再设置
                // historyRecord.setUpdatedAt(java.time.LocalDateTime.now());
                reliabilityLabDataDao.insert(historyRecord);
                
                // 4. 刷新Redis缓存
                deviceCacheService.updateDeviceCache(deviceId, latestData);
                deviceCacheService.refreshDeviceSamplesCache(deviceId);
            }
            
            // 返回成功响应
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "样品已成功取消测试");
            
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "取消测试失败: " + e.getMessage());
            System.err.println("[样品管理] ❌ 取消测试失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
}


