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
            // 建立样品ID到样品信息的映射（用于按样品查询时精确匹配）
            Map<Long, DeviceInfo> sampleIdToSampleMap = new HashMap<>();
            // 建立样品ID到测试时长信息的映射（用于存储测试时长）
            Map<Long, Map<String, Object>> sampleTestDurationMap = new HashMap<>();
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
                    
                    // 建立设备ID到样品的映射关系（用于兼容旧逻辑）
                    deviceIdToSampleMap.put(sampleDeviceId, sample);
                    // 建立样品ID到样品的映射关系（用于精确匹配）
                    sampleIdToSampleMap.put(sampleId, sample);
                    
                    // 优化：先查询第一条和最后一条包含该样品ID的数据，确定时间范围
                    ReliabilityLabData firstData = reliabilityLabDataDao.selectFirstBySampleId(sampleIdStr, sampleDeviceId);
                    ReliabilityLabData lastData = reliabilityLabDataDao.selectLastBySampleId(sampleIdStr, sampleDeviceId);
                    
                    if (firstData == null || lastData == null) {
                        System.out.println("[样品查询] 样品ID: " + sampleId + "，未找到包含该样品ID的数据");
                        continue;
                    }
                    
                    LocalDateTime sampleStartTime = firstData.getCreatedAt();
                    LocalDateTime sampleEndTime = lastData.getCreatedAt();
                    
                    // 计算测试时长：
                    // 1. 开始时间：第一条包含该sample_id的数据的时间
                    // 2. 结束时间：该设备下一条不包含该sample_id的数据的时间
                    // 如果测试还在进行中，结束时间为null
                    LocalDateTime testStartTime = sampleStartTime;
                    LocalDateTime testEndTime = null;
                    boolean isTesting = true; // 默认假设还在测试中
                    
                    // 查询该设备在最后一条包含该样品ID的数据之后的第一条数据
                    ReliabilityLabData nextDataAfterEnd = reliabilityLabDataDao.selectFirstAfterTime(
                        sampleDeviceId, sampleEndTime
                    );
                    
                    if (nextDataAfterEnd != null) {
                        // 如果下一条数据不包含该样品ID（sample_id 和 wait_id 都不包含），说明样品已经被移除，测试已结束
                        if (!containsSampleIdInData(nextDataAfterEnd, sampleIdStr)) {
                            isTesting = false;
                            // 结束时间就是下一条数据的时间
                            testEndTime = nextDataAfterEnd.getCreatedAt();
                            System.out.println("[样品查询] 样品ID: " + sampleId + " 测试已结束，下一条数据（时间: " + 
                                             testEndTime + "）已移除该样品ID（sample_id和wait_id都不包含）");
                        } else {
                            // 下一条数据还包含该样品ID（sample_id 或 wait_id 包含），说明测试还在进行中或还在等候
                            isTesting = true;
                            testEndTime = null; // 测试还在进行中，结束时间为null
                            System.out.println("[样品查询] 样品ID: " + sampleId + " 还在测试中或等候中，下一条数据（时间: " + 
                                             nextDataAfterEnd.getCreatedAt() + "）仍包含该样品ID");
                        }
                    } else {
                        // 没有下一条数据，检查设备最新数据是否还包含该样品ID
                        ReliabilityLabData latestDeviceData = reliabilityLabDataDao.selectLatestDataByDeviceId(sampleDeviceId);
                        if (latestDeviceData != null) {
                            // 如果最新数据就是最后一条包含该样品ID的数据，说明测试可能还在进行中
                            // 或者最新数据还包含该样品ID，说明测试还在进行中或还在等候
                            if (latestDeviceData.getCreatedAt() != null && 
                                latestDeviceData.getCreatedAt().equals(sampleEndTime)) {
                                // 最新数据就是最后一条包含该样品ID的数据，说明测试还在进行中或还在等候
                                isTesting = true;
                                testEndTime = null; // 测试还在进行中，结束时间为null
                                System.out.println("[样品查询] 样品ID: " + sampleId + " 还在测试中或等候中，最新数据就是最后一条包含该样品ID的数据");
                            } else if (containsSampleIdInData(latestDeviceData, sampleIdStr)) {
                                // 最新数据还包含该样品ID（sample_id 或 wait_id 包含），说明测试还在进行中或还在等候
                                isTesting = true;
                                testEndTime = null; // 测试还在进行中，结束时间为null
                                System.out.println("[样品查询] 样品ID: " + sampleId + " 还在测试中或等候中，最新数据仍包含该样品ID");
                            } else {
                                // 最新数据不包含该样品ID（sample_id 和 wait_id 都不包含），说明测试已结束
                                isTesting = false;
                                // 结束时间使用最新数据的时间
                                testEndTime = latestDeviceData.getCreatedAt();
                                System.out.println("[样品查询] 样品ID: " + sampleId + " 测试已结束，最新数据（时间: " + 
                                                 testEndTime + "）已移除该样品ID（sample_id和wait_id都不包含）");
                            }
                        } else {
                            // 没有最新数据，无法判断，默认认为测试已结束
                            isTesting = false;
                            testEndTime = sampleEndTime; // 使用最后一条包含该样品ID的数据时间作为结束时间
                            System.out.println("[样品查询] 样品ID: " + sampleId + " 无法判断测试状态，没有最新数据，使用最后一条数据时间作为结束时间");
                        }
                    }
                    
                    // 将测试时长信息存储到sampleTestDurationMap中
                    Map<String, Object> testDurationInfo = new HashMap<>();
                    testDurationInfo.put("startTime", testStartTime);
                    testDurationInfo.put("endTime", testEndTime);
                    testDurationInfo.put("isTesting", isTesting);
                    // 计算测试时长（秒）
                    if (testStartTime != null && testEndTime != null) {
                        long durationSeconds = java.time.Duration.between(testStartTime, testEndTime).getSeconds();
                        testDurationInfo.put("durationSeconds", durationSeconds);
                        // 格式化为小时:分钟:秒
                        long hours = durationSeconds / 3600;
                        long minutes = (durationSeconds % 3600) / 60;
                        long seconds = durationSeconds % 60;
                        testDurationInfo.put("durationFormatted", String.format("%d:%02d:%02d", hours, minutes, seconds));
                    } else if (testStartTime != null && isTesting) {
                        // 测试还在进行中，计算到当前时间的时长
                        long durationSeconds = java.time.Duration.between(testStartTime, LocalDateTime.now()).getSeconds();
                        testDurationInfo.put("durationSeconds", durationSeconds);
                        long hours = durationSeconds / 3600;
                        long minutes = (durationSeconds % 3600) / 60;
                        long seconds = durationSeconds % 60;
                        testDurationInfo.put("durationFormatted", String.format("%d:%02d:%02d", hours, minutes, seconds) + " (进行中)");
                    } else {
                        testDurationInfo.put("durationSeconds", 0L);
                        testDurationInfo.put("durationFormatted", "0:00:00");
                    }
                    sampleTestDurationMap.put(sampleId, testDurationInfo);
                    
                    System.out.println("[样品查询] 样品ID: " + sampleId + 
                                     ", 测试开始时间: " + testStartTime + 
                                     ", 测试结束时间: " + (testEndTime != null ? testEndTime : "进行中") +
                                     ", 测试时长: " + testDurationInfo.get("durationFormatted"));
                    
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
                        // 测试已完成，需要查询该设备从测试开始时间到测试结束时间之后的所有数据
                        // 这样能包含下一条不包含该样品ID的数据，用于正确计算测试结束时间
                        // 查询该设备在时间范围内的所有数据（不仅仅是包含该样品ID的数据）
                        // 查询结束时间延长到测试结束时间之后，确保包含下一条数据
                        LocalDateTime extendedEndTime = testEndTime != null ? testEndTime.plusMinutes(1) : null;
                        List<ReliabilityLabData> allDeviceData = reliabilityLabDataDao.selectHistoryData(
                            sampleDeviceId, queryStartTime, extendedEndTime, 0, Integer.MAX_VALUE
                        );
                        
                        // 过滤出包含该样品ID的数据，同时保留下一条不包含该样品ID的数据
                        if (allDeviceData != null && !allDeviceData.isEmpty()) {
                            // 按时间排序（确保按时间顺序）
                            allDeviceData.sort((a, b) -> {
                                if (a.getCreatedAt() == null || b.getCreatedAt() == null) {
                                    return 0;
                                }
                                return a.getCreatedAt().compareTo(b.getCreatedAt());
                            });
                            
                            // 找到最后一条包含该样品ID的数据的索引
                            int lastContainingIndex = -1;
                            for (int i = allDeviceData.size() - 1; i >= 0; i--) {
                                if (containsSampleIdInData(allDeviceData.get(i), sampleIdStr)) {
                                    lastContainingIndex = i;
                                    break;
                                }
                            }
                            
                            // 构建结果列表：包含所有包含该样品ID的数据，以及下一条不包含该样品ID的数据
                            sampleDataList = new ArrayList<>();
                            for (int i = 0; i < allDeviceData.size(); i++) {
                                ReliabilityLabData data = allDeviceData.get(i);
                                boolean containsSample = containsSampleIdInData(data, sampleIdStr);
                                
                                if (containsSample) {
                                    // 包含该样品ID的数据，全部添加
                                    sampleDataList.add(data);
                                } else if (i == lastContainingIndex + 1) {
                                    // 这是最后一条包含该样品ID的数据之后的第一条数据，且不包含该样品ID
                                    // 这条数据用于标记测试结束时间，需要包含
                                    sampleDataList.add(data);
                                    break; // 只添加这一条，后续数据不需要
                                }
                            }
                        } else {
                            // 如果没有查询到数据，使用原来的查询方法
                            sampleDataList = reliabilityLabDataDao.selectBySampleIdAndDevice(
                                sampleIdStr, sampleDeviceId, queryStartTime, queryEndTime, 0, Integer.MAX_VALUE
                            );
                        }
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
            // 如果是按设备ID查询，需要返回 wait_id 和 sample_id 对应的样品详细信息
            // 如果是按样品信息查询，需要返回 device_info 的 status
            List<Map<String, Object>> dataMapList = convertToMapList(dataList, startDateTime, endDateTime, sampleInfoList, deviceIdToSampleMap, sampleIdToSampleMap,
                (category != null && !category.trim().isEmpty()) || (model != null && !model.trim().isEmpty()) || (tester != null && !tester.trim().isEmpty()));
            
            // 添加调试信息：检查样品是否在测试中（基于sample_id判断），并计算测试和等候时间段
            List<Map<String, Object>> sampleDebugInfo = new ArrayList<>();
            if (sampleInfoList != null && !sampleInfoList.isEmpty()) {
                for (DeviceInfo sample : sampleInfoList) {
                    Map<String, Object> debugMap = new HashMap<>();
                    debugMap.put("id", sample.getId()); // 添加样品ID
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
                    
                    // 计算测试时间段和等候时间段
                    List<Map<String, Object>> testingPeriods = new ArrayList<>(); // 测试时间段列表
                    List<Map<String, Object>> waitingPeriods = new ArrayList<>(); // 等候时间段列表
                    
                    if (sampleId != null) {
                        String sampleIdStr = String.valueOf(sampleId);
                        String debugDeviceId = sample.getDeviceId();
                        
                        // 【重要】判断测试是否结束的唯一标准：sample_id 或 wait_id 字段是否还包含该样品ID
                        // - sample_id 包含：样品正在测试中
                        // - wait_id 包含：样品在等候预约中
                        // 不能使用运行状态（runStatus）来判断，因为温箱可能因为报错而停止，
                        // 但样品实际上还在测试中（只是温箱暂时停止工作）
                        
                        // 查询所有包含该样品ID的数据（包括sample_id和wait_id）
                        List<ReliabilityLabData> allSampleData = reliabilityLabDataDao.selectBySampleIdAndDevice(
                            sampleIdStr, debugDeviceId, null, null, 0, Integer.MAX_VALUE
                        );
                        
                        if (allSampleData != null && !allSampleData.isEmpty()) {
                            // 按时间排序
                            allSampleData.sort((a, b) -> {
                                if (a.getCreatedAt() == null || b.getCreatedAt() == null) {
                                    return 0;
                                }
                                return a.getCreatedAt().compareTo(b.getCreatedAt());
                            });
                            
                            // 分析时间段：遍历数据，识别测试和等候的连续时间段
                            LocalDateTime currentTestingStart = null;
                            LocalDateTime currentWaitingStart = null;
                            LocalDateTime lastTime = null;
                            
                            for (int i = 0; i < allSampleData.size(); i++) {
                                ReliabilityLabData data = allSampleData.get(i);
                                LocalDateTime dataTime = data.getCreatedAt();
                                if (dataTime == null) continue;
                                
                                boolean inSampleId = containsSampleId(data.getSampleId(), sampleIdStr);
                                boolean inWaitId = containsWaitId(data.getWaitId(), sampleIdStr);
                                
                                // 处理测试时间段
                                if (inSampleId) {
                                    if (currentTestingStart == null) {
                                        // 开始新的测试时间段
                                        currentTestingStart = dataTime;
                                    }
                                    // 如果之前有等候时间段，结束它（使用当前数据时间作为结束时间）
                                    if (currentWaitingStart != null) {
                                        waitingPeriods.add(createPeriodMap(currentWaitingStart, dataTime));
                                        currentWaitingStart = null;
                                    }
                                } else if (currentTestingStart != null) {
                                    // 测试时间段结束（使用当前数据时间作为结束时间）
                                    testingPeriods.add(createPeriodMap(currentTestingStart, dataTime));
                                    currentTestingStart = null;
                                }
                                
                                // 处理等候时间段
                                if (inWaitId && !inSampleId) {
                                    if (currentWaitingStart == null) {
                                        // 开始新的等候时间段
                                        currentWaitingStart = dataTime;
                                    }
                                } else if (currentWaitingStart != null && !inWaitId) {
                                    // 等候时间段结束（使用当前数据时间作为结束时间）
                                    waitingPeriods.add(createPeriodMap(currentWaitingStart, dataTime));
                                    currentWaitingStart = null;
                                }
                                
                                lastTime = dataTime;
                            }
                            
                            // 处理最后的时间段（如果还在进行中，使用最后一条数据的时间作为结束时间）
                            if (currentTestingStart != null && lastTime != null) {
                                testingPeriods.add(createPeriodMap(currentTestingStart, lastTime));
                            }
                            if (currentWaitingStart != null && lastTime != null) {
                                waitingPeriods.add(createPeriodMap(currentWaitingStart, lastTime));
                            }
                            
                            // 查询最后一条包含该样品ID的数据（同时查询 sample_id 和 wait_id）
                            ReliabilityLabData lastData = reliabilityLabDataDao.selectLastBySampleId(sampleIdStr, debugDeviceId);
                            if (lastData != null) {
                                LocalDateTime lastDataTime = lastData.getCreatedAt();
                                // 查询下一条数据
                                ReliabilityLabData nextData = reliabilityLabDataDao.selectFirstAfterTime(debugDeviceId, lastDataTime);
                                
                                if (nextData != null) {
                                    // 如果下一条数据不包含该样品ID（sample_id 和 wait_id 都不包含），说明样品已经被移除，测试已结束
                                    if (!containsSampleIdInData(nextData, sampleIdStr)) {
                                        isTesting = false;
                                        testStatusMessage = "测试已结束（下一条数据已移除样品ID，sample_id和wait_id都不包含）";
                                    } else {
                                        // 下一条数据还包含该样品ID（sample_id 或 wait_id 包含），说明测试还在进行中或还在等候
                                        // 即使温箱运行状态为0（停止），只要sample_id或wait_id还包含该样品ID，就认为测试还在进行中或还在等候
                                        isTesting = true;
                                        testStatusMessage = "测试进行中或等候中（下一条数据仍包含样品ID）";
                                    }
                                } else {
                                    // 没有下一条数据，检查最新数据
                                    ReliabilityLabData latestData = reliabilityLabDataDao.selectLatestDataByDeviceId(debugDeviceId);
                                    if (latestData != null && containsSampleIdInData(latestData, sampleIdStr)) {
                                        // 最新数据还包含该样品ID（sample_id 或 wait_id 包含），说明测试还在进行中或还在等候
                                        // 即使温箱运行状态为0（停止），只要sample_id或wait_id还包含该样品ID，就认为测试还在进行中或还在等候
                                        isTesting = true;
                                        testStatusMessage = "测试进行中或等候中（最新数据仍包含样品ID）";
                                    } else {
                                        // 最新数据不包含该样品ID（sample_id 和 wait_id 都不包含），说明样品已经被移除，测试已结束
                                        isTesting = false;
                                        testStatusMessage = "测试已结束（最新数据已移除样品ID，sample_id和wait_id都不包含）";
                                    }
                                }
                            } else {
                                testStatusMessage = "未找到包含该样品ID的数据";
                            }
                        } else {
                            testStatusMessage = "未找到包含该样品ID的数据";
                        }
                    }
                    
                    debugMap.put("isTesting", isTesting);
                    debugMap.put("message", testStatusMessage);
                    debugMap.put("testingPeriods", testingPeriods); // 测试时间段列表
                    debugMap.put("waitingPeriods", waitingPeriods); // 等候时间段列表
                    
                    // 添加测试时长信息（如果存在）
                    if (sampleTestDurationMap.containsKey(sampleId)) {
                        Map<String, Object> durationInfo = sampleTestDurationMap.get(sampleId);
                        debugMap.put("testStartTime", durationInfo.get("startTime"));
                        debugMap.put("testEndTime", durationInfo.get("endTime"));
                        debugMap.put("testDurationSeconds", durationInfo.get("durationSeconds"));
                        debugMap.put("testDurationFormatted", durationInfo.get("durationFormatted"));
                    }
                    
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
     * 根据样品信息查找样品列表（直接在 device_info 表中查询）
     * 支持根据品类、型号、测试人员模糊匹配查询
     */
    private List<DeviceInfo> findSamplesByInfo(String category, String model, String tester) {
        // 直接在 device_info 表中根据条件查询匹配的样品
        return deviceInfoDao.selectByCategoryModelTester(category, model, tester);
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
     * @param isSampleSearch 是否是按样品信息搜索（true=按样品搜索，false=按设备ID搜索）
     */
    private List<Map<String, Object>> convertToMapList(List<ReliabilityLabData> dataList, 
                                                        LocalDateTime startTime, 
                                                        LocalDateTime endTime,
                                                        List<DeviceInfo> sampleInfoList,
                                                        Map<String, DeviceInfo> deviceIdToSampleMap,
                                                        Map<Long, DeviceInfo> sampleIdToSampleMap,
                                                        boolean isSampleSearch) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 用于缓存设备ID对应的样品信息列表（用于按设备ID查询时）
        Map<String, List<DeviceInfo>> deviceSampleCache = new HashMap<>();
        
        for (ReliabilityLabData data : dataList) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", data.getId());
            map.put("deviceId", data.getDeviceId());
            map.put("sampleId", data.getSampleId()); // 添加 sampleId 字段，用于前端分析测试过程
            map.put("waitId", data.getWaitId()); // 添加 waitId 字段，用于前端分析等候过程
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
            
            if (isSampleSearch) {
                // 按样品信息搜索：根据数据记录中的sample_id精确匹配对应的样品
                // 这样可以确保每个样品ID独立显示，不会因为设备ID相同而合并
                if (data.getSampleId() != null && !data.getSampleId().trim().isEmpty() && 
                    sampleIdToSampleMap != null && !sampleIdToSampleMap.isEmpty()) {
                    // 从sample_id字段中查找匹配的样品ID
                    String[] sampleIds = data.getSampleId().split(",");
                    for (String sampleIdStr : sampleIds) {
                        sampleIdStr = sampleIdStr.trim();
                        if (!sampleIdStr.isEmpty()) {
                            try {
                                Long sampleId = Long.parseLong(sampleIdStr);
                                DeviceInfo sample = sampleIdToSampleMap.get(sampleId);
                                if (sample != null) {
                                    Map<String, Object> sampleMap = new HashMap<>();
                                    sampleMap.put("id", sample.getId());
                                    sampleMap.put("category", sample.getCategory());
                                    sampleMap.put("model", sample.getModel());
                                    sampleMap.put("tester", sample.getTester());
                                    sampleMap.put("status", sample.getStatus());
                                    sampleMap.put("createdAt", sample.getCreatedAt());
                                    sampleMap.put("updatedAt", sample.getUpdatedAt());
                                    sampleInfoMapList.add(sampleMap);
                                    // 找到匹配的样品后，只添加第一个匹配的（因为一个数据记录通常只对应一个样品）
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                // 忽略无效的ID
                            }
                        }
                    }
                }
                // 如果sample_id中没有找到，尝试从wait_id中查找
                if (sampleInfoMapList.isEmpty() && data.getWaitId() != null && !data.getWaitId().trim().isEmpty() && 
                    sampleIdToSampleMap != null && !sampleIdToSampleMap.isEmpty()) {
                    String[] waitIds = data.getWaitId().split(",");
                    for (String waitIdStr : waitIds) {
                        waitIdStr = waitIdStr.trim();
                        if (!waitIdStr.isEmpty()) {
                            try {
                                Long waitId = Long.parseLong(waitIdStr);
                                DeviceInfo sample = sampleIdToSampleMap.get(waitId);
                                if (sample != null) {
                                    Map<String, Object> sampleMap = new HashMap<>();
                                    sampleMap.put("id", sample.getId());
                                    sampleMap.put("category", sample.getCategory());
                                    sampleMap.put("model", sample.getModel());
                                    sampleMap.put("tester", sample.getTester());
                                    sampleMap.put("status", sample.getStatus());
                                    sampleMap.put("createdAt", sample.getCreatedAt());
                                    sampleMap.put("updatedAt", sample.getUpdatedAt());
                                    sampleInfoMapList.add(sampleMap);
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                // 忽略无效的ID
                            }
                        }
                    }
                }
            } else {
                // 按设备ID搜索：返回 wait_id 和 sample_id 对应的样品详细信息
                // 获取 sample_id 对应的样品信息
                if (data.getSampleId() != null && !data.getSampleId().trim().isEmpty()) {
                    String[] sampleIds = data.getSampleId().split(",");
                    for (String sampleIdStr : sampleIds) {
                        sampleIdStr = sampleIdStr.trim();
                        if (!sampleIdStr.isEmpty()) {
                            try {
                                Long sampleId = Long.parseLong(sampleIdStr);
                                DeviceInfo sample = deviceInfoDao.selectById(sampleId);
                                if (sample != null) {
                                    Map<String, Object> sampleMap = new HashMap<>();
                                    sampleMap.put("id", sample.getId());
                                    sampleMap.put("category", sample.getCategory());
                                    sampleMap.put("model", sample.getModel());
                                    sampleMap.put("tester", sample.getTester());
                                    sampleMap.put("status", sample.getStatus());
                                    sampleMap.put("createdAt", sample.getCreatedAt());
                                    sampleMap.put("updatedAt", sample.getUpdatedAt());
                                    sampleMap.put("type", "testing"); // 标记为测试中
                                    sampleInfoMapList.add(sampleMap);
                                }
                            } catch (NumberFormatException e) {
                                // 忽略无效的ID
                            }
                        }
                    }
                }
                
                // 获取 wait_id 对应的样品信息
                if (data.getWaitId() != null && !data.getWaitId().trim().isEmpty()) {
                    String[] waitIds = data.getWaitId().split(",");
                    for (String waitIdStr : waitIds) {
                        waitIdStr = waitIdStr.trim();
                        if (!waitIdStr.isEmpty()) {
                            try {
                                Long waitId = Long.parseLong(waitIdStr);
                                DeviceInfo waitSample = deviceInfoDao.selectById(waitId);
                                if (waitSample != null) {
                                    Map<String, Object> waitMap = new HashMap<>();
                                    waitMap.put("id", waitSample.getId());
                                    waitMap.put("category", waitSample.getCategory());
                                    waitMap.put("model", waitSample.getModel());
                                    waitMap.put("tester", waitSample.getTester());
                                    waitMap.put("status", waitSample.getStatus());
                                    waitMap.put("createdAt", waitSample.getCreatedAt());
                                    waitMap.put("updatedAt", waitSample.getUpdatedAt());
                                    waitMap.put("type", "waiting"); // 标记为等候中
                                    sampleInfoMapList.add(waitMap);
                                }
                            } catch (NumberFormatException e) {
                                // 忽略无效的ID
                            }
                        }
                    }
                }
            }
            
            // 所有情况下都使用原始数据时间（不替换为样品创建时间）
            map.put("createdAt", data.getCreatedAt());
            map.put("updatedAt", data.getUpdatedAt());
            
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
    
    /**
     * 检查wait_id字段是否包含指定的样品ID
     * wait_id可能是单个ID（如 "1"）或多个ID用逗号分隔（如 "1,22,33"）
     * @param waitIdField wait_id字段的值
     * @param targetSampleId 要查找的样品ID（字符串格式）
     * @return 如果包含则返回true，否则返回false
     */
    private boolean containsWaitId(String waitIdField, String targetSampleId) {
        if (waitIdField == null || waitIdField.trim().isEmpty()) {
            return false;
        }
        if (targetSampleId == null || targetSampleId.trim().isEmpty()) {
            return false;
        }
        
        // 去除空格
        String field = waitIdField.trim();
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
    
    /**
     * 检查数据记录是否包含指定的样品ID（同时检查 sample_id 和 wait_id 字段）
     * @param data 数据记录
     * @param targetSampleId 要查找的样品ID（字符串格式）
     * @return 如果包含则返回true，否则返回false
     */
    private boolean containsSampleIdInData(ReliabilityLabData data, String targetSampleId) {
        if (data == null || targetSampleId == null || targetSampleId.trim().isEmpty()) {
            return false;
        }
        
        // 检查 sample_id 字段（正在测试中）
        if (containsSampleId(data.getSampleId(), targetSampleId)) {
            return true;
        }
        
        // 检查 wait_id 字段（等候预约）
        if (containsWaitId(data.getWaitId(), targetSampleId)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 创建时间段Map
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间段Map，包含startTime和endTime
     */
    private Map<String, Object> createPeriodMap(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> periodMap = new HashMap<>();
        periodMap.put("startTime", startTime);
        periodMap.put("endTime", endTime);
        return periodMap;
    }
}

