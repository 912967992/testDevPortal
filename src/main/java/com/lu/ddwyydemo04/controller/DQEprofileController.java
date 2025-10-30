package com.lu.ddwyydemo04.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiMediaUploadRequest;
import com.dingtalk.api.response.OapiMediaUploadResponse;
import com.lu.ddwyydemo04.Service.AccessTokenService;
import com.taobao.api.ApiException;
import com.taobao.api.FileItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Controller
public class DQEprofileController {

    @Value("${file.storage.templatespath}")
    private String templatesPath;

    @Autowired
    private AccessTokenService accessTokenService;

    @GetMapping("/DQEprofile") // 处理页面跳转请求
    public String loginProfile() {
        // 返回跳转页面的视图名称
        return "DQE/DQEprofile";
    }



    @PostMapping("/download")
    public ResponseEntity<Map<String, Object>> downloadFile(@RequestBody Map<String, String> request) {
        String filePath = request.get("path");
        Map<String, Object> response = new HashMap<>();

        try {
            // 获取文件
            File file = new File(filePath);
            if (!file.exists()) {
                throw new FileNotFoundException("文件未找到: " + filePath);
            }

            // 获取钉钉AccessToken
            String accessToken = accessTokenService.getAccessToken();

            // 上传文件到钉钉临时服务器
            String mediaId = uploadFileToDingTalk(file, accessToken);

            response.put("success", true);
            response.put("mediaId", mediaId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "文件下载失败");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private String uploadFileToDingTalk(File file, String accessToken) throws IOException, ApiException {
        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/media/upload");
        OapiMediaUploadRequest req = new OapiMediaUploadRequest();
        req.setType("file");
// 要上传的媒体文件
        FileItem item = new FileItem(file);
        req.setMedia(item);
        OapiMediaUploadResponse rsp = client.execute(req, accessToken);
        System.out.println(parseMediaId(rsp.getBody()));
        System.out.println(rsp.getBody());
        return parseMediaId(rsp.getBody());
    }

    private String parseMediaId(String responseBody) throws ApiException {
        JSONObject jsonObject = JSON.parseObject(responseBody);
        if (jsonObject.getInteger("errcode") == 0) {
            return jsonObject.getString("media_id");
        } else {
            throw new ApiException("Error uploading file: " + jsonObject.getString("errmsg"));
        }
    }

    //    上传模板文件
//    @PostMapping("/DQEprofile/uploadTemplate")
//    @ResponseBody
//    public ResponseEntity<String> uploadTemplate(@RequestParam("file") MultipartFile file,
//                                                 @RequestParam("path") String path,
//                                                 @RequestParam("action") String action) {
//        try {
//            System.out.println("进来uploadTemplate");
//            Path targetPath = Paths.get(path);
//            if ("overwrite".equals(action)) {
//                // 覆盖文件
//                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
//            } else if ("save-as-new".equals(action)) {
//                // 保存为新文件
//                Path directory = targetPath.getParent();
//                Path newFilePath = directory.resolve(file.getOriginalFilename());
//                Files.copy(file.getInputStream(), newFilePath);
//            }
//            return ResponseEntity.ok("File uploaded successfully");
//        } catch (IOException e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload file");
//        }
//    }

    @PostMapping("/DQEprofile/uploadTemplateChunk")
    @ResponseBody
    public ResponseEntity<String> uploadTemplateChunk(@RequestParam("file") MultipartFile file,
                                                      @RequestParam("path") String path,
                                                      @RequestParam("action") String action,
                                                      @RequestParam("chunk") int chunk,
                                                      @RequestParam("totalChunks") int totalChunks) {
        try {
            Path targetPath = Paths.get(path + ".part" + chunk);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Check if all chunks are uploaded
            if (chunk == totalChunks - 1) {
                // Merge chunks
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(path), StandardOpenOption.CREATE)) {
                    for (int i = 0; i < totalChunks; i++) {
                        Path partPath = Paths.get(path + ".part" + i);
                        Files.copy(partPath, outputStream);
                        Files.delete(partPath); // Delete the chunk after merging
                    }
                }
            }

            return ResponseEntity.ok("Chunk uploaded successfully");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload chunk");
        }
    }



}
