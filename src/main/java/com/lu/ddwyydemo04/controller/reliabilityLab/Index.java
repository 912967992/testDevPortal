package com.lu.ddwyydemo04.controller.reliabilityLab;

import com.lu.ddwyydemo04.dao.UserDao;
import com.lu.ddwyydemo04.pojo.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class Index {
    
    private final UserDao userDao;
    
    public Index(UserDao userDao) {
        this.userDao = userDao;
    }
    @GetMapping("/")
    public String root(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 检查session中是否有用户信息
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            // 未登录，重定向到钉钉免登页面（index.html）
            // 添加 from=redirect 参数，防止无限循环
            response.sendRedirect("/index.html?from=redirect");
            return null;
        }
        // 已登录，返回主页
        return "home";
    }

    // 公开主页路由（不需要登录验证，用于从reliabilityIndex页面返回）
    @GetMapping("/home")
    public String publicHome() {
        // 直接返回主页模板，不需要验证session
        return "home";
    }

//    @GetMapping("/reliablityLab/index") // 处理页面跳转请求
//    public String loginUsageRate() {
//        // 返回跳转页面的视图名称
//        return "reliablityLab/reliabilityIndex";
//    }


    // 跳转到物联网温箱监控页面模板（reliabilityIndex.html）
    @GetMapping("/reliabilityIndex")
    public String reliabilityMonitor() {
        return "reliablityLab/reliabilityIndex";
    }

    // 跳转到OEE分析页面（oeeAnalysis.html）
    @GetMapping("/reliabilityLab/oeeAnalysis")
    public String oeeAnalysis() {
        return "reliablityLab/oeeAnalysis";
    }

    // 跳转到数据管理页面（dataManagement.html）
    @GetMapping("/dataManagement")
    public String dataManagement() {
        return "dataManagement";
    }

    /**
     * 获取所有用户名列表（用于切换用户功能）
     */
    @GetMapping("/api/getAllUsernames")
    @ResponseBody
    public Map<String, Object> getAllUsernames() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<User> users = userDao.selectAll();
            List<String> usernames = users.stream()
                    .map(User::getUsername)
                    .filter(username -> username != null && !username.trim().isEmpty())
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            
            result.put("success", true);
            result.put("usernames", usernames);
            result.put("count", usernames.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取用户列表失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    /**
     * 根据用户名获取用户部门名称
     */
    @PostMapping("/api/getUserDepartment")
    @ResponseBody
    public Map<String, Object> getUserDepartment(@RequestBody Map<String, String> requestMap) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String username = requestMap.get("username");
            
            if (username == null || username.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "缺少用户名参数");
                return result;
            }
            
            // 从数据库查询所有用户，找到匹配的用户名
            List<User> users = userDao.selectAll();
            User user = users.stream()
                    .filter(u -> username.equals(u.getUsername()))
                    .findFirst()
                    .orElse(null);
            
            if (user == null) {
                result.put("success", false);
                result.put("message", "用户不存在");
                result.put("departmentName", "");
                return result;
            }
            
            String departmentName = user.getDepartmentName();
            result.put("success", true);
            result.put("departmentName", departmentName != null ? departmentName : "");
            result.put("username", username);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取部门信息失败: " + e.getMessage());
            result.put("departmentName", "");
            e.printStackTrace();
        }
        
        return result;
    }

    /**
     * 检查用户是否有编辑权限
     * 权限判断规则：
     * 1. 用户名在白名单中（卢健、李良健、戴杏华、邓继元）-> 有编辑权限
     * 2. 部门名称为"可靠性实验室" -> 有编辑权限
     * 3. 其他情况 -> 只读模式
     */
    @PostMapping("/api/checkEditPermission")
    @ResponseBody
    public Map<String, Object> checkEditPermission(@RequestBody(required = false) Map<String, String> requestMap, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        // 白名单用户列表
        String[] allowedUsers = {"卢健", "李良健", "戴杏华", "邓继元"};
        
        try {
            String username = null;
            String userId = null;
            User user = null;
            
            // 优先从 session 获取用户信息
            HttpSession session = request.getSession(false);
            if (session != null) {
                userId = (String) session.getAttribute("userId");
                username = (String) session.getAttribute("username");
            }
            
            // 如果 session 中没有用户名，尝试从请求体中获取
            if (username == null && requestMap != null) {
                username = requestMap.get("username");
            }
            
            // 如果还是没有用户名，返回错误
            if (username == null || username.trim().isEmpty()) {
                result.put("hasEditPermission", false);
                result.put("message", "未提供用户名");
                System.out.println("========== 编辑权限检查结果 ==========");
                System.out.println("错误: 未提供用户名");
                System.out.println("=====================================");
                return result;
            }
            
            // 检查用户名是否在白名单中
            boolean isInWhitelist = false;
            for (String allowedUser : allowedUsers) {
                if (allowedUser.equals(username)) {
                    isInWhitelist = true;
                    break;
                }
            }
            
            // 如果不在白名单，从数据库查询用户信息
            if (!isInWhitelist) {
                // 如果有 userId，优先用 userId 查询
                if (userId != null) {
                    user = userDao.selectByUserId(userId);
                }
                
                // 如果通过 userId 没查到，或者没有 userId，用 username 查询
                if (user == null) {
                    user = userDao.selectByUsername(username);
                }
            }
            
            // 判断权限
            boolean hasEditPermission = false;
            String departmentName = null;
            
            if (isInWhitelist) {
                // 白名单用户直接授予权限
                hasEditPermission = true;
                System.out.println("========== 编辑权限检查结果 ==========");
                System.out.println("用户名: " + username);
                System.out.println("权限来源: 白名单用户");
                System.out.println("最终权限: 有编辑权限");
                System.out.println("=====================================");
            } else if (user != null) {
                // 检查部门名称是否为"可靠性实验室"
                departmentName = user.getDepartmentName();
                hasEditPermission = "可靠性实验室".equals(departmentName);
                
                // 打印判断结果
                System.out.println("========== 编辑权限检查结果 ==========");
                System.out.println("用户ID: " + (userId != null ? userId : "未提供"));
                System.out.println("用户名: " + username);
                System.out.println("部门名称: " + (departmentName != null ? departmentName : "null"));
                System.out.println("目标部门: 可靠性实验室");
                System.out.println("部门匹配: " + ("可靠性实验室".equals(departmentName) ? "是" : "否"));
                System.out.println("最终权限: " + (hasEditPermission ? "有编辑权限" : "只读模式"));
                System.out.println("=====================================");
            } else {
                // 用户不存在
                System.out.println("========== 编辑权限检查结果 ==========");
                System.out.println("用户名: " + username);
                System.out.println("错误: 用户不存在于数据库中");
                System.out.println("最终权限: 只读模式");
                System.out.println("=====================================");
            }
            
            result.put("hasEditPermission", hasEditPermission);
            result.put("username", username);
            if (departmentName != null) {
                result.put("departmentName", departmentName);
            }
            result.put("message", hasEditPermission ? "具有编辑权限" : "只读模式");
            
        } catch (Exception e) {
            result.put("hasEditPermission", false);
            result.put("message", "检查权限时发生错误: " + e.getMessage());
            System.out.println("========== 编辑权限检查异常 ==========");
            System.out.println("错误信息: " + e.getMessage());
            e.printStackTrace();
            System.out.println("=====================================");
        }
        
        return result;
    }

}
