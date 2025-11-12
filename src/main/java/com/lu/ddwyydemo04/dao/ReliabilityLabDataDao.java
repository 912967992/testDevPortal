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
}


