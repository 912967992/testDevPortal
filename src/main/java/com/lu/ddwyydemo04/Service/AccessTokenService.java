package com.lu.ddwyydemo04.Service;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.taobao.api.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("AccessTokenService")
public class AccessTokenService {
    @Value("${dingtalk.appKey}")
    private String APP_KEY;

    @Value("${dingtalk.appSecret}")
    private String APP_SECRET;

    private static final String GET_TOKEN_URL = "https://oapi.dingtalk.com/gettoken";

    public String getAccessToken() throws ApiException {
        DefaultDingTalkClient client = new DefaultDingTalkClient(GET_TOKEN_URL);
        OapiGettokenRequest request = new OapiGettokenRequest();
        request.setAppkey(APP_KEY);
        request.setAppsecret(APP_SECRET);
        request.setHttpMethod("GET");

        OapiGettokenResponse response = client.execute(request);
        if (response.getErrcode() == 0) {
            return response.getAccessToken();
        } else {
            throw new ApiException("Unable to get access_token, errcode: " + response.getErrcode() + ", errmsg: " + response.getErrmsg());
        }
    }
}
