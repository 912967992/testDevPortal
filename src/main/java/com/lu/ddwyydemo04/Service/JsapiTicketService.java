package com.lu.ddwyydemo04.Service;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiGetJsapiTicketRequest;
import com.dingtalk.api.response.OapiGetJsapiTicketResponse;
import com.taobao.api.ApiException;
import org.springframework.stereotype.Service;

@Service
public class JsapiTicketService {

    private static final String JSAPI_TICKET_URL = "https://oapi.dingtalk.com/get_jsapi_ticket";

    private final AccessTokenService accessTokenService;

    public JsapiTicketService(AccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    public String getJsapiTicket() throws ApiException {
        String accessToken = accessTokenService.getAccessToken();
        DefaultDingTalkClient client = new DefaultDingTalkClient(JSAPI_TICKET_URL);
        OapiGetJsapiTicketRequest request = new OapiGetJsapiTicketRequest();
        request.setTopHttpMethod("GET");

        OapiGetJsapiTicketResponse response = client.execute(request, accessToken);
        if (response.getErrcode() == 0) {
            return response.getTicket();
        } else {
            throw new ApiException("Unable to get jsapi_ticket, errcode: " + response.getErrcode() + ", errmsg: " + response.getErrmsg());
        }
    }

}
