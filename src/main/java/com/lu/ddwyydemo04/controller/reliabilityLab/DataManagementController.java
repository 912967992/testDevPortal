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
                (model != null && !model.trim().isEmpty())) {
                // 先找到匹配的样品信息
                sampleInfoList = findSamplesByInfo(category, model);
                if (sampleInfoList == null || sampleInfoList.isEmpty()) {
                    // 没有找到匹配的样品
                    result.put("success", true);
                    result.put("data", createEmptyResult(page, pageSize));
                    return ResponseEntity.ok(result);
                }
                
                // 对于每个样品，优先使用sample_id直接查询（如果sample_id已填充）
                for (DeviceInfo sample : sampleInfoList) {
                    Long sampleId = sample.getId();
                    
                    // 优先使用sample_id直接查询（性能更好）
                    if (sampleId != null) {
                        // 将 Long 类型的 sampleId 转换为 String 进行查询
                        String sampleIdStr = String.valueOf(sampleId);
                        List<ReliabilityLabData> sampleDataList = reliabilityLabDataDao.selectBySampleId(sampleIdStr);
                        if (sampleDataList != null && !sampleDataList.isEmpty()) {
                            // 找到数据，直接使用sample_id查询的结果
                            dataList.addAll(sampleDataList);
                            deviceIdToSampleMap.put(sample.getDeviceId(), sample);
                            System.out.println("[样品查询] 使用sample_id直接查询，样品ID: " + sampleId + "，找到 " + sampleDataList.size() + " 条数据");
                            continue; // 跳过后续的时间范围查询逻辑
                        }
                    }
                    
                    // 如果sample_id查询没有结果，使用原来的时间范围查询逻辑（兼容历史数据）
                    System.out.println("[样品查询] sample_id查询无结果，使用时间范围查询，样品ID: " + sampleId);
                    if (sample.getDeviceId() == null || sample.getCreatedAt() == null) {
                        continue;
                    }
                    
                    // 建立设备ID到样品的映射关系（一个设备对应一个样品）
                    deviceIdToSampleMap.put(sample.getDeviceId(), sample);
                    
                    // 1. 先搜索创建时间在样品创建时间之前的数据，这个数据就是温箱在样品插入时的温度
                    // 无论样品创建时间是否在历史数据表的时间之后，都要先查找样品创建时间之前最近的历史数据
                    ReliabilityLabData initialData = reliabilityLabDataDao.selectLatestBeforeTime(
                        sample.getDeviceId(), sample.getCreatedAt());
                    
                    // 2. 判断样品创建时间是否在历史数据表的时间之后
                    ReliabilityLabData earliestData = reliabilityLabDataDao.selectEarliestByDeviceId(sample.getDeviceId());
                    boolean sampleCreatedAfterHistoryData = false;
                    
                    if (earliestData != null && earliestData.getCreatedAt() != null) {
                        // 如果样品创建时间在最早历史数据之后，说明样品创建时间在历史数据表的时间之后
                        if (sample.getCreatedAt().isAfter(earliestData.getCreatedAt())) {
                            sampleCreatedAfterHistoryData = true;
                        }
                    } else {
                        // 如果没有历史数据，检查该设备是否有任何数据
                        ReliabilityLabData anyData = reliabilityLabDataDao.selectLatestByDeviceId(sample.getDeviceId());
                        if (anyData == null) {
                            continue; // 该设备没有任何数据，跳过
                        }
                        sampleCreatedAfterHistoryData = true;
                    }
                    
                    // 3. 如果样品创建时间在历史数据表的时间之后，且找到了样品创建时间之前的历史数据
                    // 需要将初始数据的记录时间（createdAt）设置为样品创建时间，这样展示时就从样品创建时间开始
                    if (sampleCreatedAfterHistoryData && initialData != null) {
                        // 创建一个新的数据对象，复制历史数据的所有信息，但将createdAt设置为样品创建时间
                        ReliabilityLabData adjustedInitialData = new ReliabilityLabData();
                        adjustedInitialData.setId(initialData.getId()); // 保留原始ID用于去重
                        adjustedInitialData.setDeviceId(initialData.getDeviceId());
                        adjustedInitialData.setCreatedAt(sample.getCreatedAt()); // 记录时间设置为样品创建时间
                        adjustedInitialData.setUpdatedAt(sample.getCreatedAt());
                        // 复制所有温箱状态信息
                        adjustedInitialData.setTemperature(initialData.getTemperature());
                        adjustedInitialData.setHumidity(initialData.getHumidity());
                        adjustedInitialData.setSetTemperature(initialData.getSetTemperature());
                        adjustedInitialData.setSetHumidity(initialData.getSetHumidity());
                        adjustedInitialData.setPowerTemperature(initialData.getPowerTemperature());
                        adjustedInitialData.setPowerHumidity(initialData.getPowerHumidity());
                        adjustedInitialData.setRunMode(initialData.getRunMode());
                        adjustedInitialData.setRunStatus(initialData.getRunStatus());
                        adjustedInitialData.setRunHours(initialData.getRunHours());
                        adjustedInitialData.setRunMinutes(initialData.getRunMinutes());
                        adjustedInitialData.setRunSeconds(initialData.getRunSeconds());
                        adjustedInitialData.setSetProgramNumber(initialData.getSetProgramNumber());
                        adjustedInitialData.setProgramNumber(initialData.getProgramNumber());
                        adjustedInitialData.setSetRunStatus(initialData.getSetRunStatus());
                        adjustedInitialData.setTotalSteps(initialData.getTotalSteps());
                        adjustedInitialData.setRunningStep(initialData.getRunningStep());
                        adjustedInitialData.setProgramStep(initialData.getProgramStep());
                        adjustedInitialData.setProgramCycles(initialData.getProgramCycles());
                        adjustedInitialData.setProgramTotalCycles(initialData.getProgramTotalCycles());
                        adjustedInitialData.setStepRemainingHours(initialData.getStepRemainingHours());
                        adjustedInitialData.setStepRemainingMinutes(initialData.getStepRemainingMinutes());
                        adjustedInitialData.setStepRemainingSeconds(initialData.getStepRemainingSeconds());
                        adjustedInitialData.setSerialStatus(initialData.getSerialStatus());
                        adjustedInitialData.setModuleConnection(initialData.getModuleConnection());
                        initialData = adjustedInitialData; // 使用调整后的初始数据
                    }
                    
                    // 4. 如果样品创建时间在历史数据表的时间之后，且没有找到样品创建时间之前的历史数据
                    // 需要创建一个虚拟的初始数据点，以样品创建时间作为初始时间
                    if (sampleCreatedAfterHistoryData && initialData == null) {
                        // 查询样品创建时间之后的第一条数据，用于获取温箱状态
                        List<ReliabilityLabData> firstDataAfterSample = queryDataByDevice(
                            sample.getDeviceId(), 
                            sample.getCreatedAt(), // 从样品创建时间开始查询（>=）
                            null, // 不限制结束时间
                            0, 
                            1); // 只查询第一条
                        
                        if (firstDataAfterSample != null && !firstDataAfterSample.isEmpty()) {
                            ReliabilityLabData firstData = firstDataAfterSample.get(0);
                            // 创建一个虚拟的初始数据点，时间设置为样品创建时间
                            initialData = new ReliabilityLabData();
                            initialData.setId(null); // 虚拟数据，没有ID
                            initialData.setDeviceId(sample.getDeviceId());
                            initialData.setCreatedAt(sample.getCreatedAt()); // 以样品创建时间作为初始时间
                            initialData.setUpdatedAt(sample.getCreatedAt());
                            // 从第一条数据中复制温箱状态信息
                            initialData.setTemperature(firstData.getTemperature());
                            initialData.setHumidity(firstData.getHumidity());
                            initialData.setSetTemperature(firstData.getSetTemperature());
                            initialData.setSetHumidity(firstData.getSetHumidity());
                            initialData.setPowerTemperature(firstData.getPowerTemperature());
                            initialData.setPowerHumidity(firstData.getPowerHumidity());
                            initialData.setRunMode(firstData.getRunMode());
                            initialData.setRunStatus(firstData.getRunStatus());
                            initialData.setRunHours(firstData.getRunHours());
                            initialData.setRunMinutes(firstData.getRunMinutes());
                            initialData.setRunSeconds(firstData.getRunSeconds());
                            initialData.setSetProgramNumber(firstData.getSetProgramNumber());
                            initialData.setProgramNumber(firstData.getProgramNumber());
                            initialData.setSetRunStatus(firstData.getSetRunStatus());
                            initialData.setTotalSteps(firstData.getTotalSteps());
                            initialData.setRunningStep(firstData.getRunningStep());
                            initialData.setProgramStep(firstData.getProgramStep());
                            initialData.setProgramCycles(firstData.getProgramCycles());
                            initialData.setProgramTotalCycles(firstData.getProgramTotalCycles());
                            initialData.setStepRemainingHours(firstData.getStepRemainingHours());
                            initialData.setStepRemainingMinutes(firstData.getStepRemainingMinutes());
                            initialData.setStepRemainingSeconds(firstData.getStepRemainingSeconds());
                            initialData.setSerialStatus(firstData.getSerialStatus());
                            initialData.setModuleConnection(firstData.getModuleConnection());
                        }
                    }
                    
                    // 2. 确定查询的开始时间：从样品创建时间开始查询后续数据
                    LocalDateTime queryStartTimeForSample = sample.getCreatedAt();
                    
                    // 2.1 确定截止时间
                    LocalDateTime queryEndTimeForSample = null; // 默认为null，表示不限制结束时间
                    boolean isTesting = false; // 标记是否还在测试中
                    
                    if (sample.getUpdatedAt() != null && 
                        !sample.getUpdatedAt().equals(sample.getCreatedAt())) {
                        // 更新时间与创建时间不一致，说明测试已完成，使用更新时间作为截止时间
                        queryEndTimeForSample = sample.getUpdatedAt();
                    } else {
                        // 更新时间与创建时间一致，说明还在测试中，截止时间设置为null（不限制）
                        isTesting = true;
                        queryEndTimeForSample = null; // 相当于没有设置截止时间，查询所有数据
                    }
                    
                    System.out.println("[样品查询调试] 样品: " + sample.getCategory() + " - " + sample.getModel() + 
                                     ", 设备ID: " + sample.getDeviceId() + 
                                     ", 是否在测试中: " + isTesting + 
                                     ", 样品创建时间: " + sample.getCreatedAt() + 
                                     ", 样品创建时间在历史数据之后: " + sampleCreatedAfterHistoryData +
                                     ", 最早历史数据时间: " + (earliestData != null && earliestData.getCreatedAt() != null ? earliestData.getCreatedAt() : "无历史数据") +
                                     ", 初始数据时间（样品插入时温箱温度）: " + (initialData != null ? initialData.getCreatedAt() : "无") + 
                                     ", 初始数据ID: " + (initialData != null ? initialData.getId() : "null（虚拟数据点）") + 
                                     ", 查询开始时间: " + queryStartTimeForSample + 
                                     ", 查询结束时间: " + (queryEndTimeForSample != null ? queryEndTimeForSample : "无限制（查询所有）"));
                    
                    // 2.2 查询样品创建时间之后的所有数据（包括等于创建时间的数据）
                    List<ReliabilityLabData> sampleDataList = queryDataByDevice(
                        sample.getDeviceId(), 
                        queryStartTimeForSample, // 从样品创建时间开始查询（>=）
                        queryEndTimeForSample, // 如果为null，SQL查询不会限制结束时间
                        0, 
                        Integer.MAX_VALUE);
                    
                    System.out.println("[样品查询调试] 查询到的数据总数: " + sampleDataList.size());
                    if (sampleDataList.size() > 0) {
                        System.out.println("[样品查询调试] 第一条数据时间: " + sampleDataList.get(0).getCreatedAt());
                        System.out.println("[样品查询调试] 最后一条数据时间: " + sampleDataList.get(sampleDataList.size() - 1).getCreatedAt());
                    }
                    
                    // 4. 添加初始数据和查询到的所有数据
                    List<ReliabilityLabData> filteredDataList = new ArrayList<>();
                    Set<Long> addedDataIds = new HashSet<>(); // 用于去重，避免同一条数据被添加多次
                    
                    // 首先添加初始数据
                    if (initialData != null) {
                        if (initialData.getId() == null) {
                            // 虚拟数据点（样品创建时间在历史数据表的时间之后，且没有找到样品创建时间之前的历史数据）
                            filteredDataList.add(initialData);
                            // 虚拟数据没有ID，不需要添加到addedDataIds
                            System.out.println("[样品查询调试] 添加虚拟初始数据点（以样品创建时间作为初始时间）, 时间: " + initialData.getCreatedAt());
                        } else {
                            // 真实的历史数据（样品创建时间之前最近的数据，代表样品插入时温箱的温度状态）
                            // 如果样品创建时间在历史数据表的时间之后，记录时间已经被调整为样品创建时间
                            filteredDataList.add(initialData);
                            addedDataIds.add(initialData.getId());
                            if (sampleCreatedAfterHistoryData) {
                                System.out.println("[样品查询调试] 保留初始数据（样品插入时温箱温度，来自reliabilityLabData表，记录时间已调整为样品创建时间）, ID: " + initialData.getId() + ", 原始时间: " + earliestData.getCreatedAt() + ", 调整后时间: " + initialData.getCreatedAt());
                            } else {
                                System.out.println("[样品查询调试] 保留初始数据（样品插入时温箱温度，来自reliabilityLabData表）, ID: " + initialData.getId() + ", 时间: " + initialData.getCreatedAt());
                            }
                        }
                    } else {
                        System.out.println("[样品查询调试] 样品创建时间之前无历史数据，且样品创建时间不在历史数据表的时间之后");
                    }
                    
                    // 然后添加查询到的所有数据（样品创建时间之后的所有数据）
                    for (ReliabilityLabData data : sampleDataList) {
                        if (data.getCreatedAt() == null || data.getId() == null) {
                            continue;
                        }
                        
                        // 跳过已经添加的数据（避免重复）
                        if (addedDataIds.contains(data.getId())) {
                            continue;
                        }
                        
                        // 如果还在测试中，保留从样品创建时间之后的所有数据
                        if (isTesting) {
                            if (data.getCreatedAt().isAfter(sample.getCreatedAt()) || 
                                data.getCreatedAt().isEqual(sample.getCreatedAt())) {
                                // 还在测试中，保留所有超过创建时间后的数据（包括等于创建时间的数据）
                                filteredDataList.add(data);
                                addedDataIds.add(data.getId());
                                System.out.println("[样品查询调试] 保留测试中数据, ID: " + data.getId() + ", 时间: " + data.getCreatedAt() + 
                                                 ", 样品创建时间: " + sample.getCreatedAt());
                            }
                        } else {
                            // 测试已完成，只保留样品创建时间之后、更新时间之前的数据
                            if (data.getCreatedAt().isAfter(sample.getCreatedAt()) || 
                                data.getCreatedAt().isEqual(sample.getCreatedAt())) {
                                if (sample.getUpdatedAt() != null && 
                                    (data.getCreatedAt().isBefore(sample.getUpdatedAt()) || 
                                     data.getCreatedAt().isEqual(sample.getUpdatedAt()))) {
                                    filteredDataList.add(data);
                                    addedDataIds.add(data.getId());
                                    System.out.println("[样品查询调试] 保留已完成测试数据, ID: " + data.getId() + ", 时间: " + data.getCreatedAt());
                                }
                            }
                        }
                    }
                    
                    System.out.println("[样品查询调试] 过滤后的数据总数: " + filteredDataList.size());
                    System.out.println("[样品查询调试] 过滤后的数据详情:");
                    for (ReliabilityLabData d : filteredDataList) {
                        System.out.println("  - ID: " + d.getId() + ", 时间: " + d.getCreatedAt() + ", 温度: " + d.getTemperature());
                    }
                    dataList.addAll(filteredDataList);
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
            
            // 添加调试信息：检查样品是否在测试中
            List<Map<String, Object>> sampleDebugInfo = new ArrayList<>();
            if (sampleInfoList != null && !sampleInfoList.isEmpty()) {
                for (DeviceInfo sample : sampleInfoList) {
                    Map<String, Object> debugMap = new HashMap<>();
                    debugMap.put("deviceId", sample.getDeviceId());
                    debugMap.put("category", sample.getCategory());
                    debugMap.put("model", sample.getModel());
                    debugMap.put("createdAt", sample.getCreatedAt());
                    debugMap.put("updatedAt", sample.getUpdatedAt());
                    boolean isTesting = (sample.getUpdatedAt() != null && 
                                        sample.getCreatedAt() != null &&
                                        sample.getUpdatedAt().equals(sample.getCreatedAt()));
                    debugMap.put("isTesting", isTesting);
                    debugMap.put("message", isTesting ? "样品还在测试中" : "样品测试已完成");
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
    private List<DeviceInfo> findSamplesByInfo(String category, String model) {
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
}

