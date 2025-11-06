package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.DeviceCommand;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DeviceCommandDao {
    /**
     * 插入新的执行命令
     */
    int insert(DeviceCommand deviceCommand);

    /**
     * 根据设备ID和状态查询命令列表
     * @param deviceId 设备ID，可为null（查询所有设备）
     * @param status 状态，可为null（查询所有状态）
     * @return 命令列表
     */
    List<DeviceCommand> selectByDeviceIdAndStatus(@Param("deviceId") String deviceId, @Param("status") String status);

    /**
     * 根据ID查询命令
     */
    DeviceCommand selectById(Long id);

    /**
     * 更新命令状态和反馈信息
     */
    int updateFeedback(@Param("id") Long id, 
                       @Param("status") String status,
                       @Param("feedbackStatusCode") String feedbackStatusCode,
                       @Param("feedbackMessage") String feedbackMessage);

    /**
     * 更新命令状态为执行中
     */
    int updateStatusToExecuting(Long id);

    /**
     * 查询待执行的命令（状态为pending）
     */
    List<DeviceCommand> selectPendingCommands(@Param("deviceId") String deviceId);

    /**
     * 查询状态码不是200的命令
     * @param deviceId 设备ID，可为null（查询所有设备）
     * @return 命令列表
     */
    List<DeviceCommand> selectCommandsWithNon200StatusCode(@Param("deviceId") String deviceId);
}

