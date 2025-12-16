package com.lu.ddwyydemo04.tasks;

import com.lu.ddwyydemo04.Service.DeviceCacheService;
import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备离线检测定时任务
 * 定期检查设备是否超时未上报数据，并更新其连接状态
 */
@Component
public class DeviceOfflineCheckTask {

    private static final Logger logger = LoggerFactory.getLogger(DeviceOfflineCheckTask.class);

    /**
     * 设备超时阈值（秒）
     * 如果设备超过此时间未上报数据，则认为设备已离线
     */
    private static final int DEVICE_TIMEOUT_SECONDS = 60;

    @Autowired
    private ReliabilityLabDataDao reliabilityLabDataDao;

    @Autowired
    private DeviceCacheService deviceCacheService;

    /**
     * 每20秒检查一次设备离线状态（根据温湿度模块连接状态判断）
     * 检查逻辑：
     * 1. 从数据库查询超过60秒未更新的设备
     * 2. 获取设备当前数据
     * 3. 更新 temperature_box_latest_data 表中的 module_connection 为 "连接异常"（前端显示字段）
     *    同时更新 serial_status 为 "离线"（保持数据完整性）
     * 4. 插入一条历史记录到 reliabilityLabData 表（用于日志查询）
     * 5. 同步更新 Redis 缓存中的设备状态
     */
    @Scheduled(fixedRate = 20000) // 20秒 = 20000毫秒
    public void checkDeviceOfflineStatus() {
        try {
            // 查找超过60秒未更新的设备ID列表
            List<String> timeoutDeviceIds = reliabilityLabDataDao.selectTimeoutDeviceIds(DEVICE_TIMEOUT_SECONDS);

            // 如果没有超时设备，直接返回
            if (timeoutDeviceIds == null || timeoutDeviceIds.isEmpty()) {
                return;
            }

            logger.warn("检测到 {} 个设备超过 {} 秒未上报数据，设备ID: {}", 
                    timeoutDeviceIds.size(), 
                    DEVICE_TIMEOUT_SECONDS, 
                    timeoutDeviceIds);

            // 处理每个离线设备
            int successCount = 0;
            for (String deviceId : timeoutDeviceIds) {
                try {
                    // 1. 获取设备当前数据（从最新数据表）
                    ReliabilityLabData currentData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
                    
                    if (currentData != null) {
                        // 2. 更新设备模块连接状态为异常（最新数据表，前端根据此字段显示）
                        currentData.setModuleConnection("连接异常");
                        currentData.setSerialStatus("离线"); // 同时更新，保持数据完整性
                        int updated = reliabilityLabDataDao.batchUpdateSerialStatusToOffline(
                            java.util.Collections.singletonList(deviceId)
                        );
                        
                        if (updated > 0) {
                            // 3. 插入历史记录到 reliabilityLabData 表
                            currentData.setId(null); // 清除ID，让数据库自动生成新ID
                            reliabilityLabDataDao.insert(currentData);
                            logger.info("设备 {} 模块连接异常事件已记录到历史数据表", deviceId);
                            
                            // 4. 同步更新 Redis 缓存
                            if (deviceCacheService.isCacheHealthy()) {
                                // 重新加载最新数据到缓存
                                ReliabilityLabData updatedData = reliabilityLabDataDao.selectLatestDataByDeviceId(deviceId);
                                if (updatedData != null) {
                                    // 确保 module_connection 字段已更新
                                    if (!"连接异常".equals(updatedData.getModuleConnection())) {
                                        logger.warn("设备 {} 的数据库更新后，module_connection 仍为: {}，强制设置为连接异常", 
                                                   deviceId, updatedData.getModuleConnection());
                                        updatedData.setModuleConnection("连接异常");
                                        updatedData.setSerialStatus("离线");
                                    }
                                    
                                    // 更新缓存
                                    deviceCacheService.updateDeviceCache(deviceId, updatedData);
                                    
                                    // 验证缓存是否更新成功
                                    ReliabilityLabData cachedData = deviceCacheService.getDeviceDataFromCache(deviceId);
                                    if (cachedData != null && "连接异常".equals(cachedData.getModuleConnection())) {
                                        logger.info("设备 {} 的缓存状态已同步更新（module_connection=连接异常）", deviceId);
                                    } else {
                                        logger.warn("设备 {} 的缓存更新后验证失败，可能需要手动刷新", deviceId);
                                    }
                                } else {
                                    logger.warn("设备 {} 更新后无法从数据库获取最新数据，缓存未更新", deviceId);
                                }
                            } else {
                                logger.warn("Redis缓存不可用，设备 {} 的离线状态未同步到缓存", deviceId);
                            }
                            
                            successCount++;
                        }
                    } else {
                        logger.warn("设备 {} 在最新数据表中没有记录，跳过离线处理", deviceId);
                    }
                    
                } catch (Exception e) {
                    logger.error("处理设备 {} 模块连接异常事件失败", deviceId, e);
                }
            }
            
            logger.info("设备模块连接检测完成：成功处理 {}/{} 个设备（已更新最新数据、插入历史记录、同步缓存）", 
                    successCount, timeoutDeviceIds.size());
            
            // 如果Redis缓存不可用，记录警告
            if (!deviceCacheService.isCacheHealthy()) {
                logger.warn("Redis缓存不可用，设备模块连接状态未同步到缓存");
            }

        } catch (Exception e) {
            logger.error("设备模块连接检测任务执行失败", e);
        }
    }
}

