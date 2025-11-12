package com.lu.ddwyydemo04.Service;

import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceCacheService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceCacheService.class);

    // Redis缓存键前缀
    private static final String DEVICE_LATEST_PREFIX = "device:latest:";
    private static final String DEVICE_LIST_KEY = "device:list";
    private static final String DEVICE_COMMAND_PREFIX = "device:command:";

    @Autowired
    private ReliabilityLabDataDao reliabilityLabDataDao;

    @Autowired
    private RedisService redisService;

    /**
     * 获取RedisService实例（供Controller使用）
     */
    public RedisService getRedisService() {
        return redisService;
    }

    /**
     * 应用启动时预热缓存
     * 从temperature_box_latest_data表加载所有设备数据到Redis缓存
     */
    @PostConstruct
    public void preloadCacheOnStartup() {
        try {
            logger.info("开始预热设备数据缓存...");

            // 检查Redis是否可用
            if (!isCacheHealthy()) {
                logger.warn("Redis缓存不可用，跳过启动预热");
                return;
            }

            // 从temperature_box_latest_data表获取所有设备的最新数据
            List<ReliabilityLabData> allLatestData = reliabilityLabDataDao.selectAllLatestData();

            if (allLatestData == null || allLatestData.isEmpty()) {
                logger.info("temperature_box_latest_data表中没有数据，跳过预热");
                return;
            }

            // 将数据批量加载到Redis缓存
            Map<String, ReliabilityLabData> deviceDataMap = new java.util.HashMap<>();
            for (ReliabilityLabData data : allLatestData) {
                if (data.getDeviceId() != null) {
                    String cacheKey = DEVICE_LATEST_PREFIX + data.getDeviceId();
                    redisService.set(cacheKey, data, 30, TimeUnit.MINUTES);
                    deviceDataMap.put(data.getDeviceId(), data);
                }
            }

            logger.info("设备数据缓存预热完成，共加载 {} 个设备的缓存数据", deviceDataMap.size());

        } catch (Exception e) {
            logger.error("设备数据缓存预热失败", e);
            // 预热失败不影响应用启动，只是缓存没有预热数据
        }
    }

    /**
     * 获取单个设备的最新数据（带缓存）
     * @param deviceId 设备ID
     * @return 设备数据
     */
    @Cacheable(value = "deviceLatest", key = "#deviceId", unless = "#result == null")
    public ReliabilityLabData getLatestDeviceData(String deviceId) {
        logger.debug("从数据库查询设备 {} 的最新数据", deviceId);
        return reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
    }

    /**
     * 获取所有设备的最新数据列表（带缓存）
     * @return 设备数据列表
     */
    @Cacheable(value = "deviceList", key = "'allDevices'")
    public List<ReliabilityLabData> getAllLatestDeviceData() {
        logger.debug("从数据库查询所有设备的最新数据");
        return reliabilityLabDataDao.selectLatestPerDevice();
    }

    /**
     * 更新设备数据缓存
     * 当接收到新的设备数据时调用此方法
     * @param deviceId 设备ID
     * @param data 新的设备数据
     */
    @CacheEvict(value = "deviceLatest", key = "#deviceId")
    public void updateDeviceCache(String deviceId, ReliabilityLabData data) {
        // 更新单个设备缓存
        String cacheKey = DEVICE_LATEST_PREFIX + deviceId;
        redisService.set(cacheKey, data, 30, TimeUnit.MINUTES); // 缓存30分钟

        // 清除设备列表缓存，让下次查询时重新从数据库获取
        redisService.delete(DEVICE_LIST_KEY);

        logger.debug("更新设备 {} 的缓存", deviceId);
    }

    /**
     * 清除所有设备缓存
     */
    @CacheEvict(value = {"deviceLatest", "deviceList"}, allEntries = true)
    public void clearAllDeviceCache() {
        redisService.flushDB();
        logger.info("清除所有设备缓存");
    }

    /**
     * 批量更新设备数据到缓存
     * @param deviceDataMap 设备数据映射
     */
    public void batchUpdateDeviceCache(Map<String, ReliabilityLabData> deviceDataMap) {
        for (Map.Entry<String, ReliabilityLabData> entry : deviceDataMap.entrySet()) {
            String deviceId = entry.getKey();
            ReliabilityLabData data = entry.getValue();

            String cacheKey = DEVICE_LATEST_PREFIX + deviceId;
            redisService.set(cacheKey, data, 30, TimeUnit.MINUTES);

            // 清除列表缓存
            redisService.delete(DEVICE_LIST_KEY);
        }
        logger.debug("批量更新 {} 个设备的缓存", deviceDataMap.size());
    }

    /**
     * 从缓存获取设备数据（不带Spring Cache注解，手动控制）
     * @param deviceId 设备ID
     * @return 设备数据
     */
    public ReliabilityLabData getDeviceDataFromCache(String deviceId) {
        String cacheKey = DEVICE_LATEST_PREFIX + deviceId;
        Object cachedData = redisService.get(cacheKey);

        if (cachedData instanceof ReliabilityLabData) {
            logger.debug("从缓存获取设备 {} 的数据", deviceId);
            return (ReliabilityLabData) cachedData;
        }

        // 缓存未命中，从数据库查询并缓存
        ReliabilityLabData data = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
        if (data != null) {
            redisService.set(cacheKey, data, 30, TimeUnit.MINUTES);
            logger.debug("设备 {} 数据缓存未命中，已缓存", deviceId);
        }

        return data;
    }

    /**
     * 缓存设备命令状态
     * @param deviceId 设备ID
     * @param commandId 命令ID
     * @param status 状态
     */
    public void cacheDeviceCommandStatus(String deviceId, Long commandId, String status) {
        String cacheKey = DEVICE_COMMAND_PREFIX + deviceId + ":" + commandId;
        redisService.set(cacheKey, status, 1, TimeUnit.HOURS); // 缓存1小时
        logger.debug("缓存设备 {} 命令 {} 状态: {}", deviceId, commandId, status);
    }

    /**
     * 获取设备命令状态
     * @param deviceId 设备ID
     * @param commandId 命令ID
     * @return 状态
     */
    public String getDeviceCommandStatus(String deviceId, Long commandId) {
        String cacheKey = DEVICE_COMMAND_PREFIX + deviceId + ":" + commandId;
        Object status = redisService.get(cacheKey);
        return status != null ? status.toString() : null;
    }

    /**
     * 检查缓存健康状态
     * @return 是否健康
     */
    public boolean isCacheHealthy() {
        try {
            redisService.set("health:check", "ok", 10, TimeUnit.SECONDS);
            Object result = redisService.get("health:check");
            return "ok".equals(result);
        } catch (Exception e) {
            logger.error("Redis缓存健康检查失败", e);
            return false;
        }
    }

    /**
     * 获取缓存统计信息
     * @return 统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new java.util.HashMap<>();

        try {
            // 获取所有设备相关的缓存键
            java.util.Set<String> deviceKeys = redisService.keys(DEVICE_LATEST_PREFIX + "*");
            stats.put("deviceCacheCount", deviceKeys != null ? deviceKeys.size() : 0);

            // 获取命令相关的缓存键
            java.util.Set<String> commandKeys = redisService.keys(DEVICE_COMMAND_PREFIX + "*");
            stats.put("commandCacheCount", commandKeys != null ? commandKeys.size() : 0);

            // 检查设备列表缓存 - Spring Cache可能使用不同的key格式
            Boolean hasDeviceList = checkDeviceListCache();
            stats.put("hasDeviceListCache", hasDeviceList);

            stats.put("cacheHealthy", isCacheHealthy());

        } catch (Exception e) {
            logger.error("获取缓存统计信息失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 检查设备列表缓存是否存在
     */
    private boolean checkDeviceListCache() {
        try {
            // 直接检查我们手动设置的缓存key
            return redisService.hasKey(DEVICE_LIST_KEY);
        } catch (Exception e) {
            logger.debug("检查设备列表缓存时出错", e);
        }
        return false;
    }
}
