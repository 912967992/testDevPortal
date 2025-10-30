package com.lu.ddwyydemo04.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lu.ddwyydemo04.controller.testManIndexController;
import com.lu.ddwyydemo04.dao.QuestDao;
import com.lu.ddwyydemo04.dao.SamplesDao;
import com.lu.ddwyydemo04.exceptions.ExcelOperationException;
import com.lu.ddwyydemo04.pojo.MergedCellInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Shape;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTDrawing;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTTwoCellAnchor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

//此服务层主要用于填写测试报告页面的详细功能方法！
@Service
public class ExcelShowService {

    // 设置图片存放目录为根目录下的 imageDirectory
    // 图片存放目录的路径（C盘的imageDirectory文件夹）
    @Value("${file.storage.imagepath}")
    private String imagepath;

    @Value("${file.storage.jsonpath}")
    private String jsonpath;

    private Path getImageLocationC(){
        return Paths.get(imagepath.replace("/","\\"));
    }

    private static final Logger logger = LoggerFactory.getLogger(testManIndexController.class);
    // 定义静态最终的 ObjectMapper 实例
    private static final ObjectMapper mapper = new ObjectMapper();


    @Autowired
    private SamplesDao samplesDao;
    public List<String> getSheetNames(String file_path) {
        List<String> sheetNames = new ArrayList<>();
        try (InputStream is = new FileInputStream(file_path)) {
            Workbook workbook = WorkbookFactory.create(is);
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sheetNames;
    }


    //20240917:限制列数解析最大为100，行数则如果连续10行为空则停止解析往下一个工作表
//    public List<List<Object>> getSheetData(@RequestParam String sheetName, String file_path) {
//        List<List<Object>> sheetData = new ArrayList<>();
//        try (InputStream is = new FileInputStream(file_path)) {
//            Workbook workbook = WorkbookFactory.create(is);
//            Sheet sheet = workbook.getSheet(sheetName);
//            if (sheet != null) {
//                // 获取合并区域的列表
//                List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
//                Drawing<?> drawing = sheet.createDrawingPatriarch();
//
//                // 检查 lastRowNum 是否为 -1，如果是则设为 0
//                int lastRowNum = sheet.getLastRowNum() == -1 ? 0 : sheet.getLastRowNum();
//                int col_width = 0;
//                int maxCols = 100; // 最大列数限制
//
//                int emptyRowCount = 0; // 空行计数器
//
//                for (int rowNum = 0; rowNum <= lastRowNum; rowNum++) {
//                    Row row = sheet.getRow(rowNum);
//                    List<Object> rowData = new ArrayList<>();
//
//                    if (row != null) {
//                        // 如果 row.getLastCellNum() 为 -1，表示没有单元格，设置为 0
//                        int lastCellNum = row.getLastCellNum() == -1 ? 0 : Math.min(row.getLastCellNum(), maxCols);
//
//                        boolean isRowEmpty = true; // 标记当前行是否为空
//
//                        for (int colNum = 0; colNum < lastCellNum; colNum++) {
//                            Cell cell = row.getCell(colNum, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
//
//                            String colorStr = getColorAsString(cell);
//                            String color = "";
//
//                            // 如果单元格有内容，标记此行不为空
//                            if (cell.getCellType() != CellType.BLANK) {
//                                isRowEmpty = false;
//                            }
//
//                            System.out.println(sheetName + ","+rowNum + "," + colNum);
//                            if(sheetName.equals("封面")&&rowNum==31&&colNum==0){
//                                System.out.println(sheetName + ","+rowNum + "," + colNum);
//                                System.out.println(getCellValue(cell));
//                            }
//
//                            // 检查颜色是否为黑色，处理黑色单元格
//                            if ("000000".equals(colorStr)) {
//                                CellRangeAddress mergedRegion = getMergedRegion(cell, mergedRegions);
//                                color = "black";
//
//                                // 获取cell的字符宽度
//                                col_width = getCellWidth(cell, sheet, colNum);
//
//                                getRowData(mergedRegion, cell, drawing, sheetName, rowNum, colNum, rowData, color, col_width, file_path);
//
//                            } else if ("FF0000".equals(colorStr)) { // 处理红色单元格
//                                CellRangeAddress mergedRegion = getMergedRegion(cell, mergedRegions);
//                                color = "red";
//
//                                col_width = getCellWidth(cell, sheet, colNum);
//                                getRowData(mergedRegion, cell, drawing, sheetName, rowNum, colNum, rowData, color, col_width, file_path);
//                            }
//                        }
//
//                        // 如果整行为空，则增加空行计数
//                        if (isRowEmpty) {
//                            emptyRowCount++;
//                        } else {
//                            emptyRowCount = 0; // 如果当前行不为空，重置空行计数
//                        }
//
//                        // 如果连续 10 行为空，跳出循环
//                        if (emptyRowCount >= 10) {
//                            break;
//                        }
//
//                        // 即使行数据为空也添加到sheetData中
//                        if (!rowData.isEmpty()) {
//                            sheetData.add(rowData);
//                        }
//                    } else {
//                        // 如果行对象本身为空，也计为空行
//                        emptyRowCount++;
//
//                        // 如果连续 10 行为空，跳出循环
//                        if (emptyRowCount >= 10) {
//                            break;
//                        }
//                    }
//                }
//            }
//        } catch (IOException e) {
//            logger.error("读取文件失败", file_path, e);
//            throw new ExcelOperationException(500, "读取文件失败");
//        }
//
//        // 将 "封面" 工作表的数据写入 TXT 文件,调试用
////        if ("封面".equals(sheetName)) {
////            try (BufferedWriter writer = new BufferedWriter(new FileWriter("封面Data.txt"))) {
////                for (List<Object> row : sheetData) {
////                    writer.write(row.stream().map(Object::toString).collect(Collectors.joining(",")));
////                    writer.newLine();
////                }
////            } catch (IOException e) {
////                logger.error("保存文件失败", e);
////                throw new ExcelOperationException(500, "保存文件失败");
////            }
////        }
//        return sheetData;
//    }

    //传workbook防止内存泄漏
    public List<List<Object>> getSheetData(String sheetName, Workbook workbook,String file_path) {
        List<List<Object>> sheetData = new ArrayList<>();
        Sheet sheet = workbook.getSheet(sheetName); // 直接使用传入的 workbook
        if (sheet != null) {
            // 获取合并区域的列表
            List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
            Drawing<?> drawing = sheet.createDrawingPatriarch();

            int lastRowNum = sheet.getLastRowNum() == -1 ? 0 : sheet.getLastRowNum();
            int col_width = 0;
            int maxCols = 100; // 最大列数限制

            int emptyRowCount = 0; // 空行计数器

            for (int rowNum = 0; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                List<Object> rowData = new ArrayList<>();

                if (row != null) {
                    int lastCellNum = row.getLastCellNum() == -1 ? 0 : Math.min(row.getLastCellNum(), maxCols);
                    boolean isRowEmpty = true; // 标记当前行是否为空

                    for (int colNum = 0; colNum < lastCellNum; colNum++) {
                        Cell cell = row.getCell(colNum, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String colorStr = getColorAsString(cell);
                        String color = "";

                        if (cell.getCellType() != CellType.BLANK) {
                            isRowEmpty = false;
                        }

                        if ("000000".equals(colorStr)) {
                            CellRangeAddress mergedRegion = getMergedRegion(cell, mergedRegions);
                            color = "black";
                            col_width = getCellWidth(cell, sheet, colNum);
                            getRowData(mergedRegion, cell, drawing, sheetName, rowNum, colNum, rowData, color, col_width, file_path);
                        } else if ("FF0000".equals(colorStr)) {
                            CellRangeAddress mergedRegion = getMergedRegion(cell, mergedRegions);
                            color = "red";
                            col_width = getCellWidth(cell, sheet, colNum);
                            getRowData(mergedRegion, cell, drawing, sheetName, rowNum, colNum, rowData, color, col_width ,file_path);
                        }
                    }

                    if (isRowEmpty) {
                        emptyRowCount++;
                    } else {
                        emptyRowCount = 0;
                    }

                    if (emptyRowCount >= 10) {
                        break;
                    }

                    if (!rowData.isEmpty()) {
                        sheetData.add(rowData);
                    }
                } else {
                    emptyRowCount++;
                    if (emptyRowCount >= 10) {
                        break;
                    }
                }
            }
        }
        return sheetData;
    }

//    public List<List<List<Object>>> getAllSheetData(String filepath) {
//        List<List<List<Object>>> allSheetData = new ArrayList<>();
//        try (InputStream is = new FileInputStream(filepath)) {
//            Workbook workbook = WorkbookFactory.create(is);
//            int numberOfSheets = workbook.getNumberOfSheets();
//            for (int i = 0; i < numberOfSheets; i++) {
////                Sheet sheet = workbook.getSheetAt(i);
//                String sheetName = workbook.getSheetName(i); // 获取工作表名字
//                System.out.println("sheetName111:"+sheetName);
//                List<List<Object>> sheetData = getSheetData(sheetName, filepath); // 调用 getSheetData 方法
//                allSheetData.add(sheetData);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        //将解析好的数据保存为一份json文件
//        saveDataAsJson(allSheetData, filepath);
//
//        // 打印 allSheetData 到 TXT 文件
////        printDataToTxt(allSheetData, "output.txt");
//        return allSheetData;
//    }

    public List<List<List<Object>>> getAllSheetData(String filepath) {
        List<List<List<Object>>> allSheetData = new ArrayList<>();
        try (InputStream is = new FileInputStream(filepath)) {
            Workbook workbook = WorkbookFactory.create(is); // 创建一次 Workbook
            int numberOfSheets = workbook.getNumberOfSheets();
            for (int i = 0; i < numberOfSheets; i++) {
                String sheetName = workbook.getSheetName(i); // 获取工作表名字
                System.out.println("解析了工作表名字:" + sheetName);
                List<List<Object>> sheetData = getSheetData(sheetName, workbook, filepath); // 将 workbook 传入
                allSheetData.add(sheetData);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 将解析好的数据保存为一份json文件
        saveDataAsJson(allSheetData, filepath);
        return allSheetData;
    }

    //打印allSheetData调试用
    private void printDataToTxt(List<List<List<Object>>> allSheetData, String outputFilePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            for (List<List<Object>> sheet : allSheetData) {
                for (List<Object> row : sheet) {
                    writer.write(row.toString()); // 将每行数据写入文件
                    writer.newLine(); // 换行
                }
                writer.newLine(); // 每个工作表之间添加空行
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //将allSheetData转换成json文件保存到jsonpath路径下
    private void saveDataAsJson(List<List<List<Object>>> allSheetData, String filepath) {
        File file = new File(filepath);
        String fileName = file.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.')); // 去除文件扩展名
        String jsonFilePath = jsonpath + File.separator + baseName + ".json";

        try {
            mapper.writeValue(new File(jsonFilePath), allSheetData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //将jsonpath路径下的json文件解析出来返回给前端
    public List<List<List<Object>>> getAllSheetDataFromJson(String filepath) {
        List<List<List<Object>>> allSheetData = new ArrayList<>();

        // 获取文件名并去除扩展名
        File file = new File(filepath);
        String fileName = file.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.')); // 去除文件扩展名
        // 生成 JSON 文件的路径
        String jsonFilePath = jsonpath + File.separator + baseName + ".json";

        try {
            // 从 JSON 文件中读取数据
            allSheetData = mapper.readValue(new File(jsonFilePath), new TypeReference<List<List<List<Object>>>>() {});
        } catch (IOException e) {
            e.printStackTrace();
        }
        return allSheetData;
    }



    public int getCellWidth(Cell cell, Sheet sheet, int colNum) {

        // 默认情况下，获取单列的宽度
        double columnWidthInChars = sheet.getColumnWidth(colNum) / 256.0;
        int estimatedPixelWidth = (int) (columnWidthInChars * 6);

        // 获取单元格字体大小
        Workbook workbook = sheet.getWorkbook();
        CellStyle cellStyle = cell.getCellStyle();
        Font font = workbook.getFontAt(cellStyle.getFontIndexAsInt());
        int fontSize = font.getFontHeightInPoints();

        // 检查单元格是否在合并单元格区域内
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(cell.getRowIndex(), colNum)) {
                columnWidthInChars = 0;
                for (int col = region.getFirstColumn(); col <= region.getLastColumn(); col++) {
                    columnWidthInChars += sheet.getColumnWidth(col) / 256.0;
                }
                estimatedPixelWidth = (int) (columnWidthInChars * 6);
                break;
            }
        }

        // 根据字体大小调整宽度
        if (fontSize < 12) {
            int scaleNum = 12 - fontSize;
            estimatedPixelWidth += scaleNum*25;
        }


        return estimatedPixelWidth;
    }



    private List<Object> getRowData(CellRangeAddress mergedRegion,Cell cell,Drawing<?> drawing,String sheetName,int rowNum,int colNum,List<Object> rowData,String color,int col_width,String filepath){

        if (mergedRegion != null) {
            boolean imageFound = false;

            // 如果是合并区域的左上角单元格，则添加合并信息
            if (cell.getRowIndex() == mergedRegion.getFirstRow() && cell.getColumnIndex() == mergedRegion.getFirstColumn()) {

                for (int currentCol = mergedRegion.getFirstColumn(); currentCol <= mergedRegion.getLastColumn(); currentCol++) {
                    int finalCurrentCol = currentCol;
                    if (drawing instanceof XSSFDrawing && ((XSSFDrawing) drawing).getShapes().stream().anyMatch(s -> s instanceof XSSFPicture && ((XSSFPicture) s).getClientAnchor().getCol1() == finalCurrentCol && ((XSSFPicture) s).getClientAnchor().getRow1() == cell.getRowIndex())) { // 检查单元格是否包含图片，并且单元格颜色标记为蓝色、绿色或红色
                        //当单元格有图片和内容的时候，只展示内容
                        if(!Objects.equals(getCellValue(cell), "")){
                            break;
                        }

                        // 如果找到图片
                        imageFound = true;
                        //锚点：图片只认第一个最接近左边的第一个单元格，其他的统一认为不是图片的锚点！所以只有该图片的锚点（小的单元格）才能进这个方法
                        String imageLink = saveImageFromCell(cell, drawing, sheetName, rowNum, mergedRegion.getFirstColumn(),filepath,finalCurrentCol);
                        rowData.add(new MergedCellInfo(sheetName,imageLink,mergedRegion.getLastRow() - mergedRegion.getFirstRow() + 1, mergedRegion.getLastColumn() - mergedRegion.getFirstColumn() + 1,rowNum,colNum,color,col_width));
                        break;
                    }
                }
                // 如果没有找到图片，则添加单元格信息
                if (!imageFound && cell.getRowIndex() == mergedRegion.getFirstRow() && cell.getColumnIndex() == mergedRegion.getFirstColumn()) {
                    rowData.add(new MergedCellInfo(sheetName, getCellValue(cell),
                            mergedRegion.getLastRow() - mergedRegion.getFirstRow() + 1,
                            mergedRegion.getLastColumn() - mergedRegion.getFirstColumn() + 1,
                            rowNum, colNum, color, col_width));
                }

            }

        } else {
            int finalCurrentCol = cell.getColumnIndex();
            // 非合并单元格，添加单元格值
            if (drawing instanceof XSSFDrawing && ((XSSFDrawing) drawing).getShapes().stream().anyMatch(s -> s instanceof XSSFPicture && ((XSSFPicture) s).getClientAnchor().getCol1() == cell.getColumnIndex() && ((XSSFPicture) s).getClientAnchor().getRow1() == cell.getRowIndex())) { // 检查单元格是否包含图片，并且单元格颜色标记为蓝色、绿色或红色
                //锚点：图片只认第一个最接近左边的第一个单元格，其他的统一认为不是图片的锚点！所以只有该图片的锚点（小的单元格）才能进这个方法
                String imageLink = saveImageFromCell(cell, drawing, sheetName, rowNum, colNum,filepath,finalCurrentCol);
                rowData.add(new MergedCellInfo(sheetName,imageLink,1,1,rowNum,colNum,color,col_width));
            }else{
                rowData.add(new MergedCellInfo(sheetName,getCellValue(cell), 1, 1, rowNum, colNum,color,col_width));
            }
        }
        return rowData;
    }






    private String getColorAsString(Cell cell) {
        if (cell != null && cell.getCellStyle() != null) {
            Workbook wb = cell.getSheet().getWorkbook();
            Font font = wb.getFontAt(cell.getCellStyle().getFontIndex());
            if (font instanceof XSSFFont) {
                XSSFFont xssfFont = (XSSFFont) font;
                XSSFColor color = xssfFont.getXSSFColor();
                if (color != null) {
                    byte[] rgb = color.getRGB();
                    if (rgb != null) {
                        return bytesToHex(rgb);
                    }
                }
                // 如果颜色为null，返回默认的黑色
                return "000000";
            }
        }
        return "";
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02X", b));
        }
        return builder.toString();
    }

    private CellRangeAddress getMergedRegion(Cell cell, List<CellRangeAddress> mergedRegions) {
        for (CellRangeAddress mergedRegion : mergedRegions) {
            if (mergedRegion.isInRange(cell.getRowIndex(), cell.getColumnIndex())) {
                return mergedRegion;
            }
        }
        return null;
    }

//    private String saveImageFromCell(Cell cell, Drawing<?> drawing, String sheetName, int rowIndex, int columnIndex,String filepath,Integer  finalCurrentCol) {
//        try {
//            // 检查单元格是否包含图片
//            for (Shape shape : drawing) {
//                if (shape instanceof Picture) {
//                    Picture picture = (Picture) shape;
//                    ClientAnchor anchor = picture.getClientAnchor();
//
//                    if (finalCurrentCol == anchor.getCol1() && cell.getRowIndex() == anchor.getRow1()) {
//
//                        // 获取图片数据
//                        byte[] pictureData = picture.getPictureData().getData();
//
//                        int lastIndex = filepath.lastIndexOf('\\');
//                        String fileName = filepath.substring(lastIndex + 1);
//
//                        // 生成图片文件名
//                        String imageName = fileName + "_" + sheetName + "_" + rowIndex + "_" + columnIndex + ".png";
//
//                        // 检查并创建目录
//                        if (!Files.exists(getImageLocationC())) {
//                            Files.createDirectories(getImageLocationC());
//                        }
//
//                        // 检查文件是否已存在
//                        Path imagePathC = getImageLocationC().resolve(imageName);
//                            // 保存图片到C盘目录
//                        Files.write(imagePathC, pictureData);
//                        // 返回图片文件路径（保持路径格式不变）
//                        return "/imageDirectory/" + imageName;
//                    }
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return "";
//
//    }

    //20240926 因为龙运有一个US355 80150_功能样_A1_第1次送样_USB-C_M TO USB-C_M Gen2 240W 黑色编织网数线(铝壳款) 里边的140W功能测试的10，3会导致
//    报错空指针，找不到原因，要么创建副本就好，要么就是换成下边的方法，下边的方法会直接将报错的空指针跳过绘制成空的单元格！
    private String saveImageFromCell(Cell cell, Drawing<?> drawing, String sheetName, int rowIndex, int columnIndex, String filepath, Integer finalCurrentCol) {
        try {
            // 检查单元格是否包含图片
            for (Shape shape : drawing) {
                if (shape instanceof Picture) {
                    Picture picture = (Picture) shape;

                    // 确保 picture 和其相关的 anchor 不为 null
                    ClientAnchor anchor = picture.getClientAnchor();

                    if (anchor != null && finalCurrentCol == anchor.getCol1() && cell.getRowIndex() == anchor.getRow1()) {
                        // 增加对 picture.getPictureData() 的空指针检查
                        if (picture.getPictureData() != null) {
                            byte[] pictureData = picture.getPictureData().getData();

                            int lastIndex = filepath.lastIndexOf('\\');
                            String fileName = filepath.substring(lastIndex + 1);

                            // 生成图片文件名
                            String imageName = fileName + "_" + sheetName + "_" + rowIndex + "_" + columnIndex + ".png";

                            // 检查并创建目录
                            if (!Files.exists(getImageLocationC())) {
                                Files.createDirectories(getImageLocationC());
                            }

                            // 保存图片到指定目录
                            Path imagePathC = getImageLocationC().resolve(imageName);
                            Files.write(imagePathC, pictureData);

                            // 返回图片文件路径
                            return "/imageDirectory/" + imageName;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // 处理空指针异常以确保继续执行
            e.printStackTrace();
        }
        return "";
    }


    //解决小数点被保留位整数的bug
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    return sdf.format(cell.getDateCellValue());
                } else {
                    double numericValue = cell.getNumericCellValue();
                    // 检查是否为整数
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((int) numericValue); // 转换为整数格式
                    } else {
                        return Double.toString(numericValue); // 保留小数
                    }
                }
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case FORMULA:
                return getFormulaValue(cell); // 处理公式
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    private String getFormulaValue(Cell cell) {
        switch (cell.getCachedFormulaResultType()) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    return sdf.format(cell.getDateCellValue());
                } else {
                    return Double.toString(cell.getNumericCellValue());
                }
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case ERROR:
                return "ERROR";  // 处理错误情况
            default:
                return "";
        }
    }

    public String saveEditedCell(String filePath, Map<String, Map<String, Object>> cellData) {
        System.out.println("进来保存方法里了");
        // 更新 XLSX 文件
        String xlsxUpdateStatus = updateXlsxFile(filePath, cellData);

        // 解析 XLSX 更新状态
        try {
            JsonNode statusNode = mapper.readTree(xlsxUpdateStatus);
            String status = statusNode.path("status").asText();

            if (!"saved".equals(status)) {
                return xlsxUpdateStatus; // 返回错误状态，如果 XLSX 更新失败
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"status\": \"error\", \"message\": \"无法解析 XLSX 更新状态\"}";
        }

        return updatejsonIorD(filePath,cellData);

    }
    private String getJsonFilePath(String filePath) {
        // 从 XLSX 文件路径中提取文件名（去掉扩展名）
        String fileNameWithoutExtension = Paths.get(filePath).getFileName().toString().replaceFirst("[.][^.]+$", "");

        // 构造 JSON 文件路径
        return Paths.get(jsonpath, fileNameWithoutExtension + ".json").toString();
    }

    private String updateXlsxFile(String filePath, Map<String, Map<String, Object>> cellData) {
        try (FileInputStream fileInputStream = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream)) {

            // 处理单元格数据
            for (Map.Entry<String, Map<String, Object>> entry : cellData.entrySet()) {
                Map<String, Object> cellDetails = entry.getValue();
                String sheetName = (String) cellDetails.get("sheetName");
                int row = (int) cellDetails.get("row");
                int column = (int) cellDetails.get("column");
                String value = (String) cellDetails.get("value");

                String color = (String) cellDetails.get("color");

                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    continue;
                }

                Row sheetRow = sheet.getRow(row);
                if (sheetRow == null) {
                    sheetRow = sheet.createRow(row);
                }

                Cell cell = sheetRow.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if (cell == null) {
                    cell = sheetRow.createCell(column);
                }

                if (value.startsWith("/imageDirectory/")) {
                    deleteImageFromCell(sheet, row, column);
                    insertImageToCell(workbook, sheet, value, row, column);
                    cell.setCellValue("");
                } else {
                    if ("NG".equals(value) || "FAIL".equals(value) || "×".equals(value)|| "red".equals(color)) {
                        setCellFontColor(cell, "FF0000");
                    }else {
                        setCellFontColor(cell, "000000");
                    }
                    cell.setCellValue(value);
                }

                if (cellDetails.containsKey("scrollHeight")) {
                    float scrollHeight = ((Number) cellDetails.get("scrollHeight")).floatValue();
                    sheetRow.setHeightInPoints(scrollHeight);
                }
            }

            // 尝试获取文件输出流写入内容
            try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                workbook.write(fileOutputStream);
                return "{\"status\": \"saved\"}";
            } catch (IOException e) {
                e.printStackTrace();
                return "{\"status\": \"locked\", \"message\": \"文件正在被另一个进程使用\"}";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"status\": \"locked\", \"message\": \"文件正在被另一个进程使用\"}";
        }
    }

    public String updatejsonIorD(String filePath, Map<String, Map<String, Object>> cellData){
        // 构造 JSON 文件路径
        String jsonFilePath = getJsonFilePath(filePath);
        try {
            File jsonFile = new File(jsonFilePath);
            if (!jsonFile.exists()) {
                System.out.println("JSON file does not exist.");
                return "{\"status\": \"error\", \"message\": \"JSON 文件不存在\"}";
            }
            if (!jsonFile.canRead()) {
                System.out.println("Cannot read JSON file.");
                return "{\"status\": \"error\", \"message\": \"无法读取 JSON 文件\"}";
            }
            // 读取 JSON 文件
            JsonNode rootNode = mapper.readTree(new File(jsonFilePath));

            updateJson(rootNode, cellData);

            // 保存更新后的 JSON 数据
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonFilePath), rootNode);
            return "{\"status\": \"saved\"}";
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"status\": \"error\", \"message\": \"JSON 文件更新失败\"}";
        }
    }


    private static void updateJson(JsonNode rootNode, Map<String, Map<String, Object>> collectData) {
        List<JsonNode> allFirstElements = new ArrayList<>();
        if (rootNode.isArray() && rootNode.size() > 0) {
            for (JsonNode sheetNode : rootNode) {
                List<JsonNode> firstElements = collectFirstElements(sheetNode);
                allFirstElements.addAll(firstElements);
            }

            // 遍历 collectData
            for (Map.Entry<String, Map<String, Object>> entry : collectData.entrySet()) {
                Map<String, Object> data = entry.getValue();

                String sheetName = (String) data.get("sheetName");
                String insertRow = (String) data.get("insertRow");

                // 找到对应的工作表
                int sheetIndex = -1;
                for (int i = 0; i < allFirstElements.size(); i++) {
                    JsonNode firstElement = allFirstElements.get(i);
                    if (firstElement.path("sheetName").asText().equals(sheetName)) {
                        sheetIndex = i;
                        break;
                    }
                }

                if (sheetIndex >= 0 && sheetIndex < rootNode.size()) {
                    JsonNode sheetNode = rootNode.get(sheetIndex);
                    if (sheetNode.isArray()) {
                        // 如果 collectData 中的任意项有 insertRow = "是"，则清除该工作表中的所有数据
                        boolean hasInsertRow = collectData.values().stream()
                                .anyMatch(entryData -> "是".equals(entryData.get("insertRow")));

                        if (hasInsertRow) {
                            // 清除工作表中的所有数据
                            ((ArrayNode) sheetNode).removeAll();
                        }

                        // 将 collectData 的数据添加到工作表
                        for (Map.Entry<String, Map<String, Object>> collectEntry : collectData.entrySet()) {
                            Map<String, Object> cellData = collectEntry.getValue();
                            String cellSheetName = (String) cellData.get("sheetName");
                            int row = (int) cellData.get("row");
                            int column = (int) cellData.get("column");


                            // 确保在正确的工作表中
                            if (cellSheetName.equals(sheetName)) {
                                if ("是".equals(insertRow)) {
                                    int rowspan = (int) cellData.get("rowspan");
                                    int colspan = (int) cellData.get("colspan");
                                    int col_wdith = (int) cellData.get("col_width");
                                    String color = (String) cellData.get("color");
                                    // 清除工作表中的所有数据并添加新单元格
                                    ObjectNode newCell = new ObjectMapper().createObjectNode()
                                            .put("sheetName", cellSheetName)
                                            .put("value", (String) cellData.get("value"))
                                            .put("row", row)
                                            .put("column", column)
                                            .put("rowspan", rowspan)
                                            .put("colspan", colspan)
                                            .put("col_width", col_wdith)
                                            .put("color", color);

                                    ((ArrayNode) sheetNode).add(newCell);
                                }else{
                                    // 遍历现有单元格
                                    ArrayNode sheetArrayNode = (ArrayNode) sheetNode;
                                    boolean updated = false;  // 标志变量，表示是否已经更新了单元格

                                    for (JsonNode existingCell : sheetArrayNode) {
                                        for (JsonNode signalCell : existingCell) {
                                            int existingRow = signalCell.path("row").asInt();
                                            int existingColumn = signalCell.path("column").asInt();
                                            if (existingRow == row && existingColumn == column) {
                                                ((ObjectNode) signalCell).put("value", (String) cellData.get("value"));

                                                updated = true;  // 标记为已更新
                                                break;
                                            }
                                        }
                                        if (updated) {
                                            break;  // 跳出外层循环
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private static List<JsonNode> collectFirstElements(JsonNode sheetNode) {
        List<JsonNode> firstElements = new ArrayList<>();

        // 确保工作表节点是数组
        if (sheetNode.isArray() && sheetNode.size() > 0) {
            // 获取第一个元素
            JsonNode firstElement = sheetNode.get(0);

            // 检查第一个元素是否是数组
            if (firstElement.isArray() && firstElement.size() > 0) {
                // 获取第一个数据数组的第一个元素
                JsonNode firstDataElement = firstElement.get(0);
                // 添加到集合中
                firstElements.add(firstDataElement);
            } else {
                // 如果第一个元素不是有效的数组，添加一个空的占位符
                firstElements.add(new ObjectMapper().createObjectNode());
            }
        } else {
            // 如果工作表为空或不符合预期，添加一个空的占位符
            firstElements.add(new ObjectMapper().createObjectNode());
        }

        return firstElements;
    }

    public static void deleteImageFromCell(Sheet sheet, int row, int column) {
        if (sheet instanceof XSSFSheet) {
            XSSFSheet xssfSheet = (XSSFSheet) sheet;
            XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();

            // 检查绘图对象是否为空
            if (drawing == null) {
                return;
            }
            CTDrawing ctDrawing = drawing.getCTDrawing();
            List<CTTwoCellAnchor> anchors = new ArrayList<>(ctDrawing.getTwoCellAnchorList());

            // 获取点击单元格所属的合并区域
            CellRangeAddress mergedRegion = getMergedRegionForCell(sheet, row, column);

            // 创建一个新列表，用于保存需要删除的形状索引
            List<Integer> indicesToRemove = new ArrayList<>();

            // 遍历所有锚点（包含图片等形状）并检查它们的锚点
            for (int i = 0; i < anchors.size(); i++) {
                CTTwoCellAnchor anchor = anchors.get(i);

                // 打印图片的锚点信息
                System.out.println("图片锚点信息: Row1=" + anchor.getFrom().getRow() + ", Col1=" + anchor.getFrom().getCol() +
                        ", Row2=" + anchor.getTo().getRow() + ", Col2=" + anchor.getTo().getCol());

                boolean isInTargetCell = (anchor.getFrom().getRow() == row && anchor.getFrom().getCol() == column);

                // 检查图片锚点是否在当前单元格区域（合并或非合并）内
                if (mergedRegion != null) {
                    isInTargetCell = isInMergedRegion(anchor, mergedRegion);
                }

                if (isInTargetCell) {
                    indicesToRemove.add(i);
                }
            }

            // 删除所有需要删除的形状
            for (int index : indicesToRemove) {
                ctDrawing.removeTwoCellAnchor(index);
            }
        }
    }

    private void insertImageToCell(XSSFWorkbook workbook, Sheet sheet, String imagePath, int row, int column) {
        try {
            System.out.println("imagePath"+imagePath);

            // 将传入的路径解析为C盘imageDirectory目录下的绝对路径
            Path absoluteImagePath = getImageLocationC().resolve(imagePath.substring("/imageDirectory/".length())).normalize();
            System.out.println("imagePath (absolute): " + absoluteImagePath);

            InputStream is = new FileInputStream(absoluteImagePath.toFile());
            byte[] bytes = IOUtils.toByteArray(is);
            int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
            is.close();

            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper helper = workbook.getCreationHelper();
            ClientAnchor anchor = helper.createClientAnchor();

            // 获取点击单元格所属的合并区域
            CellRangeAddress mergedRegion = getMergedRegionForCell(sheet, row, column);

            int firstCol = column;
            int lastCol = column;
            int numOfColumns = 0;

            // 计算合并单元格的列数和行数
            if(mergedRegion != null){
                firstCol = mergedRegion.getFirstColumn();
                lastCol = mergedRegion.getLastColumn();
                numOfColumns = lastCol + firstCol + 1;
            }

            anchor.setCol1(firstCol);

            if (mergedRegion != null) {
                anchor.setCol1(numOfColumns/2-1);
                anchor.setCol2(lastCol+1);
            }else{
                anchor.setCol1(firstCol);
                anchor.setCol2(lastCol);
            }

            // 设置图片插入的位置和大小
            anchor.setRow1(row);
            anchor.setRow2(row+1);

            Picture pict = drawing.createPicture(anchor, pictureIdx);
            pict.resize(); // 自动调整图片大小以适应单元格


            // 获取单元格的宽度和高度
            float cellHeight = sheet.getRow(row).getHeightInPoints() / 72 * 96; // 将高度从点转换为像素
            // 计算合并区域的总宽度
            float cellWidth = 0;
            for (int col = firstCol; col <= lastCol; col++) {
                cellWidth += sheet.getColumnWidthInPixels(col);
            }

            // 获取图片的原始尺寸
            Dimension originalSize = pict.getImageDimension();
            float originalWidth = (float) originalSize.getWidth();
            float originalHeight = (float) originalSize.getHeight();

            System.out.println("originalWidth:"+originalWidth);
            System.out.println("originalHeight:"+originalHeight);

            float scaleWidth;
            float scaleHeight;
            float scale;
            scaleWidth = cellWidth / originalWidth;
            scaleHeight = cellHeight / originalHeight;
            System.out.println("scaleWidth:"+scaleWidth);
            System.out.println("scaleHeight:"+scaleHeight);


            if(mergedRegion!=null){
                scale = Math.min(scaleWidth, scaleHeight)/2;
            }else{
                scale = Math.min(scaleWidth, scaleHeight);
            }

            // 按照计算的缩放比例调整图片大小
            pict.resize(scale);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setCellFontColor(Cell cell, String colorStr) {
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle originalStyle = cell.getCellStyle(); // 保存当前单元格样式

        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setColor(new XSSFColor(hexToRgb(colorStr), null)); // 将十六进制颜色转换为RGB颜色并设置字体颜色
//        font.setFontHeightInPoints((short) 16);
        font.setFontName("宋体");

        CellStyle newStyle = workbook.createCellStyle();
        newStyle.cloneStyleFrom(originalStyle); // 复制当前单元格样式
        newStyle.setFont(font); // 设置新的字体颜色

        // 设置居中格式,20240923 修改保存的时候单元格自动换行
        newStyle.setAlignment(HorizontalAlignment.CENTER); // 水平居中
        newStyle.setVerticalAlignment(VerticalAlignment.CENTER); // 垂直居中
        newStyle.setWrapText(true); // 允许自动换行

        cell.setCellStyle(newStyle); // 应用新样式到单元格
    }

    private byte[] hexToRgb(String colorStr) {
        int r = Integer.valueOf(colorStr.substring(0, 2), 16);
        int g = Integer.valueOf(colorStr.substring(2, 4), 16);
        int b = Integer.valueOf(colorStr.substring(4, 6), 16);
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }

    //    获取合并单元格区域
    private static CellRangeAddress getMergedRegionForCell(Sheet sheet, int row, int column) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(row, column)) {
                return region;
            }
        }
        return null;
    }


    private static boolean isInMergedRegion(CTTwoCellAnchor anchor, CellRangeAddress mergedRegion) {
        return anchor.getFrom().getRow() >= mergedRegion.getFirstRow() && anchor.getTo().getRow() <= mergedRegion.getLastRow() &&
                anchor.getFrom().getCol() >= mergedRegion.getFirstColumn() && anchor.getTo().getCol() <= mergedRegion.getLastColumn();
    }


    public Map<String, String> uploadImage(@RequestParam("image") MultipartFile imageFile, @RequestParam("row") int row, @RequestParam("column") int column, @RequestParam("sheetName") String sheetName, @RequestParam("filepath") String filepath) {
        Map<String, String> response = new HashMap<>();
        try {
            // 检查并创建目录
            if (!Files.exists(getImageLocationC())) {
                Files.createDirectories(getImageLocationC());
            }

            int lastIndex = filepath.lastIndexOf('\\');
            String fileName = filepath.substring(lastIndex + 1);

            // 获取图片文件名
            String imageName = fileName + "_" + sheetName + "_" + row + "_" + column + ".png";
            // 构建目标文件路径
            Path targetPath = getImageLocationC().resolve(imageName);
            System.out.println("targetPath: " + targetPath);

            // 检查图片是否损坏
            try (InputStream imageInputStream = imageFile.getInputStream()) {
                BufferedImage bufferedImage = ImageIO.read(imageInputStream);
                if (bufferedImage == null) {
                    response.put("error", "上传的文件不是有效的图片文件或已损坏。");
                    logger.info("上传的文件不是有效的图片文件或已损坏。"+imageName);
                    return response;
                }
            }

            // 将上传的图片保存到目标文件路径
            Files.copy(imageFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            // 构建图片访问路径
            String imageUrl = "/imageDirectory/" + imageName;

            // 返回上传成功的图片路径
            response.put("imageUrl", imageUrl);
        } catch (IOException e) {
            e.printStackTrace();
            logger.info("和文件保存起冲突，上传图片失败，请稍等5s后重试");
            // 返回上传失败的响应
            response.put("error", "和文件保存起冲突，上传图片失败，请稍等5s后重试");
        }
        return response;
    }




    public String insertRow(String sheetName,int rowNum,String file_path) {
        try (FileInputStream fileInputStream = new FileInputStream(file_path);
             XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream);
             FileOutputStream fileOutputStream = new FileOutputStream(file_path)) {

            Sheet sheet = workbook.getSheet(sheetName);

            // 检查插入行的范围
            if (rowNum < 0 || rowNum > sheet.getLastRowNum()) {
                return "Invalid row number.";
            }

            // 向下移动行以腾出空间
            sheet.shiftRows(rowNum, sheet.getLastRowNum(), 1);

            // 创建新行
            Row newRow = sheet.createRow(rowNum);

            // 获取目标行
            Row targetRow = sheet.getRow(rowNum + 1);

            // 如果目标行存在，则复制格式
            Row sourceRow = sheet.getRow(rowNum + 1);
            if (sourceRow != null) {
                copyRowFormat(workbook, sourceRow, newRow);
                copyMergedRegions(sheet, sourceRow, newRow);

                // 检查目标行的单元格是否包含“上传图片”
                for (Cell cell : targetRow) {
                    if ("上传图片".equals(cell.getStringCellValue())) {
                        Cell newCell = newRow.createCell(cell.getColumnIndex());
                        newCell.setCellValue("上传图片");
                        newCell.setCellStyle(cell.getCellStyle()); // 复制单元格格式
                    }
                }

            }

            // 保存到文件
            workbook.write(fileOutputStream);
            logger.info("成功新增一行");
            return "成功新增一行";

        } catch (IOException e) {
            e.printStackTrace();
            return "新增一行有冲突，请稍等稍等5秒再试，谢谢！";
        }
    }

    private void copyRowFormat(XSSFWorkbook workbook, Row sourceRow, Row newRow) {
        // 复制行高
        newRow.setHeight(sourceRow.getHeight());

        for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
            Cell newCell = newRow.createCell(i);
            Cell sourceCell = sourceRow.getCell(i);

            if (sourceCell != null) {
                // 复制单元格样式
                CellStyle newCellStyle = workbook.createCellStyle();
                newCellStyle.cloneStyleFrom(sourceCell.getCellStyle());
                newCell.setCellStyle(newCellStyle);
            }
        }
    }

    private void copyMergedRegions(Sheet sheet, Row sourceRow, Row newRow) {
        int sourceRowNum = sourceRow.getRowNum();
        int newRowNum = newRow.getRowNum();
        List<CellRangeAddress> mergedRegions = new ArrayList<>();

        // 查找所有合并单元格
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress mergedRegion = sheet.getMergedRegion(i);
            if (mergedRegion.getFirstRow() == sourceRowNum && mergedRegion.getLastRow() == sourceRowNum) {
                mergedRegions.add(mergedRegion);
            }
        }

        // 复制合并单元格到新行
        for (CellRangeAddress mergedRegion : mergedRegions) {
            CellRangeAddress newMergedRegion = new CellRangeAddress(newRowNum,
                    newRowNum,
                    mergedRegion.getFirstColumn(),
                    mergedRegion.getLastColumn());
            sheet.addMergedRegion(newMergedRegion);
        }
    }

    public String deleteRow(String sheetName,int rowNum,String file_path) {
        try (FileInputStream fileInputStream = new FileInputStream(file_path)) {
            XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream);
//            FileOutputStream fileOutputStream = new FileOutputStream(file_path);
            Sheet sheet = workbook.getSheet(sheetName);

            if(rowNum<0 || rowNum>sheet.getLastRowNum()){
                return "Invalid row number";
            }

            Row previousRow = sheet.getRow(rowNum - 1);
            if (isRowEffectivelyEmpty(previousRow)) {
                //为ture时证明上一行是空的，可以进行删除
                List<CellRangeAddress> mergedRegionsToRemove = getMergedRegions(sheet, rowNum - 1);
                for (CellRangeAddress region : mergedRegionsToRemove) {
                    sheet.removeMergedRegion(findMergedRegionIndex(sheet, region));
                }
                sheet.removeRow(previousRow);
                if (rowNum <= sheet.getLastRowNum()) {
                    sheet.shiftRows(rowNum, sheet.getLastRowNum(), -1);
                }
            }


            // 保存到文件
            try (FileOutputStream fileOutputStream = new FileOutputStream(file_path)) {
                workbook.write(fileOutputStream);
                logger.info("成功删除一行");
                return "成功删除一行";
            }

        } catch (IOException e) {
            e.printStackTrace();
            return "删除一行失败.";
        }
    }
    // 判断行是否实际为空（忽略“上传图片”单元格）
    private boolean isRowEffectivelyEmpty(Row row) {
        System.out.println("row:"+row);
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) {
                String cellValue = cell.getStringCellValue().trim();
                if (!"上传图片".equals(cellValue) && !cellValue.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
    private List<CellRangeAddress> getMergedRegions(Sheet sheet, int rowNum) {
        List<CellRangeAddress> mergedRegions = new ArrayList<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == rowNum || region.getLastRow() == rowNum) {
                mergedRegions.add(region);
            }
        }
        return mergedRegions;
    }

    private int findMergedRegionIndex(Sheet sheet, CellRangeAddress region) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            if (sheet.getMergedRegion(i).equals(region)) {
                return i;
            }
        }
        return -1;
    }

    public int sampleCount(String model,String coding,String category,String version,int sample_frequency,String big_species,
                           String small_species,String high_frequency,String questStats){
        return samplesDao.sampleCount(model,coding,category,version,sample_frequency,big_species,small_species,high_frequency,questStats);
    }

    public int sampleOtherCount(String model,String coding,String high_frequency){
        return samplesDao.sampleOtherCount(model,coding,high_frequency);
    }


    public int insertSample(String tester, String filepath, String model, String coding, String category, String version, String sample_name, String  planfinish_time,String create_time,String sample_schedule,int sample_frequency,int sample_quantity,
                            String big_species,String small_species,String high_frequency,String questStats) {
        String full_model = model + " " + coding;

        // 定义日期时间格式
        DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 定义匹配 '2024-09-20T18:43' 格式的日期时间格式
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");


        // 解析字符串为 LocalDateTime
        LocalDateTime createDateTime = LocalDateTime.parse(create_time, formatter1);
        LocalDateTime planFinishDateTime = LocalDateTime.parse(planfinish_time, formatter2);

        double planTestDuration = calculateWorkDays(createDateTime,planFinishDateTime,0);
        // 将其转换为 0.5 的倍数，向上取整,只算0.5的倍数
        double adjustedWorkDays = Math.ceil(planTestDuration * 2) / 2;

        System.out.println("adjustedWorkDays:"+adjustedWorkDays);

        return samplesDao.insertSample(tester,filepath,model,coding,full_model,category,version,sample_name,planfinish_time,create_time,sample_schedule,sample_frequency,sample_quantity,big_species,small_species,high_frequency,questStats,adjustedWorkDays);
    }

    public List<String> querySample(String model,String coding,String high_frequency){
        return samplesDao.querySample(model,coding,high_frequency);
    }


    public String getUUID(String filepath){
        return samplesDao.getUUID(filepath);
    }

    public int updateUUID(String filepath,String uuid){
        return samplesDao.updateUUID(filepath,uuid);
    }

    public  List<List<List<Object>>> getJson(String filepath){
        // 获取文件名并去除扩展名
        File file = new File(filepath);
        String fileName = file.getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.')); // 去除文件扩展名
        // 生成 JSON 文件的路径
        String jsonFilePath = jsonpath + File.separator + baseName + ".json";
        logger.info("jsonFilePath:"+jsonFilePath);
        // 创建 File 对象
        File jsonFile = new File(jsonFilePath);

        // 检查文件是否存在
        if (jsonFile.exists()) {
            logger.info("JSON file exists: " + jsonFilePath);
            // 如果文件存在，可以返回从 JSON 文件读取的数据
            return getAllSheetDataFromJson(jsonFilePath);
        } else {
            logger.info("JSON file does not exist: " + jsonFilePath);
            // 如果文件不存在，处理逻辑或者返回默认值
            // return an empty list or handle the case when the file doesn't exist
            return new ArrayList<>();
        }
    }

    //计算测试时长testDuration
    // 计算上班时间内的小时数，并换算成工作天数
    // 计算工作天数，只计算工作时间段（早9到12点，下午1:30到6:30）
    // 计算工作天数，只计算工作时间段（早9到12点，下午1:30到6:30）
    public static double calculateWorkDays(LocalDateTime createTime, LocalDateTime finishTime, double restDays) {
        // 计算两个日期之间的总工作小时数
        double totalWorkHours = 0;
        LocalDateTime currentDateTime = createTime;

        while (currentDateTime.toLocalDate().isBefore(finishTime.toLocalDate()) || !currentDateTime.isAfter(finishTime)) {
            // 计算当天工作时间
            LocalDateTime endOfDay = currentDateTime.toLocalDate().atTime(18, 30);
            LocalDateTime startOfDay = currentDateTime.toLocalDate().atTime(9, 0);

            // 如果当天已经超过了完成时间，则调整结束时间
            if (finishTime.isBefore(endOfDay)) {
                endOfDay = finishTime;
            }

            // 计算早上时间段
            LocalDateTime endMorning = currentDateTime.toLocalDate().atTime(12, 0);
            LocalDateTime effectiveEndMorning = (finishTime.isBefore(endMorning) ? finishTime : endMorning);

            if (currentDateTime.isBefore(effectiveEndMorning)) {
                totalWorkHours += calculateTimeInPeriod(currentDateTime, effectiveEndMorning);
            }

            // 计算下午时间段
            LocalDateTime startAfternoon = currentDateTime.toLocalDate().atTime(13, 30);
            LocalDateTime effectiveStartAfternoon = (currentDateTime.isBefore(startAfternoon) ? startAfternoon : currentDateTime);
            LocalDateTime endAfternoon = currentDateTime.toLocalDate().atTime(18, 30);

            if (finishTime.isBefore(endAfternoon)) {
                endAfternoon = finishTime;
            }

            if (effectiveStartAfternoon.isBefore(endAfternoon)) {
                totalWorkHours += calculateTimeInPeriod(effectiveStartAfternoon, endAfternoon);
            }

            // 移动到第二天
            currentDateTime = currentDateTime.toLocalDate().plusDays(1).atStartOfDay().plusHours(9);
        }

        // 将总工作时间转换为工作天数
        double workDays = totalWorkHours / 8;

        // 减去休息天数
        workDays -= restDays;

        // 保留一位小数
        return Math.max(Math.round(workDays * 10) / 10.0, 0); // 确保返回值不小于0
    }

    private static double calculateTimeInPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        // 计算两个时间点之间的小时数
        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        return Math.max(0, minutes / 60.0);
    }


}
