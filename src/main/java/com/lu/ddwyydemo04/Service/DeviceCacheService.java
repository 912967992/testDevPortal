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

    // Redis缓存键前缀 - 优化后的统一键设计
    private static final String DEVICE_DATA_KEY = "device:data"; // Hash类型，存储所有设备最新数据
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

            // 清理旧的缓存数据（兼容性清理）
            cleanupLegacyCache();

            // 从temperature_box_latest_data表获取所有设备的最新数据
            List<ReliabilityLabData> allLatestData = reliabilityLabDataDao.selectAllLatestData();

            if (allLatestData == null || allLatestData.isEmpty()) {
                logger.info("temperature_box_latest_data表中没有数据，跳过预热");
                return;
            }

            // 将所有设备数据存储到统一的Hash缓存中
            int loadedCount = 0;
            for (ReliabilityLabData data : allLatestData) {
                if (data.getDeviceId() != null) {
                    redisService.hSet(DEVICE_DATA_KEY, data.getDeviceId(), data);
                    loadedCount++;
                }
            }

            // 设置Hash缓存的过期时间（24小时）
            redisService.expire(DEVICE_DATA_KEY, 24, TimeUnit.HOURS);

            logger.info("设备数据缓存预热完成，共加载 {} 个设备的缓存数据到Hash: {}", loadedCount, DEVICE_DATA_KEY);

        } catch (Exception e) {
            logger.error("设备数据缓存预热失败", e);
            // 预热失败不影响应用启动，只是缓存没有预热数据
        }
    }

    /**
     * 清理遗留的旧缓存数据（兼容性方法）
     * 用于清理旧版本缓存策略留下的数据
     */
    private void cleanupLegacyCache() {
        try {
            logger.info("开始清理遗留的旧缓存数据...");

            int cleanedCount = 0;

            // 清理旧的单个设备缓存键
            java.util.Set<String> legacyKeys = redisService.keys("device:latest:*");
            if (legacyKeys != null && !legacyKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(legacyKeys));
                cleanedCount += legacyKeys.size();
                logger.info("清理了 {} 个旧的 device:latest:* 缓存键", legacyKeys.size());
            }

            // 清理旧的设备列表缓存
            if (redisService.hasKey("device:list")) {
                redisService.delete("device:list");
                cleanedCount++;
                logger.info("清理了旧的 device:list 缓存键");
            }

            // 清理Spring Cache自动生成的键
            java.util.Set<String> springCacheKeys = redisService.keys("deviceList::*");
            if (springCacheKeys != null && !springCacheKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(springCacheKeys));
                cleanedCount += springCacheKeys.size();
                logger.info("清理了 {} 个Spring Cache自动生成的缓存键", springCacheKeys.size());
            }

            if (cleanedCount > 0) {
                logger.info("旧缓存清理完成，共清理 {} 个缓存键", cleanedCount);
            } else {
                logger.info("没有发现需要清理的旧缓存数据");
            }

        } catch (Exception e) {
            logger.error("清理旧缓存数据时出错: {}", e.getMessage());
        }
    }

    /**
     * 获取单个设备的最新数据（带缓存）
     * @param deviceId 设备ID
     * @return 设备数据
     */
    public ReliabilityLabData getLatestDeviceData(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }

        try {
            // 从统一Hash缓存中获取设备数据
            Object cachedData = redisService.hGet(DEVICE_DATA_KEY, deviceId);
            if (cachedData instanceof ReliabilityLabData) {
                logger.debug("从Hash缓存获取设备 {} 的数据", deviceId);
                return (ReliabilityLabData) cachedData;
            }

            // 缓存中没有，从数据库查询并缓存
            logger.debug("缓存未命中，从数据库查询设备 {} 的最新数据", deviceId);
            ReliabilityLabData data = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
            if (data != null) {
                // 缓存到Hash中
                redisService.hSet(DEVICE_DATA_KEY, deviceId, data);
                logger.debug("设备 {} 数据已缓存到Hash", deviceId);
            }

            return data;

        } catch (Exception e) {
            logger.error("获取设备 {} 缓存数据失败: {}", deviceId, e.getMessage());
            // 缓存出错时直接从数据库查询
            return reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
        }
    }

    /**
     * 获取所有设备的最新数据列表（从缓存Hash中获取）
     * @return 设备数据列表（按创建时间升序排列）
     */
    public List<ReliabilityLabData> getAllLatestDeviceData() {
        try {
            // 从Hash缓存中获取所有设备数据
            Map<Object, Object> allDeviceData = redisService.hGetAll(DEVICE_DATA_KEY);

            if (allDeviceData != null && !allDeviceData.isEmpty()) {
                List<ReliabilityLabData> result = new java.util.ArrayList<>();
                for (Object value : allDeviceData.values()) {
                    if (value instanceof ReliabilityLabData) {
                        result.add((ReliabilityLabData) value);
                    }
                }
                
                // Redis Hash是无序的，需要手动排序
                // 按创建时间升序排列（最早创建的在前面）
                result.sort((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    
                    int timeCompare = a.getCreatedAt().compareTo(b.getCreatedAt());
                    if (timeCompare != 0) return timeCompare;
                    
                    // 创建时间相同时，按ID排序
                    if (a.getId() == null && b.getId() == null) return 0;
                    if (a.getId() == null) return 1;
                    if (b.getId() == null) return -1;
                    return a.getId().compareTo(b.getId());
                });
                
                logger.debug("从Hash缓存获取所有设备数据，共 {} 个设备（已按创建时间排序）", result.size());
                return result;
            }

            // 缓存中没有数据，从数据库查询
            logger.debug("缓存中没有设备数据，从数据库查询所有设备的最新数据");
            List<ReliabilityLabData> dataList = reliabilityLabDataDao.selectLatestPerDevice();

            // 将查询结果缓存到Hash中
            if (dataList != null && !dataList.isEmpty()) {
                for (ReliabilityLabData data : dataList) {
                    if (data.getDeviceId() != null) {
                        redisService.hSet(DEVICE_DATA_KEY, data.getDeviceId(), data);
                    }
                }
                // 刷新过期时间
                redisService.expire(DEVICE_DATA_KEY, 24, TimeUnit.HOURS);
                logger.debug("所有设备数据已缓存到Hash，共 {} 个设备", dataList.size());
            }

            return dataList;

        } catch (Exception e) {
            logger.error("获取所有设备缓存数据失败: {}", e.getMessage());
            // 缓存出错时直接从数据库查询
            return reliabilityLabDataDao.selectLatestPerDevice();
        }
    }

    /**
     * 更新设备数据缓存
     * 当接收到新的设备数据时调用此方法
     * @param deviceId 设备ID
     * @param data 新的设备数据
     */
    public void updateDeviceCache(String deviceId, ReliabilityLabData data) {
        if (deviceId == null || data == null) {
            return;
        }

        try {
            // 更新统一Hash缓存中的设备数据
            redisService.hSet(DEVICE_DATA_KEY, deviceId, data);

            // 刷新整个Hash的过期时间
            redisService.expire(DEVICE_DATA_KEY, 24, TimeUnit.HOURS);

            logger.debug("更新设备 {} 的Hash缓存数据", deviceId);

        } catch (Exception e) {
            logger.error("更新设备 {} 缓存失败: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 删除单个设备的缓存
     * @param deviceId 设备ID
     */
    public void deleteDeviceCache(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }

        try {
            // 从Hash缓存中删除设备数据
            redisService.hDelete(DEVICE_DATA_KEY, deviceId);
            logger.info("已从Hash缓存中删除设备 {} 的数据", deviceId);

            // 删除该设备的命令缓存
            String commandPattern = DEVICE_COMMAND_PREFIX + deviceId + ":*";
            java.util.Set<String> commandKeys = redisService.keys(commandPattern);
            if (commandKeys != null && !commandKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(commandKeys));
                logger.info("已删除设备 {} 的 {} 个命令缓存", deviceId, commandKeys.size());
            }

        } catch (Exception e) {
            logger.error("删除设备 {} 缓存失败: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 清除所有设备缓存（包括旧缓存）
     */
    public void clearAllDeviceCache() {
        try {
            // 清除新的统一Hash缓存
            redisService.delete(DEVICE_DATA_KEY);
            logger.info("清除设备数据Hash缓存: {}", DEVICE_DATA_KEY);

            // 清除命令缓存
            java.util.Set<String> commandKeys = redisService.keys(DEVICE_COMMAND_PREFIX + "*");
            if (commandKeys != null && !commandKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(commandKeys));
                logger.info("清除设备命令缓存 {} 个", commandKeys.size());
            }

            // 同时清理可能存在的旧缓存（以防万一）
            cleanupLegacyCache();

        } catch (Exception e) {
            logger.error("清除设备缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 批量更新设备数据到缓存
     * @param deviceDataMap 设备数据映射
     */
    public void batchUpdateDeviceCache(Map<String, ReliabilityLabData> deviceDataMap) {
        if (deviceDataMap == null || deviceDataMap.isEmpty()) {
            return;
        }

        try {
            // 批量更新Hash缓存
            for (Map.Entry<String, ReliabilityLabData> entry : deviceDataMap.entrySet()) {
                String deviceId = entry.getKey();
                ReliabilityLabData data = entry.getValue();

                if (deviceId != null && data != null) {
                    redisService.hSet(DEVICE_DATA_KEY, deviceId, data);
                }
            }

            // 刷新整个Hash的过期时间
            redisService.expire(DEVICE_DATA_KEY, 24, TimeUnit.HOURS);

            logger.debug("批量更新 {} 个设备的Hash缓存", deviceDataMap.size());

        } catch (Exception e) {
            logger.error("批量更新设备缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 从缓存获取设备数据（直接从Hash缓存获取）
     * @param deviceId 设备ID
     * @return 设备数据
     */
    public ReliabilityLabData getDeviceDataFromCache(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }

        try {
            // 从统一Hash缓存中获取设备数据
            Object cachedData = redisService.hGet(DEVICE_DATA_KEY, deviceId);
            if (cachedData instanceof ReliabilityLabData) {
                logger.debug("从Hash缓存获取设备 {} 的数据", deviceId);
                return (ReliabilityLabData) cachedData;
            }

            logger.debug("设备 {} 在Hash缓存中不存在", deviceId);
            return null;

        } catch (Exception e) {
            logger.error("从Hash缓存获取设备 {} 数据失败: {}", deviceId, e.getMessage());
            return null;
        }
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
            // 检查统一的设备数据Hash缓存
            boolean hasDeviceDataHash = redisService.hasKey(DEVICE_DATA_KEY);
            stats.put("hasDeviceDataHash", hasDeviceDataHash);

            if (hasDeviceDataHash) {
                // 获取Hash中设备数量
                Map<Object, Object> allDeviceData = redisService.hGetAll(DEVICE_DATA_KEY);
                int deviceCount = allDeviceData != null ? allDeviceData.size() : 0;
                stats.put("deviceDataCount", deviceCount);

                // 获取Hash过期时间
                Long expireTime = redisService.getExpire(DEVICE_DATA_KEY);
                stats.put("deviceDataExpireSeconds", expireTime);
            }

            // 获取命令相关的缓存键
            java.util.Set<String> commandKeys = redisService.keys(DEVICE_COMMAND_PREFIX + "*");
            stats.put("commandCacheCount", commandKeys != null ? commandKeys.size() : 0);

            stats.put("cacheHealthy", isCacheHealthy());

        } catch (Exception e) {
            logger.error("获取缓存统计信息失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

}
