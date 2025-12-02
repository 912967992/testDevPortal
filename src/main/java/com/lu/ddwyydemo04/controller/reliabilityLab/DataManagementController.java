package com.lu.ddwyydemo04.controller.reliabilityLab;

import com.lu.ddwyydemo04.dao.ReliabilityLabDataDao;
import com.lu.ddwyydemo04.dao.DeviceInfoDao;
import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import com.lu.ddwyydemo04.pojo.DeviceInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/api/data")
public class DataManagementController {

    private final ReliabilityLabDataDao reliabilityLabDataDao;
    private final DeviceInfoDao deviceInfoDao;

    public DataManagementController(ReliabilityLabDataDao reliabilityLabDataDao,
                                   DeviceInfoDao deviceInfoDao) {
        this.reliabilityLabDataDao = reliabilityLabDataDao;
        this.deviceInfoDao = deviceInfoDao;
    }

    /**
     * 数据查询接口
     * 支持按设备ID或样品信息查询温箱历史数据
     */
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchData(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String tester,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 解析时间参数（可选）
            LocalDateTime startDateTime = null;
            LocalDateTime endDateTime = null;
            
            if (startTime != null && !startTime.trim().isEmpty()) {
                startDateTime = parseDateTime(startTime);
                if (startDateTime == null) {
                    result.put("success", false);
                    result.put("message", "开始时间格式错误，请使用 yyyy-MM-ddTHH:mm 格式");
                    return ResponseEntity.badRequest().body(result);
                }
            }
            
            if (endTime != null && !endTime.trim().isEmpty()) {
                endDateTime = parseDateTime(endTime);
                if (endDateTime == null) {
                    result.put("success", false);
                    result.put("message", "结束时间格式错误，请使用 yyyy-MM-ddTHH:mm 格式");
                    return ResponseEntity.badRequest().body(result);
                }
            }
            
            // 如果两个时间都设置了，验证时间范围
            if (startDateTime != null && endDateTime != null) {
                if (startDateTime.isAfter(endDateTime)) {
                    result.put("success", false);
                    result.put("message", "开始时间不能晚于结束时间");
                    return ResponseEntity.badRequest().body(result);
                }
            }
            
            // 查询数据
            List<ReliabilityLabData> dataList = new ArrayList<>();
            List<DeviceInfo> sampleInfoList = new ArrayList<>(); // 用于存储样品信息
            // 建立设备ID到样品信息的映射（用于按样品查询时关联）
            Map<String, DeviceInfo> deviceIdToSampleMap = new HashMap<>();
            int total = 0;
            
            // 如果按样品信息查询
            if ((category != null && !category.trim().isEmpty()) || 
                (model != null && !model.trim().isEmpty()) ||
                (tester != null && !tester.trim().isEmpty())) {
                // 先找到匹配的样品信息
                sampleInfoList = findSamplesByInfo(category, model, tester);
                if (sampleInfoList == null || sampleInfoList.isEmpty()) {
                    // 没有找到匹配的样品
                    result.put("success", true);
                    result.put("data", createEmptyResult(page, pageSize));
                    return ResponseEntity.ok(result);
                }
                
                // 对于每个样品，根据sample_id字段查询数据
                for (DeviceInfo sample : sampleInfoList) {
                    Long sampleId = sample.getId();
                    
                    if (sampleId == null) {
                        continue;
                    }
                    
                    // 将 Long 类型的 sampleId 转换为 String 进行查询
                    String sampleIdStr = String.valueOf(sampleId);
                    String sampleDeviceId = sample.getDeviceId();
                    
                    // 建立设备ID到样品的映射关系
                    deviceIdToSampleMap.put(sampleDeviceId, sample);
                    
                    // 优化：先查询第一条和最后一条包含该样品ID的数据，确定时间范围
                    ReliabilityLabData firstData = reliabilityLabDataDao.selectFirstBySampleId(sampleIdStr, sampleDeviceId);
                    ReliabilityLabData lastData = reliabilityLabDataDao.selectLastBySampleId(sampleIdStr, sampleDeviceId);
                    
                    if (firstData == null || lastData == null) {
                        System.out.println("[样品查询] 样品ID: " + sampleId + "，未找到包含该样品ID的数据");
                        continue;
                    }
                    
                    LocalDateTime sampleStartTime = firstData.getCreatedAt();
                    LocalDateTime sampleEndTime = lastData.getCreatedAt();
                    
                    // 判断样品是否还在测试中：
                    // 【重要】判断标准：仅根据 sample_id 字段是否还包含该样品ID来判断
                    // 不能使用运行状态（runStatus）来判断，因为温箱可能因为报错而停止（状态为0），
                    // 但样品实际上还在测试中（只是温箱暂时停止工作）
                    // 
                    // 判断逻辑：
                    // 1. 查询该设备在最后一条包含该样品ID的数据之后的第一条数据
                    // 2. 如果下一条数据不包含该样品ID，说明样品已经被移除，测试已结束
                    // 3. 如果下一条数据还包含该样品ID，或者没有下一条数据，说明测试还在进行中
                    boolean isTesting = true; // 默认假设还在测试中
                    ReliabilityLabData nextDataAfterEnd = reliabilityLabDataDao.selectFirstAfterTime(
                        sampleDeviceId, sampleEndTime
                    );
                    
                    if (nextDataAfterEnd != null) {
                        // 如果下一条数据不包含该样品ID，说明样品已经被移除，测试已结束
                        if (!containsSampleId(nextDataAfterEnd.getSampleId(), sampleIdStr)) {
                            isTesting = false;
                            System.out.println("[样品查询] 样品ID: " + sampleId + " 测试已结束，下一条数据（时间: " + 
                                             nextDataAfterEnd.getCreatedAt() + "）已移除该样品ID");
                        } else {
                            // 下一条数据还包含该样品ID，说明测试还在进行中
                            isTesting = true;
                            System.out.println("[样品查询] 样品ID: " + sampleId + " 还在测试中，下一条数据（时间: " + 
                                             nextDataAfterEnd.getCreatedAt() + "）仍包含该样品ID");
                        }
                    } else {
                        // 没有下一条数据，检查设备最新数据是否还包含该样品ID
                        ReliabilityLabData latestDeviceData = reliabilityLabDataDao.selectLatestDataByDeviceId(sampleDeviceId);
                        if (latestDeviceData != null) {
                            // 如果最新数据就是最后一条包含该样品ID的数据，说明测试可能还在进行中
                            // 或者最新数据还包含该样品ID，说明测试还在进行中
                            if (latestDeviceData.getCreatedAt() != null && 
                                latestDeviceData.getCreatedAt().equals(sampleEndTime)) {
                                // 最新数据就是最后一条包含该样品ID的数据，说明测试还在进行中
                                isTesting = true;
                                System.out.println("[样品查询] 样品ID: " + sampleId + " 还在测试中，最新数据就是最后一条包含该样品ID的数据");
                            } else if (containsSampleId(latestDeviceData.getSampleId(), sampleIdStr)) {
                                // 最新数据还包含该样品ID，说明测试还在进行中
                                isTesting = true;
                                System.out.println("[样品查询] 样品ID: " + sampleId + " 还在测试中，最新数据仍包含该样品ID");
                            } else {
                                // 最新数据不包含该样品ID，说明测试已结束
                                isTesting = false;
                                System.out.println("[样品查询] 样品ID: " + sampleId + " 测试已结束，最新数据已移除该样品ID");
                            }
                        } else {
                            // 没有最新数据，无法判断，默认认为测试已结束
                            isTesting = false;
                            System.out.println("[样品查询] 样品ID: " + sampleId + " 无法判断测试状态，没有最新数据");
                        }
                    }
                    
                    // 确定查询的结束时间：
                    // - 如果还在测试中，结束时间使用用户指定的endTime（如果有），否则查询到最新
                    // - 如果测试已完成，结束时间使用最后一条包含该样品ID的数据时间
                    LocalDateTime queryEndTime = isTesting ? endDateTime : sampleEndTime;
                    if (queryEndTime == null && isTesting) {
                        // 如果还在测试中且用户没有指定结束时间，查询到最新
                        queryEndTime = null;
                    }
                    
                    // 确定查询的开始时间：使用第一条包含该样品ID的数据时间，或用户指定的开始时间（取较晚的）
                    LocalDateTime queryStartTime = sampleStartTime;
                    if (startDateTime != null && startDateTime.isAfter(sampleStartTime)) {
                        queryStartTime = startDateTime;
                    }
                    
                    System.out.println("[样品查询] 样品ID: " + sampleId + 
                                     ", 设备ID: " + sampleDeviceId +
                                     ", 样品开始时间: " + sampleStartTime + 
                                     ", 样品结束时间: " + sampleEndTime + 
                                     ", 是否在测试中: " + isTesting +
                                     ", 查询开始时间: " + queryStartTime +
                                     ", 查询结束时间: " + (queryEndTime != null ? queryEndTime : "最新"));
                    
                    // 使用优化的查询方法：按设备ID和时间范围查询，而不是查询所有数据
                    // 先统计总数（用于分页）
                    int totalCount = 0;
                    List<ReliabilityLabData> sampleDataList = new ArrayList<>();
                    
                    // 如果还在测试中，需要查询所有数据（因为可能还在持续更新）
                    // 如果测试已完成，只查询到结束时间的数据
                    if (isTesting) {
                        // 还在测试中，分页查询数据
                        int currentOffset = 0;
                        int batchSize = 1000; // 每次查询1000条
                        while (true) {
                            List<ReliabilityLabData> batch = reliabilityLabDataDao.selectBySampleIdAndDevice(
                                sampleIdStr, sampleDeviceId, queryStartTime, queryEndTime, 
                                currentOffset, batchSize
                            );
                            if (batch == null || batch.isEmpty()) {
                                break;
                            }
                            sampleDataList.addAll(batch);
                            if (batch.size() < batchSize) {
                                break; // 已经查询完所有数据
                            }
                            currentOffset += batchSize;
                        }
                        totalCount = sampleDataList.size();
                    } else {
                        // 测试已完成，查询所有数据（因为时间范围已确定）
                        sampleDataList = reliabilityLabDataDao.selectBySampleIdAndDevice(
                            sampleIdStr, sampleDeviceId, queryStartTime, queryEndTime, 0, Integer.MAX_VALUE
                        );
                        totalCount = sampleDataList != null ? sampleDataList.size() : 0;
                    }
                    
                    System.out.println("[样品查询] 样品ID: " + sampleId + 
                                     ", 查询到数据总数: " + totalCount);
                    
                    if (sampleDataList != null && !sampleDataList.isEmpty()) {
                        dataList.addAll(sampleDataList);
                    }
                }
                
                // 按创建时间倒序排序
                dataList.sort((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) {
                        return 0;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
                
                total = dataList.size();
                
                // 手动分页
                int fromIndex = (page - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, dataList.size());
                if (fromIndex < dataList.size()) {
                    dataList = dataList.subList(fromIndex, toIndex);
                } else {
                    dataList = new ArrayList<>();
                }
            } else {
                // 按设备ID查询或查询所有设备
                dataList = queryDataByDevice(deviceId, startDateTime, endDateTime, 
                    (page - 1) * pageSize, pageSize);
                total = countDataByDevice(deviceId, startDateTime, endDateTime);
            }
            
            // 转换为前端需要的格式，并关联样品信息
            List<Map<String, Object>> dataMapList = convertToMapList(dataList, startDateTime, endDateTime, sampleInfoList, deviceIdToSampleMap);
            
            // 添加调试信息：检查样品是否在测试中（基于sample_id判断）
            List<Map<String, Object>> sampleDebugInfo = new ArrayList<>();
            if (sampleInfoList != null && !sampleInfoList.isEmpty()) {
                for (DeviceInfo sample : sampleInfoList) {
                    Map<String, Object> debugMap = new HashMap<>();
                    debugMap.put("deviceId", sample.getDeviceId());
                    debugMap.put("category", sample.getCategory());
                    debugMap.put("model", sample.getModel());
                    debugMap.put("tester", sample.getTester());
                    debugMap.put("createdAt", sample.getCreatedAt());
                    debugMap.put("updatedAt", sample.getUpdatedAt());
                    
                    // 判断测试状态：查询该样品ID的最后一条数据和下一条数据
                    Long sampleId = sample.getId();
                    boolean isTesting = false;
                    String testStatusMessage = "未知状态";
                    
                    if (sampleId != null) {
                        String sampleIdStr = String.valueOf(sampleId);
                        String debugDeviceId = sample.getDeviceId();
                        
                        // 【重要】判断测试是否结束的唯一标准：sample_id 字段是否还包含该样品ID
                        // 不能使用运行状态（runStatus）来判断，因为温箱可能因为报错而停止，
                        // 但样品实际上还在测试中（只是温箱暂时停止工作）
                        
                        // 查询最后一条包含该样品ID的数据
                        ReliabilityLabData lastData = reliabilityLabDataDao.selectLastBySampleId(sampleIdStr, debugDeviceId);
                        if (lastData != null) {
                            LocalDateTime lastDataTime = lastData.getCreatedAt();
                            // 查询下一条数据
                            ReliabilityLabData nextData = reliabilityLabDataDao.selectFirstAfterTime(debugDeviceId, lastDataTime);
                            
                            if (nextData != null) {
                                // 如果下一条数据不包含该样品ID，说明样品已经被移除，测试已结束
                                if (!containsSampleId(nextData.getSampleId(), sampleIdStr)) {
                                    isTesting = false;
                                    testStatusMessage = "测试已结束（下一条数据已移除样品ID）";
                                } else {
                                    // 下一条数据还包含该样品ID，说明测试还在进行中
                                    // 即使温箱运行状态为0（停止），只要sample_id还包含该样品ID，就认为测试还在进行中
                                    isTesting = true;
                                    testStatusMessage = "测试进行中（下一条数据仍包含样品ID）";
                                }
                            } else {
                                // 没有下一条数据，检查最新数据
                                ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(debugDeviceId);
                                if (latestData != null && containsSampleId(latestData.getSampleId(), sampleIdStr)) {
                                    // 最新数据还包含该样品ID，说明测试还在进行中
                                    // 即使温箱运行状态为0（停止），只要sample_id还包含该样品ID，就认为测试还在进行中
                                    isTesting = true;
                                    testStatusMessage = "测试进行中（最新数据仍包含样品ID）";
                                } else {
                                    // 最新数据不包含该样品ID，说明样品已经被移除，测试已结束
                                    isTesting = false;
                                    testStatusMessage = "测试已结束（最新数据已移除样品ID）";
                                }
                            }
                        } else {
                            testStatusMessage = "未找到包含该样品ID的数据";
                        }
                    }
                    
                    debugMap.put("isTesting", isTesting);
                    debugMap.put("message", testStatusMessage);
                    sampleDebugInfo.add(debugMap);
                }
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("list", dataMapList);
            data.put("total", total);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("sampleDebugInfo", sampleDebugInfo); // 添加样品调试信息
            
            result.put("success", true);
            result.put("data", data);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * 根据样品信息查找样品列表（包含样品信息和创建时间）
     */
    private List<DeviceInfo> findSamplesByInfo(String category, String model, String tester) {
        List<DeviceInfo> matchedSamples = new ArrayList<>();
        
        // 查询所有设备的最新数据，然后通过设备ID查询对应的样品信息
        List<ReliabilityLabData> allLatestData = reliabilityLabDataDao.selectAllLatestData();
        
        for (ReliabilityLabData data : allLatestData) {
            String devId = data.getDeviceId();
            if (devId != null) {
                // 查询该设备的所有样品信息
                List<DeviceInfo> deviceInfos = deviceInfoDao.selectAllByDeviceId(devId);
                
                for (DeviceInfo deviceInfo : deviceInfos) {
                    boolean match = true;
                    
                    if (category != null && !category.trim().isEmpty()) {
                        if (deviceInfo.getCategory() == null || 
                            !deviceInfo.getCategory().contains(category.trim())) {
                            match = false;
                        }
                    }
                    
                    if (match && model != null && !model.trim().isEmpty()) {
                        if (deviceInfo.getModel() == null || 
                            !deviceInfo.getModel().contains(model.trim())) {
                            match = false;
                        }
                    }
                    
                    if (match && tester != null && !tester.trim().isEmpty()) {
                        if (deviceInfo.getTester() == null || 
                            !deviceInfo.getTester().contains(tester.trim())) {
                            match = false;
                        }
                    }
                    
                    if (match) {
                        matchedSamples.add(deviceInfo);
                    }
                }
            }
        }
        
        return matchedSamples;
    }
    
    /**
     * 按设备查询数据
     */
    private List<ReliabilityLabData> queryDataByDevice(String deviceId, 
                                                        LocalDateTime startTime, 
                                                        LocalDateTime endTime,
                                                        int offset, 
                                                        int limit) {
        return reliabilityLabDataDao.selectHistoryData(deviceId, startTime, endTime, offset, limit);
    }
    
    /**
     * 统计数据总数
     */
    private int countDataByDevice(String deviceId, 
                                  LocalDateTime startTime, 
                                  LocalDateTime endTime) {
        return reliabilityLabDataDao.countHistoryData(deviceId, startTime, endTime);
    }
    
    /**
     * 转换为Map列表（方便JSON序列化），并关联样品信息
     */
    private List<Map<String, Object>> convertToMapList(List<ReliabilityLabData> dataList, 
                                                        LocalDateTime startTime, 
                                                        LocalDateTime endTime,
                                                        List<DeviceInfo> sampleInfoList,
                                                        Map<String, DeviceInfo> deviceIdToSampleMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 用于缓存设备ID对应的样品信息列表（用于按设备ID查询时）
        Map<String, List<DeviceInfo>> deviceSampleCache = new HashMap<>();
        
        for (ReliabilityLabData data : dataList) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", data.getId());
            map.put("deviceId", data.getDeviceId());
            map.put("temperature", data.getTemperature());
            map.put("humidity", data.getHumidity());
            map.put("setTemperature", data.getSetTemperature());
            map.put("setHumidity", data.getSetHumidity());
            map.put("runStatus", data.getRunStatus());
            map.put("runMode", data.getRunMode());
            map.put("powerTemperature", data.getPowerTemperature());
            map.put("powerHumidity", data.getPowerHumidity());
            
            // 关联样品信息
            List<Map<String, Object>> sampleInfoMapList = new ArrayList<>();
            
            // 如果是按样品查询（deviceIdToSampleMap不为空），通过设备ID直接获取样品信息
            if (deviceIdToSampleMap != null && !deviceIdToSampleMap.isEmpty() && 
                data.getDeviceId() != null) {
                DeviceInfo sample = deviceIdToSampleMap.get(data.getDeviceId());
                if (sample != null) {
                    Map<String, Object> sampleMap = new HashMap<>();
                    sampleMap.put("id", sample.getId());
                    sampleMap.put("category", sample.getCategory());
                    sampleMap.put("model", sample.getModel());
                    sampleMap.put("tester", sample.getTester());
                    sampleMap.put("createdAt", sample.getCreatedAt());
                    sampleInfoMapList.add(sampleMap);
                }
            }
            
            // 所有情况下都使用原始数据时间（不替换为样品创建时间）
            map.put("createdAt", data.getCreatedAt());
            map.put("updatedAt", data.getUpdatedAt());
            
            // 如果不是按样品查询，需要查询该设备在数据记录时间点附近的样品信息
            if (deviceIdToSampleMap == null || deviceIdToSampleMap.isEmpty()) {
                sampleInfoMapList = getSampleInfoForDataRecord(
                    data.getDeviceId(), data.getCreatedAt(), startTime, endTime, deviceSampleCache);
            }
            
            map.put("samples", sampleInfoMapList);
            result.add(map);
        }
        
        return result;
    }
    
    /**
     * 获取数据记录对应的样品信息
     * 查询在该数据记录时间点之前创建的样品信息
     * 如果设置了查询时间范围，只返回在查询时间范围内有效的样品
     */
    private List<Map<String, Object>> getSampleInfoForDataRecord(String deviceId, 
                                                                 LocalDateTime dataTime,
                                                                 LocalDateTime queryStartTime,
                                                                 LocalDateTime queryEndTime,
                                                                 Map<String, List<DeviceInfo>> deviceSampleCache) {
        List<Map<String, Object>> sampleList = new ArrayList<>();
        
        // 从缓存获取或查询该设备的所有样品信息
        List<DeviceInfo> allSamples = deviceSampleCache.get(deviceId);
        if (allSamples == null) {
            allSamples = deviceInfoDao.selectAllByDeviceId(deviceId);
            deviceSampleCache.put(deviceId, allSamples);
        }
        
        if (allSamples == null || allSamples.isEmpty()) {
            return sampleList;
        }
        
        // 筛选符合时间条件的样品：
        // 1. 样品创建时间要在数据记录时间之前或等于数据记录时间（样品先创建，然后开始测试）
        // 2. 如果设置了查询时间范围：
        //    - 样品创建时间在查询时间范围内，或者
        //    - 样品创建时间在查询开始时间之前但在数据记录时间之前（表示该样品在查询期间仍然有效）
        for (DeviceInfo sample : allSamples) {
            LocalDateTime sampleCreatedTime = sample.getCreatedAt();
            
            if (sampleCreatedTime == null) {
                continue;
            }
            
            // 样品创建时间要在数据记录时间之前或等于（样品先创建，然后开始测试）
            if (sampleCreatedTime.isAfter(dataTime)) {
                continue;
            }
            
            // 判断样品是否在查询时间范围内有效
            boolean isValid = false;
            
            if (queryStartTime != null && queryEndTime != null) {
                // 如果设置了查询时间范围
                if (sampleCreatedTime.isBefore(queryStartTime) || sampleCreatedTime.isEqual(queryStartTime)) {
                    // 样品在查询开始时间之前或等于查询开始时间创建，且在数据记录时间之前，说明该样品在查询期间有效
                    isValid = true;
                } else if ((sampleCreatedTime.isAfter(queryStartTime) || sampleCreatedTime.isEqual(queryStartTime))
                    && (sampleCreatedTime.isBefore(queryEndTime) || sampleCreatedTime.isEqual(queryEndTime))) {
                    // 样品创建时间在查询时间范围内，且在数据记录时间之前，也有效
                    isValid = true;
                }
            } else {
                // 没有设置查询时间范围，只要样品在数据记录时间之前或等于数据记录时间创建就有效
                isValid = true;
            }
            
            if (isValid) {
                Map<String, Object> sampleMap = new HashMap<>();
                sampleMap.put("id", sample.getId());
                sampleMap.put("category", sample.getCategory());
                sampleMap.put("model", sample.getModel());
                sampleMap.put("tester", sample.getTester());
                sampleMap.put("createdAt", sample.getCreatedAt());
                sampleList.add(sampleMap);
            }
        }
        
        // 按创建时间倒序排序，最新的在前（表示当前最有效的样品）
        sampleList.sort((a, b) -> {
            LocalDateTime timeA = (LocalDateTime) a.get("createdAt");
            LocalDateTime timeB = (LocalDateTime) b.get("createdAt");
            if (timeA == null || timeB == null) {
                return 0;
            }
            return timeB.compareTo(timeA);
        });
        
        return sampleList;
    }
    
    /**
     * 创建空结果
     */
    private Map<String, Object> createEmptyResult(int page, int pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", new ArrayList<>());
        data.put("total", 0);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }
    
    /**
     * 解析日期时间字符串
     * 支持格式：yyyy-MM-ddTHH:mm 或 yyyy-MM-dd HH:mm:ss
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 尝试解析 ISO 格式（前端datetime-local输入框的格式）
            if (dateTimeStr.contains("T")) {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } else {
                // 尝试解析其他格式
                return LocalDateTime.parse(dateTimeStr, 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 检查sample_id字段是否包含指定的样品ID
     * sample_id可能是单个ID（如 "1"）或多个ID用逗号分隔（如 "1,22,33"）
     * @param sampleIdField sample_id字段的值
     * @param targetSampleId 要查找的样品ID（字符串格式）
     * @return 如果包含则返回true，否则返回false
     */
    private boolean containsSampleId(String sampleIdField, String targetSampleId) {
        if (sampleIdField == null || sampleIdField.trim().isEmpty()) {
            return false;
        }
        if (targetSampleId == null || targetSampleId.trim().isEmpty()) {
            return false;
        }
        
        // 去除空格
        String field = sampleIdField.trim();
        String target = targetSampleId.trim();
        
        // 精确匹配（单个ID的情况）
        if (field.equals(target)) {
            return true;
        }
        
        // 检查是否在逗号分隔的列表中
        // 使用逗号分割，检查每个部分是否匹配
        String[] parts = field.split(",");
        for (String part : parts) {
            if (part.trim().equals(target)) {
                return true;
            }
        }
        
        return false;
    }
}

