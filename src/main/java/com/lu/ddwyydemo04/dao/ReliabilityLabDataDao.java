package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.ReliabilityLabData;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReliabilityLabDataDao {
    int insert(ReliabilityLabData data);
    ReliabilityLabData selectLatest();
    
    /**
     * 根据设备ID查询最新数据
     */
    ReliabilityLabData selectLatestByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 获取每台设备的最新一条数据
     */
    List<ReliabilityLabData> selectLatestPerDevice();

    /**
     * 更新temperature_box_latest_data表中的设备数据
     */
    int updateLatestData(ReliabilityLabData data);

    /**
     * 向temperature_box_latest_data表插入新设备数据
     */
    int insertLatestData(ReliabilityLabData data);

    /**
     * 根据设备ID查询temperature_box_latest_data中的数据
     */
    ReliabilityLabData selectLatestDataByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 获取所有设备的最新数据（用于启动时缓存预热）
     */
    List<ReliabilityLabData> selectAllLatestData();

    /**
     * 查找超过指定秒数未更新的设备ID列表（只查询module_connection='连接正常'的设备）
     * @param seconds 超时秒数（例如：15秒）
     * @return 超时设备的ID列表
     */
    List<String> selectTimeoutDeviceIds(@Param("seconds") int seconds);

    /**
     * 批量更新设备的模块连接状态为异常（前端根据此字段显示），同时更新串口状态为离线（保持数据完整性）
     * @param deviceIds 设备ID列表
     * @return 更新的记录数
     */
    int batchUpdateSerialStatusToOffline(@Param("deviceIds") List<String> deviceIds);

    /**
     * 根据设备ID删除temperature_box_latest_data表中的记录
     * @param deviceId 设备ID
     * @return 删除的记录数
     */
    int deleteLatestDataByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 查询历史数据（用于OEE分析）
     * @param deviceId 设备ID（可选）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param offset 分页偏移量
     * @param limit 每页数量
     * @return 历史数据列表
     */
    List<ReliabilityLabData> selectHistoryData(
        @Param("deviceId") String deviceId,
        @Param("startTime") java.time.LocalDateTime startTime,
        @Param("endTime") java.time.LocalDateTime endTime,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    /**
     * 统计历史数据总数（用于分页）
     * @param deviceId 设备ID（可选）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 记录总数
     */
    int countHistoryData(
        @Param("deviceId") String deviceId,
        @Param("startTime") java.time.LocalDateTime startTime,
        @Param("endTime") java.time.LocalDateTime endTime
    );

    /**
     * 查询某个设备在指定时间点之前最近的一条数据
     * @param deviceId 设备ID
     * @param beforeTime 指定时间点
     * @return 距离指定时间点之前最近的一条数据
     */
    ReliabilityLabData selectLatestBeforeTime(
        @Param("deviceId") String deviceId,
        @Param("beforeTime") java.time.LocalDateTime beforeTime
    );

    /**
     * 查询某个设备最早的一条历史数据
     * @param deviceId 设备ID
     * @return 该设备最早的一条历史数据
     */
    ReliabilityLabData selectEarliestByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 根据样品ID查询历史数据（支持多个ID，格式如 "1,22,33"）
     * @param sampleId 样品ID（可以是单个ID字符串，也可以是多个ID用逗号分隔）
     * @return 该样品的所有历史数据
     */
    List<ReliabilityLabData> selectBySampleId(@Param("sampleId") String sampleId);
    
    /**
     * 根据样品ID和设备ID查询历史数据（优化查询，先按设备过滤）
     * @param sampleId 样品ID
     * @param deviceId 设备ID
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param offset 分页偏移量
     * @param limit 每页数量
     * @return 该样品的所有历史数据
     */
    List<ReliabilityLabData> selectBySampleIdAndDevice(
        @Param("sampleId") String sampleId,
        @Param("deviceId") String deviceId,
        @Param("startTime") java.time.LocalDateTime startTime,
        @Param("endTime") java.time.LocalDateTime endTime,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
    
    /**
     * 根据样品ID查询第一条包含该样品ID的数据
     * @param sampleId 样品ID
     * @param deviceId 设备ID（可选，如果提供则只查询该设备的数据）
     * @return 第一条包含该样品ID的数据
     */
    ReliabilityLabData selectFirstBySampleId(
        @Param("sampleId") String sampleId,
        @Param("deviceId") String deviceId
    );
    
    /**
     * 根据样品ID查询最后一条包含该样品ID的数据
     * @param sampleId 样品ID
     * @param deviceId 设备ID（可选，如果提供则只查询该设备的数据）
     * @return 最后一条包含该样品ID的数据
     */
    ReliabilityLabData selectLastBySampleId(
        @Param("sampleId") String sampleId,
        @Param("deviceId") String deviceId
    );
    
    /**
     * 查询指定设备在指定时间之后的第一条数据
     * @param deviceId 设备ID
     * @param afterTime 指定时间点
     * @return 指定时间之后的第一条数据
     */
    ReliabilityLabData selectFirstAfterTime(
        @Param("deviceId") String deviceId,
        @Param("afterTime") java.time.LocalDateTime afterTime
    );

    /**
     * 根据设备ID追加 sample_id 到 reliabilitylabdata 表中最后一条记录
     * @param deviceId 设备ID
     * @param sampleId 样品ID（Long类型，会自动转换为字符串并追加）
     * @return 更新的记录数
     */
    int updateLatestSampleIdByDeviceId(@Param("deviceId") String deviceId, @Param("sampleId") Long sampleId);

    /**
     * 根据设备ID追加 sample_id 到 temperature_box_latest_data 表中
     * @param deviceId 设备ID
     * @param sampleId 样品ID（Long类型，会自动转换为字符串并追加）
     * @return 更新的记录数
     */
    int updateLatestDataSampleIdByDeviceId(@Param("deviceId") String deviceId, @Param("sampleId") Long sampleId);

    /**
     * 只更新 temperature_box_latest_data 表的 updated_at 字段（用于数据无变化时刷新更新时间）
     * @param deviceId 设备ID
     * @return 更新的记录数
     */
    int updateLatestDataTimestamp(@Param("deviceId") String deviceId);
}


