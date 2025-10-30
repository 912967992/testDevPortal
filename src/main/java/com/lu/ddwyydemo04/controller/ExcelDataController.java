package com.lu.ddwyydemo04.controller;

import com.lu.ddwyydemo04.Service.ExcelDataService;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ExcelDataController {

    @Autowired
    private ExcelDataService excelDataService;

    @PostMapping("/import/excel")
    public String importExcelData(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            // 处理文件为空的情况
            return "redirect:/error";
        }

        try {
            // 将上传的文件保存到临时文件中
            File tempFile = File.createTempFile("temp", ".xls");
            file.transferTo(tempFile);

            // 处理上传的文件
            excelDataService.importDataFromExcel(tempFile.getAbsolutePath());

            // 删除临时文件
            tempFile.delete();

            return "redirect:/success";
        } catch (IOException e) {
            // 处理文件保存失败的情况
            e.printStackTrace();
            return "redirect:/error";
        }
    }

    @RequestMapping("/loginTemplates")
    public String loginQuest() {
        return "uploadTemplate"; // 返回视图名称，将跳转到testPageUI.html页面
    }

//    @PostMapping("/uploadTemplates")
//    @ResponseBody
//    public List<List<List<String>>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
//        try (InputStream inputStream = file.getInputStream()) {
//            List<List<List<String>>> jsonDataBySheet = excelDataService.parseFile(inputStream);
//            return jsonDataBySheet;
//        }
//    }



}
