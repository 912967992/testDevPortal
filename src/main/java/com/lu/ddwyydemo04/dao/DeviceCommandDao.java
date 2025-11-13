package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.DeviceCommand;

import java.util.List;

public interface DeviceCommandDao {
    /**
     * 插入新的执行命令
     */
    int insert(DeviceCommand deviceCommand);

    /**
     * 根据ID查询命令
     */
    DeviceCommand selectById(Long id);

    /**
     * 根据设备ID查询命令列表
     */
    List<DeviceCommand> selectByDeviceId(String deviceId);

    /**
     * 查询所有命令
     */
    List<DeviceCommand> selectAll();

    List<DeviceCommand> selectCommandsWithNon200StatusCode(String deviceId);
    
    /**
     * 查询指定设备未完成的命令（取最新一条）
     */
    DeviceCommand selectPendingCommand(String deviceId);
    
    /**
     * 更新命令完成状态
     */
    int updateFinishStatus(Long id, Integer isFinished);
}

