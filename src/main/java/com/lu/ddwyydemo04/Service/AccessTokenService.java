package com.lu.ddwyydemo04.Service;

import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiV2DepartmentGetRequest;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiV2DepartmentGetResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.taobao.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service("AccessTokenService")
public class AccessTokenService {
    @Value("${dingtalk.appKey}")
    private String APP_KEY;

    @Value("${dingtalk.appSecret}")
    private String APP_SECRET;

    @Autowired
    private RedisService redisService;

    private static final Logger logger = LoggerFactory.getLogger(AccessTokenService.class);

    private static final String GET_TOKEN_URL = "https://oapi.dingtalk.com/gettoken";
    private static final String ACCESS_TOKEN_CACHE_KEY = "dingtalk:access_token";

    public String getAccessToken() throws ApiException {
        // 首先尝试从Redis缓存中获取access_token
        String cachedToken = (String) redisService.get(ACCESS_TOKEN_CACHE_KEY);
        if (cachedToken != null) {
            System.out.println("从Redis缓存中获取到access_token");
            return cachedToken;
        }

        // 缓存中没有，从钉钉API获取
        System.out.println("从钉钉API获取access_token");
        DefaultDingTalkClient client = new DefaultDingTalkClient(GET_TOKEN_URL);
        OapiGettokenRequest request = new OapiGettokenRequest();
        request.setAppkey(APP_KEY);
        request.setAppsecret(APP_SECRET);
        request.setHttpMethod("GET");

        OapiGettokenResponse response = client.execute(request);
        if (response.getErrcode() == 0) {
            String accessToken = response.getAccessToken();
            // 将access_token缓存到Redis中，设置过期时间为7000秒（提前200秒过期，避免临界点问题）
            redisService.set(ACCESS_TOKEN_CACHE_KEY, accessToken, 7000, TimeUnit.SECONDS);
            return accessToken;
        } else {
            throw new ApiException("Unable to get access_token, errcode: " + response.getErrcode() + ", errmsg: " + response.getErrmsg());
        }
    }

    /**
     * 查询指定部门的子部门（分组）并打印详细信息
     * @param deptId 部门ID
     */
    public void queryAndPrintDeptUsers(Long deptId) {
        try {
            logger.info("========== 开始查询部门 ID: " + deptId + " 的子部门（分组）信息 ==========");

            // 获取部门名称
            String deptName = getDepartmentNameByDeptId(deptId);
            logger.info("父部门名称: " + deptName);
            logger.info("父部门ID: " + deptId);
            logger.info("----------------------------------------");

            // 查询该部门的子部门列表
            List<OapiV2DepartmentListsubResponse.DeptBaseResponse> subDepts = getDeptListByDeptId(deptId);

            logger.info("----------------------------------------");
            logger.info("共找到 " + subDepts.size() + " 个子部门（分组）");
            logger.info("========================================");

            // 打印子部门详细信息
            if (subDepts.isEmpty()) {
                logger.info("该部门下没有子部门（分组）");
            } else {
                int index = 1;
                for (OapiV2DepartmentListsubResponse.DeptBaseResponse subDept : subDepts) {
                    logger.info("【子部门（分组） " + index + "】");
                    logger.info("  部门ID: " + subDept.getDeptId());
                    logger.info("  部门名称: " + subDept.getName());
                    logger.info("  父部门ID: " + subDept.getParentId());
                    logger.info("  是否自动添加用户: " + subDept.getAutoAddUser());
                    logger.info("  是否创建部门群: " + subDept.getCreateDeptGroup());
                    logger.info("  ---");
                    index++;
                }
            }

            logger.info("========== 查询完成 ==========");

        } catch (ApiException e) {
            logger.error("查询部门子部门时发生错误: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("查询部门子部门时发生未知错误: " + e.getMessage(), e);
        }
    }



    // 新增获取部门名称的方法
    public String getDepartmentNameByDeptId(Long deptId) throws ApiException {
        // 使用部门 ID 获取部门的详细信息
        DingTalkClient infoClient = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/department/get");
        OapiV2DepartmentGetRequest infoReq = new OapiV2DepartmentGetRequest();
        infoReq.setDeptId(deptId);
        OapiV2DepartmentGetResponse infoRsp = infoClient.execute(infoReq, getAccessToken());
        return extractParamOfResult(infoRsp.getBody(), "name");
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


    // 根据dept_id去部门列表信息：部门ID: 971739387
    //部门名称: 蓝牙线材家居组
    //父部门ID: 523528658
    //是否自动添加用户: false
    //是否创建部门群: false
    public List<OapiV2DepartmentListsubResponse.DeptBaseResponse> getDeptListByDeptId(Long deptId) throws ApiException {
        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/department/listsub");
        OapiV2DepartmentListsubRequest req = new OapiV2DepartmentListsubRequest();
        req.setDeptId(deptId);
        req.setLanguage("zh_CN");

        OapiV2DepartmentListsubResponse rsp = client.execute(req, getAccessToken());

        if (rsp.getErrcode() != 0) {
            throw new ApiException("Error: " + rsp.getErrmsg());
        }

        // 直接返回 result 列表
        return rsp.getResult();
    }

    /**
     * 定时任务：查询部门 63652303L 的子部门（分组）信息并打印
     * 可以通过取消注释 @Scheduled 注解来启用定时执行
     * 或者直接调用此方法手动执行
     *
     * 使用方式：
     * 1. 定时执行：取消下面任意一个 @Scheduled 注解的注释
     * 2. 手动执行：在 Controller 或其他地方调用此方法
     */
//     @Scheduled(cron = "0 47 10 * * ?") // 每天早上10点31分执行
    // @Scheduled(cron = "0 0 */1 * * ?") // 每1小时执行一次
    // @Scheduled(fixedRate = 3600000) // 每1小时执行一次（毫秒）
//    @Scheduled(fixedRate = 10000) // 每1分钟执行一次（测试用）
    public void scheduledQueryDept63652303Users() {
        logger.info("========== 定时任务开始：查询部门 63652303L 的子部门（分组）信息 ==========");
//        Long deptId = 63652303L; //品质工程部
        Long deptId = 523645469L; //可靠性实验室
        queryAndPrintDeptUsers(deptId);
        logger.info("========== 定时任务结束 ==========");
    }





}
