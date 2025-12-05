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
    private static final String DEVICE_SAMPLES_PREFIX = "device:samples:"; // 设备样品信息缓存前缀

    @Autowired
    private ReliabilityLabDataDao reliabilityLabDataDao;

    @Autowired
    private RedisService redisService;
    
    @Autowired
    private com.lu.ddwyydemo04.dao.DeviceInfoDao deviceInfoDao;

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

            // 项目启动时清空所有与项目相关的Redis缓存键
            clearAllProjectCache();

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

            // 预热样品信息缓存
            logger.info("开始预热样品信息缓存...");
            int samplesLoadedCount = 0;
            for (ReliabilityLabData data : allLatestData) {
                if (data.getDeviceId() != null) {
                    try {
                        java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> samples = deviceInfoDao.selectAllByDeviceId(data.getDeviceId());
                        if (samples != null && !samples.isEmpty()) {
                            updateDeviceSamplesCache(data.getDeviceId(), samples);
                            samplesLoadedCount += samples.size();
                        }
                    } catch (Exception e) {
                        logger.warn("预热设备 {} 的样品缓存失败: {}", data.getDeviceId(), e.getMessage());
                    }
                }
            }
            logger.info("样品信息缓存预热完成，共加载 {} 个设备的样品信息，总计 {} 个样品", 
                       allLatestData.size(), samplesLoadedCount);

        } catch (Exception e) {
            logger.error("设备数据缓存预热失败", e);
            // 预热失败不影响应用启动，只是缓存没有预热数据
        }
    }

    /**
     * 清空所有与项目相关的Redis缓存键
     * 项目启动时调用，确保从干净的状态开始
     */
    private void clearAllProjectCache() {
        try {
            logger.info("开始清空所有项目相关的Redis缓存...");
            
            int cleanedCount = 0;
            
            // 1. 清除设备数据Hash缓存
            if (redisService.hasKey(DEVICE_DATA_KEY)) {
                redisService.delete(DEVICE_DATA_KEY);
                cleanedCount++;
                logger.info("清除了设备数据Hash缓存: {}", DEVICE_DATA_KEY);
            }
            
            // 2. 清除所有设备命令缓存
            java.util.Set<String> commandKeys = redisService.keys(DEVICE_COMMAND_PREFIX + "*");
            if (commandKeys != null && !commandKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(commandKeys));
                cleanedCount += commandKeys.size();
                logger.info("清除了 {} 个设备命令缓存键", commandKeys.size());
            }
            
            // 3. 清除所有样品信息缓存
            java.util.Set<String> samplesKeys = redisService.keys(DEVICE_SAMPLES_PREFIX + "*");
            if (samplesKeys != null && !samplesKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(samplesKeys));
                cleanedCount += samplesKeys.size();
                logger.info("清除了 {} 个样品信息缓存键", samplesKeys.size());
            }
            
            // 4. 清理旧的单个设备缓存键（兼容性清理）
            java.util.Set<String> legacyKeys = redisService.keys("device:latest:*");
            if (legacyKeys != null && !legacyKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(legacyKeys));
                cleanedCount += legacyKeys.size();
                logger.info("清除了 {} 个旧的 device:latest:* 缓存键", legacyKeys.size());
            }
            
            // 5. 清理旧的设备列表缓存
            if (redisService.hasKey("device:list")) {
                redisService.delete("device:list");
                cleanedCount++;
                logger.info("清除了旧的 device:list 缓存键");
            }
            
            // 6. 清理Spring Cache自动生成的键
            java.util.Set<String> springCacheKeys = redisService.keys("deviceList::*");
            if (springCacheKeys != null && !springCacheKeys.isEmpty()) {
                redisService.delete(new java.util.ArrayList<>(springCacheKeys));
                cleanedCount += springCacheKeys.size();
                logger.info("清除了 {} 个Spring Cache自动生成的缓存键", springCacheKeys.size());
            }
            
            logger.info("项目缓存清空完成，共清理 {} 个缓存键", cleanedCount);
            
        } catch (Exception e) {
            logger.error("清空项目缓存时出错: {}", e.getMessage(), e);
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
                ReliabilityLabData cached = (ReliabilityLabData) cachedData;
                
                // 验证缓存数据：如果缓存中有 sample_id，但数据库中没有，则强制刷新
                // 这样可以确保当数据库被直接修改时，缓存也能及时更新
                // 注意：这里只验证 sample_id 字段，避免每次都查询数据库影响性能
                String cachedSampleId = cached.getSampleId();
                
                // 只有当缓存中有 sample_id 时，才去验证数据库（因为如果缓存中就没有，说明本来就没有样品）
                if (cachedSampleId != null && !cachedSampleId.trim().isEmpty()) {
                    // 从数据库查询最新数据验证
                    ReliabilityLabData dbData = reliabilityLabDataDao.selectLatestByDeviceId(deviceId);
                    if (dbData != null) {
                        String dbSampleId = dbData.getSampleId();
                        
                        // 如果数据库中的 sample_id 为空或与缓存不一致，刷新缓存
                        if (dbSampleId == null || dbSampleId.trim().isEmpty()) {
                            logger.info("检测到设备 {} 的 sample_id 在数据库中被清空，刷新缓存", deviceId);
                            redisService.hSet(DEVICE_DATA_KEY, deviceId, dbData);
                            // 同时刷新样品信息缓存，确保样品列表也更新
                            refreshDeviceSamplesCache(deviceId);
                            return dbData;
                        } else if (!cachedSampleId.equals(dbSampleId)) {
                            logger.info("检测到设备 {} 的 sample_id 不一致，刷新缓存（缓存: {}, 数据库: {}）", 
                                       deviceId, cachedSampleId, dbSampleId);
                            redisService.hSet(DEVICE_DATA_KEY, deviceId, dbData);
                            // 同时刷新样品信息缓存
                            refreshDeviceSamplesCache(deviceId);
                            return dbData;
                        }
                    }
                }
                
                logger.debug("从Hash缓存获取设备 {} 的数据", deviceId);
                return cached;
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
                // 按创建时间升序排列（最早创建的在前面），确保排序稳定性
                result.sort((a, b) -> {
                    // 1. 先按创建时间排序
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) {
                        // 都没有创建时间，按device_id排序
                        return compareDeviceId(a.getDeviceId(), b.getDeviceId());
                    }
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    
                    int timeCompare = a.getCreatedAt().compareTo(b.getCreatedAt());
                    if (timeCompare != 0) return timeCompare;
                    
                    // 2. 创建时间相同时，按device_id排序（更稳定）
                    return compareDeviceId(a.getDeviceId(), b.getDeviceId());
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

    /**
     * 比较两个device_id的字符串顺序（用于排序）
     * @param deviceId1 设备ID1
     * @param deviceId2 设备ID2
     * @return 比较结果
     */
    private int compareDeviceId(String deviceId1, String deviceId2) {
        if (deviceId1 == null && deviceId2 == null) return 0;
        if (deviceId1 == null) return 1;
        if (deviceId2 == null) return -1;
        return deviceId1.compareTo(deviceId2);
    }

    // ========================================
    // 样品信息缓存相关方法
    // ========================================

    /**
     * 获取设备的所有样品信息列表（从Redis缓存获取）
     * @param deviceId 设备ID
     * @return 样品信息列表（返回所有状态的样品：预约、测试中、测试完成）
     */
    public java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> getDeviceSamples(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }

        try {
            String cacheKey = DEVICE_SAMPLES_PREFIX + deviceId;
            
            // 从Redis Hash中获取样品列表
            Map<Object, Object> samplesMap = redisService.hGetAll(cacheKey);
            
            if (samplesMap != null && !samplesMap.isEmpty()) {
                java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> result = new java.util.ArrayList<>();
                
                for (Object value : samplesMap.values()) {
                    if (value instanceof com.lu.ddwyydemo04.pojo.DeviceInfo) {
                        com.lu.ddwyydemo04.pojo.DeviceInfo info = (com.lu.ddwyydemo04.pojo.DeviceInfo) value;
                        result.add(info);
                    }
                }
                
                // 按创建时间升序排序
                result.sort((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                });
                
                logger.debug("从Redis缓存获取设备 {} 的样品信息，共 {} 个样品", deviceId, result.size());
                return result;
            }

            // 缓存中没有，从数据库查询并缓存
            logger.debug("缓存未命中，从数据库查询设备 {} 的样品信息", deviceId);
            java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> samples = deviceInfoDao.selectAllByDeviceId(deviceId);
            
            if (samples != null && !samples.isEmpty()) {
                // 缓存到Redis Hash中
                updateDeviceSamplesCache(deviceId, samples);
            }
            
            return samples != null ? samples : new java.util.ArrayList<>();

        } catch (Exception e) {
            logger.error("获取设备 {} 样品缓存数据失败: {}", deviceId, e.getMessage());
            // 缓存出错时直接从数据库查询
            java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> samples = deviceInfoDao.selectAllByDeviceId(deviceId);
            return samples != null ? samples : new java.util.ArrayList<>();
        }
    }
    
    /**
     * 获取设备正在测试中的样品信息列表（从Redis缓存获取）
     * @param deviceId 设备ID
     * @return 样品信息列表（只返回状态为 TESTING 的样品）
     */
    public java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> getTestingDeviceSamples(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }

        try {
            // 获取所有样品
            java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> allSamples = getDeviceSamples(deviceId);
            
            // 过滤出状态为 TESTING 的样品
            java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> testingSamples = new java.util.ArrayList<>();
            for (com.lu.ddwyydemo04.pojo.DeviceInfo info : allSamples) {
                if (com.lu.ddwyydemo04.pojo.DeviceInfo.STATUS_TESTING.equals(info.getStatus())) {
                    testingSamples.add(info);
                }
            }
            
            logger.debug("获取设备 {} 正在测试中的样品，共 {} 个", deviceId, testingSamples.size());
            return testingSamples;

        } catch (Exception e) {
            logger.error("获取设备 {} 正在测试中的样品失败: {}", deviceId, e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 更新设备的样品信息缓存
     * @param deviceId 设备ID
     * @param samples 样品信息列表
     */
    public void updateDeviceSamplesCache(String deviceId, java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> samples) {
        if (deviceId == null || samples == null) {
            return;
        }

        try {
            String cacheKey = DEVICE_SAMPLES_PREFIX + deviceId;
            
            // 先删除旧的缓存
            redisService.delete(cacheKey);
            
            // 将所有样品存储到Hash中，使用样品ID作为字段名
            for (com.lu.ddwyydemo04.pojo.DeviceInfo sample : samples) {
                if (sample.getId() != null) {
                    redisService.hSet(cacheKey, String.valueOf(sample.getId()), sample);
                }
            }
            
            // 设置过期时间（24小时）
            redisService.expire(cacheKey, 24, TimeUnit.HOURS);
            
            logger.debug("更新设备 {} 的样品信息缓存，共 {} 个样品", deviceId, samples.size());

        } catch (Exception e) {
            logger.error("更新设备 {} 样品缓存失败: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 删除设备的样品信息缓存
     * @param deviceId 设备ID
     */
    public void deleteDeviceSamplesCache(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }

        try {
            String cacheKey = DEVICE_SAMPLES_PREFIX + deviceId;
            redisService.delete(cacheKey);
            logger.debug("删除设备 {} 的样品信息缓存", deviceId);

        } catch (Exception e) {
            logger.error("删除设备 {} 样品缓存失败: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 刷新设备的样品信息缓存（从数据库重新加载）
     * @param deviceId 设备ID
     */
    public void refreshDeviceSamplesCache(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }

        try {
            java.util.List<com.lu.ddwyydemo04.pojo.DeviceInfo> samples = deviceInfoDao.selectAllByDeviceId(deviceId);
            updateDeviceSamplesCache(deviceId, samples != null ? samples : new java.util.ArrayList<>());
            logger.debug("刷新设备 {} 的样品信息缓存", deviceId);

        } catch (Exception e) {
            logger.error("刷新设备 {} 样品缓存失败: {}", deviceId, e.getMessage());
        }
    }

}
