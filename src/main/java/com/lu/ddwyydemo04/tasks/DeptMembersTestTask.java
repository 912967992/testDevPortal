package com.lu.ddwyydemo04.tasks;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiV2UserListRequest;
import com.dingtalk.api.request.OapiV2DepartmentGetRequest;
import com.dingtalk.api.response.OapiV2UserListResponse;
import com.dingtalk.api.response.OapiV2DepartmentGetResponse;
import com.lu.ddwyydemo04.Service.AccessTokenService;
import com.lu.ddwyydemo04.dao.UserDao;
import com.lu.ddwyydemo04.pojo.User;
import com.taobao.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门成员获取测试定时任务
 * 用于测试钉钉API获取部门成员功能
 */
@Component
public class DeptMembersTestTask {

    private static final Logger log = LoggerFactory.getLogger(DeptMembersTestTask.class);

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private UserDao userDao;

    // 测试用的部门ID，可以根据需要修改
    // 523459714 是电子测试组的编号（从代码中看到的）
    // 523645469 是可靠性实验室的编号（从代码中看到的）
    private static final Long TEST_DEPT_ID = 523645469L;

    // 部门名称缓存，避免重复调用API
    private final Map<Long, String> deptNameCache = new HashMap<>();

    /**
     * 定时测试获取部门成员
     * 每1分钟执行一次（测试用，可以调整）
     * 
     * 如果需要修改执行频率：
     * - fixedRate = 60000 表示每60秒执行一次
     * - fixedRate = 300000 表示每5分钟执行一次
     * - cron = "0 0/1 * * * ?" 表示每分钟的第0秒执行
     * - cron = "0 0 0/1 * * ?" 表示每小时执行一次
     */
    @Scheduled(cron = "0 45 06 * * ?")  // 定时任务获取users
//    @Scheduled(fixedRate = 20000) // 每1分钟执行一次，用于测试
    public void testGetDeptMembers() {
        try {
            log.info("========== 开始测试获取部门成员 ==========");
            log.info("测试部门ID: {}", TEST_DEPT_ID);

            // 获取accessToken
            String accessToken = accessTokenService.getAccessToken();
            log.info("✅ 成功获取 accessToken");

            // 调用钉钉API获取部门成员列表
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/user/list");
            OapiV2UserListRequest request = new OapiV2UserListRequest();
            request.setDeptId(TEST_DEPT_ID);
            request.setCursor(0L);  // 分页游标，从0开始
            request.setSize(100L);  // 每页大小，最大100

            OapiV2UserListResponse response = client.execute(request, accessToken);

            if (response.getErrcode() == 0) {
                // 解析返回结果
                List<Map<String, Object>> memberList = new ArrayList<>();
                String responseBody = response.getBody();
                JSONObject responseJson = JSON.parseObject(responseBody);

                if (responseJson.getInteger("errcode") == 0) {
                    JSONObject resultObj = responseJson.getJSONObject("result");
                    if (resultObj != null) {
                        JSONArray list = resultObj.getJSONArray("list");
                        if (list != null) {
                            // 解析第一页数据
                            for (int i = 0; i < list.size(); i++) {
                                JSONObject userObj = list.getJSONObject(i);
                                Map<String, Object> member = new HashMap<>();
                                member.put("userId", userObj.getString("userid"));
                                member.put("name", userObj.getString("name"));
                                member.put("mobile", userObj.getString("mobile"));
                                member.put("email", userObj.getString("email"));
                                member.put("jobNumber", userObj.getString("job_number"));
                                member.put("title", userObj.getString("title"));
                                member.put("deptIdList", userObj.getJSONArray("dept_id_list"));
                                memberList.add(member);
                            }
                        }

                        // 处理分页：如果还有更多数据，继续获取
                        Long nextCursor = resultObj.getLong("next_cursor");
                        while (nextCursor != null && nextCursor > 0) {
                            request.setCursor(nextCursor);
                            response = client.execute(request, accessToken);
                            responseBody = response.getBody();
                            responseJson = JSON.parseObject(responseBody);

                            if (responseJson.getInteger("errcode") == 0) {
                                resultObj = responseJson.getJSONObject("result");
                                if (resultObj != null) {
                                    list = resultObj.getJSONArray("list");
                                    if (list != null) {
                                        for (int i = 0; i < list.size(); i++) {
                                            JSONObject userObj = list.getJSONObject(i);
                                            Map<String, Object> member = new HashMap<>();
                                            member.put("userId", userObj.getString("userid"));
                                            member.put("name", userObj.getString("name"));
                                            member.put("mobile", userObj.getString("mobile"));
                                            member.put("email", userObj.getString("email"));
                                            member.put("jobNumber", userObj.getString("job_number"));
                                            member.put("title", userObj.getString("title"));
                                            member.put("deptIdList", userObj.getJSONArray("dept_id_list"));
                                            memberList.add(member);
                                        }
                                    }
                                    nextCursor = resultObj.getLong("next_cursor");
                                } else {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }

                // 输出结果并保存到数据库
                log.info("✅ 成功获取部门成员，共 {} 人", memberList.size());
                log.info("========== 开始保存到数据库 ==========");
                
                int successCount = 0;
                int failCount = 0;
                
                for (Map<String, Object> member : memberList) {
                    try {
                        // 转换为User对象（传入accessToken以便获取部门名称）
                        User user = convertToUser(member, TEST_DEPT_ID, accessToken);
                        
                        // 保存到数据库（如果已存在则更新，不存在则插入）
                        int result = userDao.insertOrUpdate(user);
                        if (result > 0) {
                            successCount++;
                            log.debug("✅ 保存用户成功: {} (ID: {})", user.getUsername(), user.getUserId());
                        } else {
                            failCount++;
                            log.warn("⚠️ 保存用户失败: {} (ID: {})", user.getUsername(), user.getUserId());
                        }
                    } catch (Exception e) {
                        failCount++;
                        log.error("❌ 保存用户异常: {} - {}", member.get("name"), e.getMessage(), e);
                    }
                }
                
                log.info("========== 保存完成 ==========");
                log.info("✅ 成功保存: {} 人", successCount);
                if (failCount > 0) {
                    log.warn("⚠️ 保存失败: {} 人", failCount);
                }
                
                log.info("========== 部门成员列表 ==========");
                for (Map<String, Object> member : memberList) {
                    log.info("  - 姓名: {}, 工号: {}, 职位: {}, 用户ID: {}", 
                        member.get("name"), 
                        member.get("jobNumber"), 
                        member.get("title"),
                        member.get("userId"));
                }
                log.info("========== 测试完成 ==========");

            } else {
                log.error("❌ 获取部门成员失败: errcode={}, errmsg={}", 
                    response.getErrcode(), response.getErrmsg());
            }

        } catch (ApiException e) {
            log.error("❌ 调用钉钉API异常: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ 测试任务执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发测试方法（可选）
     * 可以通过其他方式调用这个方法进行测试
     */
    public void testGetDeptMembers(Long deptId) {
        try {
            log.info("========== 手动测试获取部门成员 ==========");
            log.info("测试部门ID: {}", deptId);

            String accessToken = accessTokenService.getAccessToken();
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/user/list");
            OapiV2UserListRequest request = new OapiV2UserListRequest();
            request.setDeptId(deptId);
            request.setCursor(0L);
            request.setSize(100L);

            OapiV2UserListResponse response = client.execute(request, accessToken);

            if (response.getErrcode() == 0) {
                String responseBody = response.getBody();
                JSONObject responseJson = JSON.parseObject(responseBody);
                JSONObject resultObj = responseJson.getJSONObject("result");
                
                if (resultObj != null) {
                    JSONArray list = resultObj.getJSONArray("list");
                    log.info("✅ 部门 {} 共有 {} 个成员", deptId, list != null ? list.size() : 0);
                }
            } else {
                log.error("❌ 获取失败: errcode={}, errmsg={}", 
                    response.getErrcode(), response.getErrmsg());
            }

        } catch (Exception e) {
            log.error("❌ 测试异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 将Map格式的成员信息转换为User对象
     * 
     * @param member Map格式的成员信息
     * @param currentDeptId 当前查询的部门ID（作为主要部门ID）
     * @param accessToken 钉钉访问令牌，用于获取部门名称
     * @return User对象
     */
    private User convertToUser(Map<String, Object> member, Long currentDeptId, String accessToken) {
        User user = new User();
        
        // 基本信息
        user.setUserId((String) member.get("userId"));
        user.setUsername((String) member.get("name"));
        user.setPosition((String) member.get("title"));  // 职位
        user.setJobNumber((String) member.get("jobNumber"));  // 工号
        
        // 处理部门ID列表
        JSONArray deptIdList = (JSONArray) member.get("deptIdList");
        Long finalDeptId = currentDeptId;  // 最终使用的部门ID
        
        if (deptIdList != null && deptIdList.size() > 0) {
            // 取第一个部门ID作为主要部门ID
            Long firstDeptId = deptIdList.getLong(0);
            user.setMajorDeptId(firstDeptId);
            user.setDeptId(firstDeptId);
            finalDeptId = firstDeptId;
            
            // 如果当前查询的部门ID在列表中，使用当前部门ID
            for (int i = 0; i < deptIdList.size(); i++) {
                if (currentDeptId.equals(deptIdList.getLong(i))) {
                    user.setDeptId(currentDeptId);
                    finalDeptId = currentDeptId;
                    break;
                }
            }
        } else {
            // 如果没有部门列表，使用当前查询的部门ID
            user.setDeptId(currentDeptId);
            user.setMajorDeptId(currentDeptId);
        }
        
        // 获取部门名称
        String deptName = getDeptName(finalDeptId, accessToken);
        user.setDepartmentName(deptName);
        
        // 入职日期暂时为空，钉钉API返回的数据中可能没有这个字段
        user.setHireDate(null);
        
        return user;
    }

    /**
     * 根据部门ID获取部门名称
     * 使用缓存避免重复调用API
     * 
     * @param deptId 部门ID
     * @param accessToken 钉钉访问令牌
     * @return 部门名称，如果获取失败返回null
     */
    private String getDeptName(Long deptId, String accessToken) {
        if (deptId == null) {
            return null;
        }
        
        // 先从缓存中获取
        if (deptNameCache.containsKey(deptId)) {
            return deptNameCache.get(deptId);
        }
        
        try {
            // 调用钉钉API获取部门详情
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/department/get");
            OapiV2DepartmentGetRequest request = new OapiV2DepartmentGetRequest();
            request.setDeptId(deptId);
            request.setLanguage("zh_CN");
            
            OapiV2DepartmentGetResponse response = client.execute(request, accessToken);
            
            if (response.getErrcode() == 0) {
                // 解析返回结果
                String responseBody = response.getBody();
                JSONObject responseJson = JSON.parseObject(responseBody);
                
                if (responseJson.getInteger("errcode") == 0) {
                    JSONObject resultObj = responseJson.getJSONObject("result");
                    if (resultObj != null) {
                        String deptName = resultObj.getString("name");
                        // 存入缓存
                        deptNameCache.put(deptId, deptName);
                        log.debug("✅ 获取部门名称成功: {} -> {}", deptId, deptName);
                        return deptName;
                    }
                }
            } else {
                log.warn("⚠️ 获取部门名称失败: deptId={}, errcode={}, errmsg={}", 
                    deptId, response.getErrcode(), response.getErrmsg());
            }
        } catch (Exception e) {
            log.error("❌ 获取部门名称异常: deptId={}, error={}", deptId, e.getMessage());
        }
        
        return null;
    }
}

