package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.DeviceInfo;
import org.apache.ibatis.annotations.Param;

/**
 * 设备信息DAO接口
 */
public interface DeviceInfoDao {
    /**
     * 根据设备ID查询设备信息（查询第一条，兼容旧代码）
     */
    DeviceInfo selectByDeviceId(String deviceId);
    
    /**
     * 根据设备ID查询所有样品信息
     */
    java.util.List<DeviceInfo> selectAllByDeviceId(String deviceId);

    /**
     * 插入设备信息（样品）
     */
    int insert(DeviceInfo deviceInfo);
    
    /**
     * 根据ID查询样品信息
     */
    DeviceInfo selectById(Long id);

    /**
     * 更新设备信息（样品）
     */
    int update(DeviceInfo deviceInfo);
    
    /**
     * 根据ID删除样品信息
     */
    int deleteById(Long id);

    /**
     * 根据设备ID删除所有设备信息（删除设备时使用）
     */
    int deleteByDeviceId(String deviceId);

    /**
     * 查询设备当前正在测试的样品（created_at <= now AND updated_at = created_at）
     * @param deviceId 设备ID
     * @return 当前正在测试的样品，如果没有则返回null
     */
    DeviceInfo selectCurrentTestingSample(@Param("deviceId") String deviceId);
}

