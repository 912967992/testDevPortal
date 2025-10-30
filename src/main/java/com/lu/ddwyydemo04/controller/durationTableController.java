package com.lu.ddwyydemo04.controller;

import com.lu.ddwyydemo04.Service.DurationTableService;

import com.lu.ddwyydemo04.pojo.Samples;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;


@Controller
public class durationTableController {

    @Autowired
    private DurationTableService durationTableService;

    @GetMapping("/home") // 处理页面跳转请求
    public String loginHome() {
        // 返回跳转页面的视图名称
        return "testManIndex";
    }


    @GetMapping("/durationTable") // 处理页面跳转请求
    public String loginCloud(String role) {
        System.out.println(role);
        if(role.equals("DQE")){
            return "DQE/durationTableDQE";
        }else{
            return "durationTable";
        }

    }


//    @GetMapping("/searchSampleTestMan") // 处理页面跳转请求
//    @ResponseBody
//    public List<Samples> searchSampleTestMan() {
//        List<Samples> resultSamples = durationTableService.searchSampleTestMan();
//        System.out.println("resultSamples:"+resultSamples);
//        return resultSamples;
//    }

    @GetMapping("/searchSampleTestMan")
    @ResponseBody
    public List<Samples> searchSampleTestMan(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "problemTimeStart", required = false) String problemTimeStart,
            @RequestParam(value = "problemTimeEnd", required = false) String problemTimeEnd,
            @RequestParam(value = "problemFinishStart", required = false) String problemFinishStart,
            @RequestParam(value = "problemFinishEnd", required = false) String problemFinishEnd,
            @RequestParam(value = "sample_schedule", required = false) String sample_schedule
    ) {
        // 处理空字符串，转换为 null
        if (problemTimeStart != null && problemTimeStart.isEmpty()) {
            problemTimeStart = null;
        }
        if (problemTimeEnd != null && problemTimeEnd.isEmpty()) {
            problemTimeEnd = null;
        }
        if (problemFinishStart != null && problemFinishStart.isEmpty()) {
            problemFinishStart = null;
        }
        if (problemFinishEnd != null && problemFinishEnd.isEmpty()) {
            problemFinishEnd = null;
        }
        if (sample_schedule != null && sample_schedule.isEmpty()) {
            sample_schedule = null;
        }

        System.out.println("problemFinishStart:"+problemFinishStart);
        System.out.println("problemFinishEnd:"+problemFinishEnd);
        System.out.println("sample_schedule:"+sample_schedule);

        List<Samples> resultSamples = durationTableService.searchSampleTestMan(keyword, problemTimeStart, problemTimeEnd, problemFinishStart, problemFinishEnd,
                sample_schedule);
        System.out.println("resultSamples:"+resultSamples);
        return resultSamples;
    }


    @PostMapping("/exportSamples")
    @ResponseBody
    public ResponseEntity<byte[]> exportSamples(@RequestBody List<Map<String, Object>> data) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Samples");

            // 创建列标题
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "创建时间", "预计完成时间", "实际完成时间", "预计测试时长", "实测时长", "测试人员", "完整编码", "样品阶段","大类","小类", "版本", "样品名称"  ,"样品进度"};


            // 创建样式
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER); // 水平居中
            style.setVerticalAlignment(VerticalAlignment.CENTER); // 垂直居中

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(style); // 应用样式
                if(i==0){
                    sheet.setColumnWidth(i, 5 * 256); // 设置列宽
                }else if (i==1 || i==2 || i== 3){
                    sheet.setColumnWidth(i, 30 * 256); // 设置列宽
                }else if (i==4 ||i==5){
                    sheet.setColumnWidth(i, 8 * 256); // 设置列宽
                }else{
                    sheet.setColumnWidth(i, 20 * 256); // 设置列宽
                }

            }

            // 填充数据
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> item = data.get(i);
                Row row = sheet.createRow(i + 1);

                for (int j = 0; j < columns.length; j++) {
                    Cell cell = row.createCell(j);
                    Object value = null;

                    switch (j) {
                        case 0: value = item.get("sample_id"); break;
                        case 1:
                            value = item.get("create_time");
                            if (value != null) {
                                // 去掉"T"并格式化
                                value = value.toString().replace("T", " ");
                            }
                            break;
                        case 2: value = item.get("planfinish_time"); break;
                        case 3: value = item.get("finish_time"); break;
                        case 4: value = item.get("planTestDuration"); break;
                        case 5: value = item.get("testDuration"); break;
                        case 6: value = item.get("tester"); break;
                        case 7: value = item.get("full_model"); break;
                        case 8: value = item.get("sample_category"); break;
                        case 9: value = item.get("big_species"); break;
                        case 10: value = item.get("small_species"); break;
                        case 11: value = item.get("version"); break;
                        case 12: value = item.get("sample_name"); break;
                        case 13:
                            String scheduleValueStr = (String) item.get("sample_schedule");
                            if (scheduleValueStr != null) {
                                switch (scheduleValueStr) {
                                    case "0":
                                        value = "测试中";
                                        break;
                                    case "1":
                                        value = "待审核";
                                        break;
                                    case "2":
                                        value = "已完成";
                                        break;
                                    default:
                                        value = "未知状态"; // 处理其他可能的值
                                        break;
                                }
                            } else {
                                value = "未知状态"; // 处理为 null 的情况
                            }
                            break;
                    }
                    // 设置单元格值
                    if (j == 4 || j == 5) { // 如果是预计测试时长或实测时长
                        cell.setCellValue(value != null ? Double.parseDouble(value.toString()) : 0);
                    } else {
                        cell.setCellValue(value != null ? value.toString() : "");
                    }
                    cell.setCellStyle(style); // 应用样式
                }
            }

            // 将工作簿写入字节数组输出流
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=samples.xlsx");
            headers.add("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}