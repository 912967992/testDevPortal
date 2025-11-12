package com.lu.ddwyydemo04.Service;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.taobao.api.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service("AccessTokenService")
public class AccessTokenService {
    @Value("${dingtalk.appKey}")
    private String APP_KEY;

    @Value("${dingtalk.appSecret}")
    private String APP_SECRET;

    @Autowired
    private RedisService redisService;

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
}
