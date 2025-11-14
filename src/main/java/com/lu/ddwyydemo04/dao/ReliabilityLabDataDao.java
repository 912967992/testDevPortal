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
     * 查找超过指定秒数未更新的设备ID列表
     * @param seconds 超时秒数（例如：15秒）
     * @return 超时设备的ID列表
     */
    List<String> selectTimeoutDeviceIds(@Param("seconds") int seconds);

    /**
     * 批量更新设备的串口连接状态为离线，同时更新模块连接状态为异常
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
}


