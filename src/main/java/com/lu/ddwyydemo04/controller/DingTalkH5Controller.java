package com.lu.ddwyydemo04.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGetJsapiTicketRequest;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiUserGetuserinfoRequest;
import com.dingtalk.api.request.OapiV2UserGetRequest;
import com.dingtalk.api.request.OapiV2DepartmentListparentbyuserRequest;
import com.dingtalk.api.response.OapiGetJsapiTicketResponse;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiUserGetuserinfoResponse;
import com.dingtalk.api.response.OapiV2UserGetResponse;
import com.dingtalk.api.response.OapiV2DepartmentListparentbyuserResponse;
import com.lu.ddwyydemo04.Service.AccessTokenService;
import com.lu.ddwyydemo04.Service.DingTalkUserCacheService;
import com.lu.ddwyydemo04.Service.JsapiTicketService;
import com.taobao.api.ApiException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Controller
public class DingTalkH5Controller {

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private DingTalkUserCacheService userCacheService;

    @Value("${dingtalk.agentid}")
    private String agentid;

    @Value("${dingtalk.corpid}")
    private String corpid;

    @Value("${file.storage.templatespath}")
    private String templatespath;

    @Value("${file.storage.savepath}")
    private String savepath;

    @Value("${file.storage.imagepath}")
    private String imagepath;
    private static final String GET_USER_INFO_URL = "https://oapi.dingtalk.com/user/getuserinfo";

    /**
     * 钉钉免登页面
     * 当用户未登录时，会重定向到此页面进行钉钉免登
     */
//    @GetMapping("/dingtalk/login")
//    public String dingTalkLogin() {
//        return "dingtalk/login";
//    }

    // 获取access_token的方法


    /**
     * 恢复用户 Session（用于页面跳转时快速恢复登录状态）
     * 通过 username 从 Redis 缓存中获取用户信息并恢复到 session
     */
    @PostMapping("/api/restoreSession")
    @ResponseBody
    public Map<String, Object> restoreSession(@RequestBody Map<String, String> requestMap, HttpServletRequest httpRequest) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String username = requestMap.get("username");
            String job = requestMap.get("job");
            
            if (username == null || username.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "缺少用户名参数");
                return result;
            }
            
            System.out.println("🔄 尝试恢复 Session: username=" + username + ", job=" + job);
            
            // 从 Redis 缓存中查找用户信息（通过遍历所有缓存的用户）
            // 注意：这里需要 DingTalkUserCacheService 提供一个通过 username 查询的方法
            DingTalkUserCacheService.UserInfo userInfo = userCacheService.getUserInfoByUsername(username);
            
            if (userInfo != null) {
                // 找到了用户信息，恢复到 session
                HttpSession session = httpRequest.getSession(true);
                session.setAttribute("userId", userInfo.getUserId());
                session.setAttribute("username", userInfo.getUsername());
                session.setAttribute("job", userInfo.getJob());
                session.setAttribute("departmentId", userInfo.getDepartmentId());
                session.setAttribute("corp_id", userInfo.getCorpId());
                
                System.out.println("✅ Session 恢复成功: " + username + " (ID: " + userInfo.getUserId() + ")");
                
                result.put("success", true);
                result.put("message", "Session 恢复成功");
                result.put("username", userInfo.getUsername());
                result.put("job", userInfo.getJob());
            } else {
                // Redis 缓存中没有找到用户信息
                System.out.println("⚠️ Redis 缓存中未找到用户信息: " + username);
                result.put("success", false);
                result.put("message", "缓存中未找到用户信息，请重新登录");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Session 恢复失败: " + e.getMessage());
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "Session 恢复失败: " + e.getMessage());
        }
        
        return result;
    }

    @PostMapping("/api/getUserInfo")
    @ResponseBody
    public Map<String, Object> getUserInfo(@RequestBody Map<String, String> requestMap, HttpServletRequest httpRequest) throws ApiException {
        //获取免登授权码authCode
        String authCode = requestMap.get("authCode");

        // 首先使用authCode获取userid（这个API调用是必需的，不能缓存）
        String accessToken = accessTokenService.getAccessToken(); // 调用方法获取accessToken
        DingTalkClient client = new DefaultDingTalkClient(GET_USER_INFO_URL);
        OapiUserGetuserinfoRequest request = new OapiUserGetuserinfoRequest();
        request.setCode(authCode);
        request.setHttpMethod("GET");

        OapiUserGetuserinfoResponse response = client.execute(request, accessToken);
        Map<String, Object> result = new HashMap<>();
        if (response.getErrcode() == 0) {
            // 正常情况下返回用户userid
            String userid = response.getUserid();

            // 检查Redis缓存中是否已有该用户的信息
            System.out.println("检查用户 " + userid + " 的缓存信息...");
            DingTalkUserCacheService.UserInfo cachedUserInfo = userCacheService.getUserInfo(userid);
            if (cachedUserInfo != null) {
                // 从缓存中获取用户信息，完全避免调用钉钉API
                System.out.println("🎉 从缓存中获取用户信息成功，避免调用钉钉API: " + userid + " (" + cachedUserInfo.getUsername() + ")");

                // 将用户信息保存到session中
                HttpSession session = httpRequest.getSession(true);
                session.setAttribute("userId", cachedUserInfo.getUserId());
                session.setAttribute("username", cachedUserInfo.getUsername());
                session.setAttribute("job", cachedUserInfo.getJob());
                session.setAttribute("departmentId", cachedUserInfo.getDepartmentId());
                session.setAttribute("corp_id", cachedUserInfo.getCorpId());

                // 返回缓存的用户信息
                result.putAll(cachedUserInfo.toMap());
                System.out.println("✅ 用户登录成功（使用缓存），返回用户信息: " + cachedUserInfo.getUsername());
                return result;
            }

            // 缓存中没有，从钉钉API获取详细信息（首次登录）
            System.out.println("📡 缓存中没有用户信息，从钉钉API获取: " + userid);

            // 使用userId获取用户的详细信息
            DingTalkClient infoClient = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/user/get");
            OapiV2UserGetRequest infoReq = new OapiV2UserGetRequest();
            infoReq.setUserid(userid);
            infoReq.setLanguage("zh_CN");
            OapiV2UserGetResponse infoRsp = infoClient.execute(infoReq, accessToken);
            String username = extractParamOfResult(infoRsp.getBody(),"name");
            System.out.println("name:"+username);

            //提取部门id,"523459714"是电子测试组的编号
            String departmentId = extractDepartmentIds(infoRsp.getBody());
            System.out.println("departmentIds:"+departmentId);

            // 获取用户所属的父部门列表
            DingTalkClient clientDept = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/department/listparentbyuser");
            OapiV2DepartmentListparentbyuserRequest reqDept = new OapiV2DepartmentListparentbyuserRequest();
            reqDept.setUserid(userid);
            OapiV2DepartmentListparentbyuserResponse rspDept = clientDept.execute(reqDept, accessToken);
            String responseDeptBody = rspDept.getBody();

            // 调用checkParentDepartment方法判断job
            String job = checkParentDepartment(responseDeptBody, username);
            
            // 特殊用户名覆盖 job 为 "projectLeader"
            if ("陈少侠".equals(username) || "郭丽纯".equals(username) ||
                    "占海英".equals(username) || "刘定荣".equals(username) || "姚遥".equals(username)) {
                job = "projectLeader";
            }
            
            System.out.println("job:"+job);

            // 创建用户信息对象并缓存到Redis（7天有效期）
            DingTalkUserCacheService.UserInfo userInfo = new DingTalkUserCacheService.UserInfo(
                userid, username, job, departmentId, corpid, templatespath, imagepath, savepath
            );
            System.out.println("🔄 首次登录，准备缓存用户信息: " + username + " (ID: " + userid + ")");
            userCacheService.cacheUserInfo(userInfo);

            // 将用户信息保存到session中
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("userId", userid);
            session.setAttribute("username", username);
            session.setAttribute("job", job);
            session.setAttribute("departmentId", departmentId);
            session.setAttribute("corp_id", corpid);

            //将想要返回的结果保存起来
            result.put("userId", userid);
            result.put("username", username);
            result.put("job", job);
            result.put("departmentId", departmentId);
            result.put("corp_id",corpid);
            result.put("templatespath",templatespath);
            result.put("imagepath",imagepath);
            result.put("savepath",savepath);




        } else {
            // 发生错误时返回错误信息
            result.put("errorCode", response.getErrcode());
            result.put("errorMessage", response.getErrmsg());
        }

        //目前只返回userid: ,name:卢健，job:测试专员，departmentIds:[523459714]，后续看还需要的话可以从这里获取然后前端保存到sessionStorage
        return result;
    }

    //提取result里的参数param，想提取什么参数就写param，但是此处只提取不带多元素的,例如title是职位，name是姓名
    public static String extractParamOfResult(String userInfoJson,String param) {
        // 使用fastjson将JSON字符串解析为JSONObject对象
        JSONObject userInfo = JSONObject.parseObject(userInfoJson);

        // 获取result字段中的值
        JSONObject resultObj = userInfo.getJSONObject("result");
        if (resultObj != null) {
            // 获取name字段的值并返回
            String value = resultObj.getString(param);
            return value;
        } else {
            // 如果result字段为空，则返回空字符串或者其他默认值
            return "";
        }
    }



    //提取部门id
    public static String extractDepartmentIds(String userInfoJson) {
        // 使用fastjson将JSON字符串解析为JSONObject对象
        JSONObject userInfo = JSONObject.parseObject(userInfoJson);

        // 获取result字段中的值
        JSONObject resultObj = userInfo.getJSONObject("result");
        if (resultObj != null) {
            // 获取部门id列表字段的值并返回
            JSONArray deptIdList = resultObj.getJSONArray("dept_id_list");
            return deptIdList.toString();
        } else {
            // 如果result字段为空，则返回空字符串或者其他默认值
            return "";
        }
    }


    // 注入JsapiTicketService
    @Autowired
    private JsapiTicketService jsapiTicketService;

    @GetMapping("/getJsapiConfig")
    @ResponseBody
    public Map<String, Object> getJsapiConfig(@RequestParam("url") String url) throws Exception {
        // 这里使用JsapiTicketService来获取jsapi_ticket，并生成签名等信息
        return generateJsapiConfig(url);
    }

    // 生成JSAPI配置信息的方法，使用钉钉的签名逻辑
    private Map<String, Object> generateJsapiConfig(String url) throws Exception {
        // 获取jsapi_ticket
        String jsapiTicket = jsapiTicketService.getJsapiTicket();
        System.out.println("jsapiTicket:"+jsapiTicket);

        // 计算时间戳和随机字符串
        String timeStamp = Long.toString(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replaceAll("-", "");

        // 生成签名
        String signature = sign(jsapiTicket, nonceStr, Long.parseLong(timeStamp), url);

        // 返回配置信息
        Map<String, Object> config = new HashMap<>();
        config.put("agentId", agentid);
        config.put("corpId", corpid);
        config.put("timeStamp", timeStamp);
        config.put("nonceStr", nonceStr);
        config.put("signature", signature);
        config.put("jsApiList", Arrays.asList("device.base.getUUID","biz.navigation.close","biz.contact.choose","biz.cspace.chooseSpaceDir","biz.ding.create","biz.cspace.saveFile","runtime.permission.requestAuthCode","biz.util.downloadFile")); // 只需要使用选择联系人的JSAPI

        System.out.println("config:" + config);

        return config;
    }

    // 钉钉文档中的签名方法
    public static String sign(String jsticket, String nonceStr, long timeStamp, String url) throws Exception {
        String plain = "jsapi_ticket=" + jsticket + "&noncestr=" + nonceStr + "&timestamp=" + timeStamp
                + "&url=" + decodeUrl(url);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.reset();
            sha1.update(plain.getBytes("UTF-8"));
            return byteToHex(sha1.digest());
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    // 字节数组转化成十六进制字符串
    private static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    // 因为iOS端上传递的url是encode过的，Android是原始的url。开发者使用的也是原始url,
    // 所以需要把参数进行一般urlDecode
    private static String decodeUrl(String encodedUrl) throws Exception {
        // 首先对传入的URL进行解码
        String decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8");

        // 然后使用解码后的URL创建URL对象
        URL url = new URL(decodedUrl);

        // 构建不包含查询参数的URL
        StringBuilder urlBuffer = new StringBuilder();
        urlBuffer.append(url.getProtocol());
        urlBuffer.append(":");
        if (url.getAuthority() != null && url.getAuthority().length() > 0) {
            urlBuffer.append("//");
            urlBuffer.append(url.getAuthority());
        }
        if (url.getPath() != null) {
            urlBuffer.append(url.getPath());
        }

        // 如果原始URL包含查询参数，将它们添加到构建的URL中
        if (url.getQuery() != null) {
            urlBuffer.append('?');
            urlBuffer.append(url.getQuery());
        }

        return urlBuffer.toString();
    }

    /**
     * 检查用户所属部门并返回对应的job角色
     * 此方法是用来提取部门的主列表里是否包含某个部门id来判定是什么部门的
     */
    public String checkParentDepartment(String jsonResponse, String username) {
        // 如果用户名是黄家灿、荣成彧、李良健，直接设置 job 为 manager 并返回
        if ("黄家灿".equals(username) || "荣成彧".equals(username) || "李良健".equals(username)) {
            return "manager";
        }

        // 20250526新增官旺华、赵梓宇、刘鹏飞
        if ("官旺华".equals(username) || "赵梓宇".equals(username) || "刘鹏飞".equals(username)) {
            return "tester";
        } else {
            if ("卢健".equals(username) || "许梦瑶".equals(username) || "卢绮敏".equals(username) || "蓝明城".equals(username)) {
                return "DQE";
            }
        }

        // 解析 JSON 响应
        JSONObject response = JSON.parseObject(jsonResponse);
        String job = "";
        // 检查 errcode 是否为 0
        if (response.getInteger("errcode") == 0) {
            JSONObject result = response.getJSONObject("result");
            List<JSONObject> parentList = result.getJSONArray("parent_list").toJavaList(JSONObject.class);

            // 遍历所有的父部门列表
            for (JSONObject parent : parentList) {
                List<Long> parentDeptIdList = parent.getJSONArray("parent_dept_id_list").toJavaList(Long.class);

                // 检查部门 ID 并打印相应的信息
                if (parentDeptIdList.contains(62712385L)) {
                    job = "rd";
                }
                if (parentDeptIdList.contains(63652303L)) {
                    if (parentDeptIdList.contains(523459714L)) {
                        job = "tester";  // 设置为 "tester"，并优先返回
                        break;  // 找到后可选择立即返回
                    } else {
                        job = "DQE";
                    }
                }

                // 20241105 新增一个产品经营部的job判定方法：
                // 针对大部门 ID 为 62632390L 的情况
                if (parentDeptIdList.contains(62632390L)) {
                    // 检查是否属于耳机部门的两个指定 ID，并且排除特定用户
                    if ((parentDeptIdList.contains(925840291L) || parentDeptIdList.contains(925828219L))
                            && !username.equals("高玄英") && !username.equals("姜呈祥")) {
                        job = "rd";
                        break;
                    } else {
                        job = "projectLeader";
                    }
                }

            }
        } else {
            System.out.println("请求失败: " + response.getString("errmsg"));
        }
        return job;
    }

}
