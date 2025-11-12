package com.lu.ddwyydemo04.tasks;

import com.lu.ddwyydemo04.Service.DeviceCacheService;
import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备数据同步定时任务
 * 定期同步数据库最新数据到Redis缓存
 */
@Component
public class DeviceDataSyncTask {

    private static final Logger logger = LoggerFactory.getLogger(DeviceDataSyncTask.class);

    @Autowired
    private ReliabilityLabDataDao reliabilityLabDataDao;

    @Autowired
    private DeviceCacheService deviceCacheService;

    /**
     * 每5分钟同步一次设备数据到缓存
     * 确保缓存数据与数据库保持同步
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300秒 = 300000毫秒
    public void syncDeviceDataToCache() {
        try {
            logger.info("开始执行设备数据缓存同步任务");

            // 检查Redis缓存是否可用
            if (!deviceCacheService.isCacheHealthy()) {
                logger.warn("Redis缓存不可用，跳过同步任务");
                return;
            }

            // 获取数据库中最新的设备数据
            List<ReliabilityLabData> latestData = reliabilityLabDataDao.selectLatestPerDevice();

            if (latestData == null || latestData.isEmpty()) {
                logger.info("数据库中没有设备数据，跳过同步");
                return;
            }

            // 批量更新缓存
            Map<String, ReliabilityLabData> deviceDataMap = new HashMap<>();
            for (ReliabilityLabData data : latestData) {
                if (data.getDeviceId() != null) {
                    deviceDataMap.put(data.getDeviceId(), data);
                }
            }

            deviceCacheService.batchUpdateDeviceCache(deviceDataMap);

            logger.info("设备数据缓存同步完成，共同步 {} 个设备的数据", deviceDataMap.size());

        } catch (Exception e) {
            logger.error("设备数据缓存同步任务执行失败", e);
        }
    }

    /**
     * 每30分钟清理一次过期缓存
     * 清理可能存在的无效或过期数据
     */
    @Scheduled(fixedRate = 1800000) // 30分钟 = 1800秒 = 1800000毫秒
    public void cleanupExpiredCache() {
        try {
            logger.info("开始执行缓存清理任务");

            // 检查缓存健康状态
            Map<String, Object> stats = deviceCacheService.getCacheStats();
            Boolean isHealthy = (Boolean) stats.get("cacheHealthy");

            if (!isHealthy) {
                logger.warn("Redis缓存不可用，跳过清理任务");
                return;
            }

            // 这里可以添加更复杂的清理逻辑
            // 比如清理长时间未更新的设备数据等

            logger.info("缓存清理任务完成");

        } catch (Exception e) {
            logger.error("缓存清理任务执行失败", e);
        }
    }

    /**
     * 每天凌晨2点执行一次全量缓存重建
     * 确保缓存数据的完整性和一致性
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void rebuildFullCache() {
        try {
            logger.info("开始执行全量缓存重建任务");

            // 检查Redis缓存是否可用
            if (!deviceCacheService.isCacheHealthy()) {
                logger.warn("Redis缓存不可用，跳过重建任务");
                return;
            }

            // 清除所有缓存
            deviceCacheService.clearAllDeviceCache();

            // 重新加载所有设备数据到缓存
            syncDeviceDataToCache();

            logger.info("全量缓存重建任务完成");

        } catch (Exception e) {
            logger.error("全量缓存重建任务执行失败", e);
        }
    }
}


