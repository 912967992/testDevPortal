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
     * 查询设备当前正在测试的样品（status = 'TESTING'）
     * @param deviceId 设备ID
     * @return 当前正在测试的样品，如果没有则返回null
     */
    DeviceInfo selectCurrentTestingSample(@Param("deviceId") String deviceId);
    
    /**
     * 根据品类、型号、测试人员查询样品信息（支持模糊匹配）
     * @param category 品类（可选，支持模糊匹配）
     * @param model 型号（可选，支持模糊匹配）
     * @param tester 测试人员（可选，支持模糊匹配）
     * @return 匹配的样品信息列表
     */
    java.util.List<DeviceInfo> selectByCategoryModelTester(
        @Param("category") String category,
        @Param("model") String model,
        @Param("tester") String tester
    );
    
    /**
     * 根据样品ID列表批量查询样品信息
     * @param sampleIds 样品ID列表
     * @return 样品信息列表
     */
    java.util.List<DeviceInfo> selectByIds(@Param("sampleIds") java.util.List<Long> sampleIds);
}

