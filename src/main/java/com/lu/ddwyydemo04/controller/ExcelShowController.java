package com.lu.ddwyydemo04.controller;

import com.lu.ddwyydemo04.Service.ExcelShowService;

import com.lu.ddwyydemo04.exceptions.SessionTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;


import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

//此控制层用于实现测试报告页面的填写的处理逻辑
@Controller
public class ExcelShowController {

    @Autowired
    private ExcelShowService excelShowService;

    @Value("${file.storage.templatespath}")
    private String templatesPath;

    @Value("${file.storage.savepath}")
    private String savepath;

    @Value("${file.storage.imagepath}")
    private String imagepath;

    private static final Logger logger = LoggerFactory.getLogger(testManIndexController.class);



    @PostMapping("/home/queryExist")
    @ResponseBody
    public List<String> queryExist(@RequestBody Map<String, String> payload){
        String username = payload.get("username");
        if (username == null){
            throw new SessionTimeoutException("会话已超时，请重新登录");
        }

        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String create_time = now.format(formatter);


        String filepath = payload.get("filepath");
        String model = payload.get("model");
        String coding = payload.get("coding");
        String category = payload.get("category");
        String version = payload.get("version");
        String sample_name = payload.get("sample_name");
        String planfinish_time = payload.get("planfinish_time");
        String sample_frequencyStr = payload.get("sample_frequency");
        String sample_quantityStr = payload.get("sample_quantity");
        String questStats = payload.get("questStats");
        int sample_frequency =  Integer.parseInt(sample_frequencyStr.trim()); // 使用trim()去除可能的前后空格
        int sample_quantity =  Integer.parseInt(sample_quantityStr.trim()); // 使用trim()去除可能的前后空格

        String big_species = payload.get("big_species");
        String small_species = payload.get("small_species");
        String high_frequency = payload.get("high_frequency");


        String sample_schedule = "0";
        List<String> filepaths = new ArrayList<>();
        //判断samples数据库是否已存在该型号类别版本的文件
        int countSample = excelShowService.sampleCount(model,coding,category,version,sample_frequency,big_species,small_species,high_frequency,questStats);

        if(countSample==1){
            filepaths.add("已经存在这个型号版本类别的测试报告了，不能再创建！");
        }else{
            int otherCountSample = excelShowService.sampleOtherCount(model,coding,high_frequency);
            if (otherCountSample>0){
                filepaths = excelShowService.querySample(model,coding,high_frequency);
            }else{
                String newFilepath = copyFile(filepath,model,coding,category,version,sample_name,sample_frequency,high_frequency);
                excelShowService.insertSample(username ,newFilepath,model,coding,category,version,sample_name,planfinish_time,create_time,sample_schedule,sample_frequency,sample_quantity,
                        big_species,small_species,high_frequency,questStats);
                filepaths.add(newFilepath);
            }
        }

        return filepaths;
    }


    private String copyFile(String filepath,String model,String coding,String category,String version,String sample_name,int sample_frequency,String high_frequency) {

        try {
            // 替换文件名中的非法字符“/”，“\”，这两个字符串会导致复制文件失败报错
            sample_name = sample_name.replaceAll("[/\\\\]", "_");

            String copyFilePath = "";

            String high_sign = "";
            if(high_frequency.equals("是")){
                high_sign = "高频_";
            }

            Path source = Paths.get(filepath);
            Path targetDir = Paths.get(savepath); // 使用配置的路径
            String newFileName = model + " " + coding + "_" + category + "_" + version + "_第" + sample_frequency + "次送样_" + high_sign  + sample_name + ".xlsx";
            Path target = targetDir.resolve(newFileName);
            Files.createDirectories(targetDir); // 确保目标目录存在
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            copyFilePath = target.toString(); // 返回复制后的文件路径

//            System.out.println("copyFilePath"+copyFilePath);
            return copyFilePath;
        } catch (IOException e) {
            e.printStackTrace();
            // 复制文件失败，可以进行异常处理
            return "复制文件失败";
        }

    }

    @RequestMapping("/copyClickFile")
    public ModelAndView copyClickFile(@RequestParam String filePath, @RequestParam String model, @RequestParam String coding,
                                      @RequestParam String category, @RequestParam String version, @RequestParam String sample_name,
                                      @RequestParam String planfinish_time, @RequestParam String username,@RequestParam String sample_frequencyStr,@RequestParam String sample_quantityStr,
                                      @RequestParam String big_species, @RequestParam String small_species,@RequestParam String high_frequency,@RequestParam String questStats){
        if (username == null){
            throw new SessionTimeoutException("会话已超时，请重新登录");
        }

        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String create_time = now.format(formatter);

        //样品进度0是测试中的意思
        String sample_schedule = "0";

        int sample_frequency = Integer.parseInt(sample_frequencyStr.trim()); // 使用trim()去除可能的前后空格
        int sample_quantity = Integer.parseInt(sample_quantityStr.trim()); // 使用trim()去除可能的前后空格

        String copyFilepath = copyFile(filePath,model,coding,category,version,sample_name,sample_frequency,high_frequency);
        excelShowService.insertSample(username,copyFilepath,model,coding,category,version,sample_name,planfinish_time,create_time,sample_schedule,sample_frequency,sample_quantity,big_species,small_species,high_frequency,questStats);
        return  new ModelAndView("redirect:/excelShow")
                .addObject("filePath", copyFilepath);
    }


    @PostMapping(value = "/loginExcelShow")
    @ResponseBody
    public Map<String, Object> loginExcelShow(@RequestParam String filePath) {
        Map<String, Object> response = new HashMap<>();
        RandomAccessFile randomAccessFile = null;
        try {
            // 尝试获取文件的排他锁
            System.out.println("尝试获取文件的排他锁filePath:"+filePath);
            randomAccessFile = new RandomAccessFile(filePath, "rw");

            if (randomAccessFile != null) {
                randomAccessFile.close();
            }

            response.put("status", "success");
            response.put("redirectUrl", "/excelShow?filePath=" + URLEncoder.encode(filePath, "UTF-8"));
        } catch (IOException e) {
            logger.info(e.getMessage());
            e.printStackTrace();

            response.put("status", "error");
            response.put("message", "文件正在保存中，请稍等五秒再试");
        } catch (Throwable t) {
            logger.error("Throwable错误: {}",  t.getMessage(), t);
            response.put("status", "error");
            response.put("message", "发生严重错误，请联系管理员");
            return response; // 返回错误响应

        } finally {
            // 确保在所有情况下都释放资源
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
            } catch (IOException e) {
                logger.error("释放资源失败: {}", e.getMessage());
            }
        }

        return response;
    }


    @RequestMapping("/excelShow")
    public String excelShow(@RequestParam String filePath, Model model) {
        model.addAttribute("filePath", filePath);
        return "excelShow"; // 返回视图名称，将跳转到 excelShow.html 或 excelShow.jsp 页面
    }




    @GetMapping("/sheets")
    @ResponseBody
    public List<String> getSheetNames(@RequestParam String filePath) {
        return excelShowService.getSheetNames(filePath);
    }


    @GetMapping("/getAllSheetData")
    @ResponseBody
    public List<List<List<Object>>> getAllSheetData(@RequestParam String filePath) {
        return excelShowService.getAllSheetData(filePath);
    }


    @PostMapping("/saveEditedCell")
    @ResponseBody
    public String saveEditedCell(@RequestParam String filePath, @RequestBody Map<String, Map<String, Object>> cellData) {
        logger.info("saveData："+cellData);

        return excelShowService.saveEditedCell(filePath,cellData);
    }

    @PostMapping("/updatejsonIorD")
    @ResponseBody
    public String updatejsonIorD(@RequestParam String filePath, @RequestBody Map<String, Map<String, Object>> cellData) {
        logger.info("updatejsonIorD："+cellData);
        return excelShowService.updatejsonIorD(filePath,cellData);
    }


    @PostMapping("/uploadImage")
    @ResponseBody
    public Map<String, String> uploadImage(@RequestParam("image") MultipartFile imageFile,@RequestParam("row") int row,@RequestParam("column") int column,@RequestParam("sheetName") String sheetName,
                                           @RequestParam("filepath") String filepath) {
        return excelShowService.uploadImage(imageFile,row,column,sheetName,filepath);
    }


    @GetMapping("/imageDirectory/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            // 构建动态的文件路径
            Path filePath = Paths.get(imagepath.replace("/","\\")).resolve(filename).normalize();

//            Path filePath = imageLocationC.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }


    @PostMapping("/insertRow")
    @ResponseBody
    public String insertRow(@RequestParam String sheetName, @RequestBody Map<String, Integer> requestBody,@RequestParam String filePath) {
        int row = requestBody.get("row");
        return excelShowService.insertRow(sheetName, row, filePath);
    }

    @PostMapping("/deleteRow")
    @ResponseBody
    public String deleteRow(@RequestParam String sheetName, @RequestBody Map<String, Integer> requestBody,@RequestParam String filePath) {
        int row = requestBody.get("row");
        return excelShowService.deleteRow(sheetName, row, filePath);
    }


    @PostMapping("/getJson")
    @ResponseBody
    public List<List<List<Object>>> getJson(@RequestBody Map<String, String> request) {
        String filepath = request.get("filepath");

        return excelShowService.getJson(filepath);
    }


    @PostMapping("/getUUID")
    @ResponseBody
    public String getUUID(@RequestBody Map<String, String> request) {
        String filepath = request.get("filepath");
        String uuidMysql = excelShowService.getUUID(filepath);
        System.out.println("uuidMysql:"+uuidMysql);
        logger.info("getUUID:"+uuidMysql);
        return uuidMysql;
    }

    @PostMapping("/updateUUID")
    @ResponseBody
    public int updateUUID(@RequestBody Map<String, String> request) {
        String filepath = request.get("filepath");
        String uuid = request.get("uuid");

        int updateUUID_result=excelShowService.updateUUID(filepath,uuid);

        logger.info(filepath+"更新UUID，返回值是："+updateUUID_result);
        return updateUUID_result;
    }



}
