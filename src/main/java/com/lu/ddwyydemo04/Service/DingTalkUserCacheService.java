package com.lu.ddwyydemo04.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 钉钉用户信息缓存服务
 * 用于缓存钉钉用户的详细信息，避免重复调用钉钉API
 */
@Service
public class DingTalkUserCacheService {

    @Autowired
    private RedisService redisService;

    private static final String USER_INFO_CACHE_PREFIX = "dingtalk:user:info:";

    /**
     * 用户信息缓存对象
     */
    public static class UserInfo {
        private String userId;
        private String username;
        private String job;
        private String departmentId;
        private String corpId;
        private String templatespath;
        private String imagepath;
        private String savepath;

        // 构造函数
        public UserInfo() {}

        public UserInfo(String userId, String username, String job, String departmentId, String corpId,
                       String templatespath, String imagepath, String savepath) {
            this.userId = userId;
            this.username = username;
            this.job = job;
            this.departmentId = departmentId;
            this.corpId = corpId;
            this.templatespath = templatespath;
            this.imagepath = imagepath;
            this.savepath = savepath;
        }

        // Getter和Setter方法
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getJob() { return job; }
        public void setJob(String job) { this.job = job; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getCorpId() { return corpId; }
        public void setCorpId(String corpId) { this.corpId = corpId; }

        public String getTemplatespath() { return templatespath; }
        public void setTemplatespath(String templatespath) { this.templatespath = templatespath; }

        public String getImagepath() { return imagepath; }
        public void setImagepath(String imagepath) { this.imagepath = imagepath; }

        public String getSavepath() { return savepath; }
        public void setSavepath(String savepath) { this.savepath = savepath; }

        /**
         * 转换为Map格式，用于返回给前端
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userId);
            map.put("username", username);
            map.put("job", job);
            map.put("departmentId", departmentId);
            map.put("corp_id", corpId);
            map.put("templatespath", templatespath);
            map.put("imagepath", imagepath);
            map.put("savepath", savepath);
            return map;
        }
    }

    /**
     * 根据用户名获取缓存的用户信息（遍历所有缓存）
     * @param username 用户名
     * @return 用户信息对象，如果缓存中不存在则返回null
     */
    public UserInfo getUserInfoByUsername(String username) {
        try {
            System.out.println("尝试通过用户名从缓存获取用户信息: " + username);
            
            // 获取所有用户信息缓存键
            java.util.Set<String> userKeys = redisService.keys(USER_INFO_CACHE_PREFIX + "*");
            
            if (userKeys == null || userKeys.isEmpty()) {
                System.out.println("缓存中没有任何用户信息");
                return null;
            }
            
            System.out.println("开始遍历 " + userKeys.size() + " 个用户缓存...");
            
            // 遍历所有用户缓存，查找匹配的 username
            for (String cacheKey : userKeys) {
                try {
                    Map<Object, Object> cachedData = redisService.hGetAll(cacheKey);
                    
                    if (cachedData != null && !cachedData.isEmpty()) {
                        String cachedUsername = (String) cachedData.get("username");
                        
                        // 匹配用户名
                        if (username.equals(cachedUsername)) {
                            System.out.println("✅ 找到匹配的用户信息: " + username);
                            
                            UserInfo userInfo = new UserInfo();
                            userInfo.setUserId((String) cachedData.get("userId"));
                            userInfo.setUsername((String) cachedData.get("username"));
                            userInfo.setJob((String) cachedData.get("job"));
                            userInfo.setDepartmentId((String) cachedData.get("departmentId"));
                            userInfo.setCorpId((String) cachedData.get("corpId"));
                            userInfo.setTemplatespath((String) cachedData.get("templatespath"));
                            userInfo.setImagepath((String) cachedData.get("imagepath"));
                            userInfo.setSavepath((String) cachedData.get("savepath"));
                            
                            return userInfo;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("处理缓存键失败: " + cacheKey + ", 错误: " + e.getMessage());
                }
            }
            
            System.out.println("⚠️ 未找到用户名匹配的缓存: " + username);
            return null;
            
        } catch (Exception e) {
            System.err.println("通过用户名获取用户信息失败: " + username + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 根据用户ID获取缓存的用户信息
     * @param userId 用户ID
     * @return 用户信息对象，如果缓存中不存在则返回null
     */
    public UserInfo getUserInfo(String userId) {
        try {
            String cacheKey = USER_INFO_CACHE_PREFIX + userId;
            System.out.println("尝试从缓存获取用户信息，键: " + cacheKey);

            Map<Object, Object> cachedData = redisService.hGetAll(cacheKey);
            System.out.println("缓存数据条目数: " + (cachedData != null ? cachedData.size() : 0));

            if (cachedData == null || cachedData.isEmpty()) {
                System.out.println("缓存中没有找到用户信息: " + userId);
                return null;
            }

            // 检查缓存是否完整（至少包含基本信息）
            if (!cachedData.containsKey("userId") || !cachedData.containsKey("username")) {
                System.out.println("缓存数据不完整，删除无效缓存: " + userId);
                redisService.delete(cacheKey);
                return null;
            }

            UserInfo userInfo = new UserInfo();
            userInfo.setUserId((String) cachedData.get("userId"));
            userInfo.setUsername((String) cachedData.get("username"));
            userInfo.setJob((String) cachedData.get("job"));
            userInfo.setDepartmentId((String) cachedData.get("departmentId"));
            userInfo.setCorpId((String) cachedData.get("corpId"));
            userInfo.setTemplatespath((String) cachedData.get("templatespath"));
            userInfo.setImagepath((String) cachedData.get("imagepath"));
            userInfo.setSavepath((String) cachedData.get("savepath"));

            // 检查缓存过期时间
            Long expireTime = redisService.getExpire(cacheKey);
            System.out.println("缓存过期时间剩余: " + expireTime + " 秒");

            System.out.println("成功从Redis缓存中获取到用户信息: " + userId + " (" + userInfo.getUsername() + ")");
            return userInfo;

        } catch (Exception e) {
            System.err.println("获取用户信息缓存失败: " + userId + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 缓存用户信息
     * @param userInfo 用户信息对象
     */
    public void cacheUserInfo(UserInfo userInfo) {
        try {
            String cacheKey = USER_INFO_CACHE_PREFIX + userInfo.getUserId();
            System.out.println("开始缓存用户信息到Redis，键: " + cacheKey);

            // 使用Hash存储用户信息
            redisService.hSet(cacheKey, "userId", userInfo.getUserId());
            redisService.hSet(cacheKey, "username", userInfo.getUsername());
            redisService.hSet(cacheKey, "job", userInfo.getJob());
            redisService.hSet(cacheKey, "departmentId", userInfo.getDepartmentId());
            redisService.hSet(cacheKey, "corpId", userInfo.getCorpId());
            redisService.hSet(cacheKey, "templatespath", userInfo.getTemplatespath());
            redisService.hSet(cacheKey, "imagepath", userInfo.getImagepath());
            redisService.hSet(cacheKey, "savepath", userInfo.getSavepath());

            // 设置过期时间为7天（更长的缓存时间）
            Boolean expireResult = redisService.expire(cacheKey, 7, TimeUnit.DAYS);

            // 验证缓存是否设置成功
            Map<Object, Object> verifyData = redisService.hGetAll(cacheKey);
            if (verifyData != null && !verifyData.isEmpty() && verifyData.containsKey("userId")) {
                Long expireTime = redisService.getExpire(cacheKey);
                System.out.println("用户信息已成功缓存到Redis: " + userInfo.getUserId() +
                                 " (" + userInfo.getUsername() + "), 过期时间: " + expireTime + " 秒");
            } else {
                System.err.println("缓存设置失败，数据验证未通过: " + userInfo.getUserId());
            }

        } catch (Exception e) {
            System.err.println("缓存用户信息失败: " + userInfo.getUserId() + ", 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 删除缓存的用户信息
     * @param userId 用户ID
     */
    public void deleteUserInfo(String userId) {
        String cacheKey = USER_INFO_CACHE_PREFIX + userId;
        redisService.delete(cacheKey);
        System.out.println("用户信息缓存已删除: " + userId);
    }

    /**
     * 检查用户信息是否已缓存
     * @param userId 用户ID
     * @return 是否已缓存
     */
    public boolean isUserInfoCached(String userId) {
        String cacheKey = USER_INFO_CACHE_PREFIX + userId;
        return redisService.hasKey(cacheKey);
    }

    /**
     * 获取缓存统计信息
     * @return 缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            // 获取所有用户信息缓存键
            java.util.Set<String> userKeys = redisService.keys(USER_INFO_CACHE_PREFIX + "*");
            stats.put("cachedUserCount", userKeys != null ? userKeys.size() : 0);
            stats.put("cacheHealthy", redisService.hasKey("health:check"));

            // 检查access_token缓存
            boolean hasAccessToken = redisService.hasKey("dingtalk:access_token");
            stats.put("hasAccessToken", hasAccessToken);

            if (hasAccessToken) {
                Long tokenExpire = redisService.getExpire("dingtalk:access_token");
                stats.put("accessTokenExpireSeconds", tokenExpire);
            }

        } catch (Exception e) {
            stats.put("error", e.getMessage());
        }
        return stats;
    }
}
