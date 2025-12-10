package com.lu.ddwyydemo04.tasks;

import com.lu.ddwyydemo04.Service.AccessTokenService;
import com.lu.ddwyydemo04.dao.UserDao;
import com.lu.ddwyydemo04.pojo.User;
import com.taobao.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 钉钉通知测试定时任务
 * 用于测试发送钉钉通知功能
 * 
 * 使用说明：
 * 1. 修改 TEST_USERNAME 为你要接收通知的用户名（数据库 user 表中的 username 字段）
 * 2. 系统会自动根据用户名查询数据库获取对应的 userId
 * 3. 修改 @Scheduled 注解中的时间表达式来设置执行频率
 * 4. 可以通过注释/取消注释 @Scheduled 来启用/禁用定时任务
 */
@Component
public class DingTalkNotificationTestTask {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotificationTestTask.class);

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private UserDao userDao;

    /**
     * 测试用的用户名，请修改为你要接收通知的用户名
     * 系统会根据用户名从数据库 user 表中查询对应的 userId
     * 可以通过查看数据库 user 表的 username 字段来获取用户名
     */
    private static final String TEST_USERNAME = "卢健";

    /**
     * 定时发送测试通知
     * 
     * Cron表达式说明：
     * - "0 0/5 * * * ?" 表示每5分钟执行一次
     * - "0 0/1 * * * ?" 表示每1分钟执行一次
     * - "0 0 9 * * ?" 表示每天上午9点执行
     * - "0 0 9,12,18 * * ?" 表示每天9点、12点、18点执行
     * 
     * 测试时建议使用较短的间隔，测试完成后可以注释掉或改为较长的间隔
     */
//    @Scheduled(cron = "0 0/1 * * * ?")  // 每1分钟执行一次（测试用）
    // @Scheduled(cron = "0 0 9 * * ?")  // 每天上午9点执行（正式使用）
    public void sendTestNotification() {
        try {
            // 检查是否已设置用户名
            if (TEST_USERNAME == null || TEST_USERNAME.equals("请修改为你的用户名") || TEST_USERNAME.trim().isEmpty()) {
                log.warn("⚠️ 请先设置 TEST_USERNAME 为你的用户名");
                return;
            }

            log.info("========== 开始发送钉钉测试通知 ==========");
            log.info("查询用户名: {}", TEST_USERNAME);
            
            // 根据用户名查询数据库获取用户信息
            User user = userDao.selectByUsername(TEST_USERNAME);
            if (user == null || user.getUserId() == null || user.getUserId().trim().isEmpty()) {
                log.error("❌ 未找到用户: {}，请检查数据库中是否存在该用户名", TEST_USERNAME);
                return;
            }
            
            String userId = user.getUserId();
            log.info("✅ 找到用户: {} (userId: {})", TEST_USERNAME, userId);
            
            // 获取当前时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());
            
            // 构建通知内容
            String title = "测试通知";
            String content = String.format(
                "这是一条测试通知\n\n" +
                "发送时间：%s\n" +
                "任务类型：定时任务测试\n" +
                "接收用户：%s\n" +
                "状态：✅ 通知发送成功",
                currentTime, TEST_USERNAME
            );
            
            // 发送通知
            boolean success = accessTokenService.sendDingTalkNotificationToUser(
                userId, 
                title, 
                content
            );
            
            if (success) {
                log.info("✅ 钉钉测试通知发送成功，接收用户: {} (userId: {})", TEST_USERNAME, userId);
            } else {
                log.error("❌ 钉钉测试通知发送失败，接收用户: {} (userId: {})", TEST_USERNAME, userId);
            }
            
            log.info("========== 测试通知发送完成 ==========");
            
        } catch (ApiException e) {
            log.error("❌ 发送钉钉测试通知异常: errcode={}, errmsg={}", 
                e.getErrCode(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ 定时任务执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送Markdown格式的测试通知
     * 可以通过注释/取消注释 @Scheduled 来启用/禁用
     */
//     @Scheduled(cron = "0 0/1 * * * ?")  // 每10分钟执行一次
    public void sendMarkdownTestNotification() {
        try {
            // 检查是否已设置用户名
            if (TEST_USERNAME == null || TEST_USERNAME.equals("请修改为你的用户名") || TEST_USERNAME.trim().isEmpty()) {
                log.warn("⚠️ 请先设置 TEST_USERNAME 为你的用户名");
                return;
            }

            log.info("========== 开始发送钉钉Markdown测试通知 ==========");
            log.info("查询用户名: {}", TEST_USERNAME);
            
            // 根据用户名查询数据库获取用户信息
            User user = userDao.selectByUsername(TEST_USERNAME);
            if (user == null || user.getUserId() == null || user.getUserId().trim().isEmpty()) {
                log.error("❌ 未找到用户: {}，请检查数据库中是否存在该用户名", TEST_USERNAME);
                return;
            }
            
            String userId = user.getUserId();
            log.info("✅ 找到用户: {} (userId: {})", TEST_USERNAME, userId);
            
            // 获取当前时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());
            
            // 构建Markdown格式的通知内容
            String title = "Markdown测试通知";
            String markdownContent = String.format(
                "## 📢 测试通知\n\n" +
                "**发送时间**：%s\n\n" +
                "**接收用户**：%s\n\n" +
                "**任务类型**：Markdown格式测试\n\n" +
                "**状态**：✅ 通知发送成功\n\n" +
                "---\n\n" ,
                currentTime, TEST_USERNAME
            );
            
            // 发送Markdown通知
            boolean success = accessTokenService.sendDingTalkMarkdownNotification(
                userId, 
                title, 
                markdownContent
            );
            
            if (success) {
                log.info("✅ 钉钉Markdown测试通知发送成功，接收用户: {} (userId: {})", TEST_USERNAME, userId);
            } else {
                log.error("❌ 钉钉Markdown测试通知发送失败，接收用户: {} (userId: {})", TEST_USERNAME, userId);
            }
            
            log.info("========== Markdown测试通知发送完成 ==========");
            
        } catch (ApiException e) {
            log.error("❌ 发送钉钉Markdown测试通知异常: errcode={}, errmsg={}", 
                e.getErrCode(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Markdown定时任务执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发测试方法（可选）
     * 可以在Controller或其他地方调用这个方法进行测试
     * 
     * @param username 用户名，如果为null则使用 TEST_USERNAME
     * @param message 通知消息内容，如果为null则使用默认消息
     */
    public void manualTest(String username, String message) {
        try {
            log.info("========== 手动触发钉钉通知测试 ==========");
            
            // 使用传入的用户名，如果为null则使用默认的 TEST_USERNAME
            String targetUsername = username != null ? username : TEST_USERNAME;
            
            if (targetUsername == null || targetUsername.equals("请修改为你的用户名") || targetUsername.trim().isEmpty()) {
                log.error("❌ 请提供有效的用户名");
                return;
            }
            
            log.info("查询用户名: {}", targetUsername);
            
            // 根据用户名查询数据库获取用户信息
            User user = userDao.selectByUsername(targetUsername);
            if (user == null || user.getUserId() == null || user.getUserId().trim().isEmpty()) {
                log.error("❌ 未找到用户: {}，请检查数据库中是否存在该用户名", targetUsername);
                return;
            }
            
            String userId = user.getUserId();
            log.info("✅ 找到用户: {} (userId: {})", targetUsername, userId);
            
            String title = "手动测试通知";
            String content = message != null ? message : "这是一条手动触发的测试通知";
            
            boolean success = accessTokenService.sendDingTalkNotificationToUser(
                userId, 
                title, 
                content
            );
            
            if (success) {
                log.info("✅ 手动测试通知发送成功，接收用户: {} (userId: {})", targetUsername, userId);
            } else {
                log.error("❌ 手动测试通知发送失败，接收用户: {} (userId: {})", targetUsername, userId);
            }
            
        } catch (Exception e) {
            log.error("❌ 手动测试异常: {}", e.getMessage(), e);
        }
    }
}

