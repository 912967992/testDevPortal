package com.lu.ddwyydemo04.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGetJsapiTicketRequest;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiUserGetuserinfoRequest;
import com.dingtalk.api.request.OapiV2UserGetRequest;
import com.dingtalk.api.response.OapiGetJsapiTicketResponse;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiUserGetuserinfoResponse;
import com.dingtalk.api.response.OapiV2UserGetResponse;
import com.lu.ddwyydemo04.Service.AccessTokenService;
import com.lu.ddwyydemo04.Service.JsapiTicketService;
import com.taobao.api.ApiException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Controller
public class DingTalkH5Controller {

    @Autowired
    private AccessTokenService accessTokenService;

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

    // 获取access_token的方法


    @PostMapping("/api/getUserInfo")
    @ResponseBody
    public Map<String, Object> getUserInfo(@RequestBody Map<String, String> requestMap) throws ApiException {
        //获取免登授权码authCode
        String authCode = requestMap.get("authCode");
        String accessToken = accessTokenService.getAccessToken(); // 调用方法获取accessToken

        DingTalkClient client = new DefaultDingTalkClient(GET_USER_INFO_URL);
        OapiUserGetuserinfoRequest request = new OapiUserGetuserinfoRequest();
        request.setCode(authCode);
        request.setHttpMethod("GET");

        OapiUserGetuserinfoResponse response = client.execute(request, accessToken);
        Map<String, Object> result = new HashMap<>();
        if (response.getErrcode() == 0) {
            // 正常情况下返回用户userid   ,deviceid是设备的唯一标识符，用不太到
            String userid = response.getUserid();

            // 使用userId获取用户的详细信息
            DingTalkClient infoClient = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/user/get");
            OapiV2UserGetRequest infoReq = new OapiV2UserGetRequest();
            infoReq.setUserid(userid);
            infoReq.setLanguage("zh_CN");
            OapiV2UserGetResponse infoRsp = infoClient.execute(infoReq, accessToken);
            String username = extractParamOfResult(infoRsp.getBody(),"name");
            System.out.println("name:"+username);

            //提取职位,"测试专员"
            String job = extractParamOfResult(infoRsp.getBody(),"title");
            System.out.println("job:"+job);

            //提取部门id,"523459714"是电子测试组的编号
            String departmentId = extractDepartmentIds(infoRsp.getBody());
            System.out.println("departmentIds:"+departmentId);

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

}
