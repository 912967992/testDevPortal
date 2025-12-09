package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户信息DAO接口
 */
public interface UserDao {
    /**
     * 根据userId查询用户信息
     */
    User selectByUserId(String userId);

    /**
     * 插入用户信息
     */
    int insert(User user);

    /**
     * 根据userId更新用户信息
     */
    int updateByUserId(User user);

    /**
     * 根据userId删除用户
     */
    int deleteByUserId(String userId);

    /**
     * 查询所有用户
     */
    List<User> selectAll();

    /**
     * 根据部门ID查询用户列表
     */
    List<User> selectByDeptId(@Param("deptId") Long deptId);

    /**
     * 插入或更新用户（如果userId存在则更新，不存在则插入）
     */
    int insertOrUpdate(User user);

    /**
     * 根据username查询用户信息
     */
    User selectByUsername(@Param("username") String username);
}

