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
}


