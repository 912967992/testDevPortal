package com.lu.ddwyydemo04.tasks;

import com.lu.ddwyydemo04.Service.JsapiTicketService;
import com.taobao.api.ApiException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JsapiTicketRefreshTask {


    private static final Logger log = LoggerFactory.getLogger(JsapiTicketRefreshTask.class);

    private final JsapiTicketService jsapiTicketService;

    public JsapiTicketRefreshTask(JsapiTicketService jsapiTicketService) {
        this.jsapiTicketService = jsapiTicketService;
    }

    @Scheduled(fixedRate = 110*60*1000) // 每1小时50分钟刷新一次,分钟*60s*1000ms
    public void refreshJsapiTicket() {
        try {
            String newJsapiTicket = jsapiTicketService.getJsapiTicket();
            // 这里你需要将新的 jsapi_ticket 存储在某处，例如数据库或缓存
            log.info("Successfully refreshed jsapi_ticket: {}", newJsapiTicket);
        } catch (ApiException e) {
            log.error("Error refreshing jsapi_ticket: {}", e.getMessage(), e);
        }
    }
}
