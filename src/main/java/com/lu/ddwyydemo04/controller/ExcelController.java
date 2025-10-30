package com.lu.ddwyydemo04.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class ExcelController {
    private static final String FILE_PATH = "files/example.xlsx";

    @RequestMapping("/loginExcel")
    public String loginExcel() {
        return "testPageUI"; // 返回视图名称，将跳转到testPageUI.html页面
    }

    @RequestMapping("/loginUploadquest")
    public String loginQuest() {
        return "uploadquest"; // 返回视图名称，将跳转到testPageUI.html页面
    }


    @GetMapping("/createExcel")
    public void createExcel(HttpServletResponse response) {
        try {
            // Create a new Excel workbook
            Workbook workbook = new XSSFWorkbook();

            // Create two sheets
            workbook.createSheet("Sheet1");
            workbook.createSheet("Sheet2");

            // Define the directory to save the file
            String directory = System.getProperty("user.dir") + File.separator + "files";

            // Create the directory if it doesn't exist
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Define the file path
            String filePath = directory + File.separator + "example.xlsx";

            // Write the workbook content to a file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }

            // Close the workbook
            workbook.close();

            // 返回成功消息
            response.getWriter().write("Excel file created successfully at: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
            // 返回错误消息
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try {
                response.getWriter().write("Error occurred while creating Excel file.");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

//    复制最后一行插入
    @GetMapping("/copyExcel")
    @ResponseBody
    private String copyExcel(){
        try (FileInputStream fileInputStream = new FileInputStream(FILE_PATH)) {

            XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream);
            Sheet sheet = workbook.getSheet("2.芯片FW版本");

            // Find the last row with content
            int lastRowNum = sheet.getLastRowNum();

            // Copy the format of the last row without the content
            if (lastRowNum >= 0) {
                Row sourceRow = sheet.getRow(lastRowNum);
                Row newRow = sheet.createRow(lastRowNum + 1);

                // Copy row height
                newRow.setHeight(sourceRow.getHeight());

                for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
                    Cell newCell = newRow.createCell(i);
                    Cell sourceCell = sourceRow.getCell(i);

                    if (sourceCell != null) {
                        // Copy cell style
                        CellStyle newCellStyle = workbook.createCellStyle();
                        newCellStyle.cloneStyleFrom(sourceCell.getCellStyle());
                        newCell.setCellStyle(newCellStyle);
                    }
                }
            }

            // 保存到文件
            FileOutputStream fileOutputStream = new FileOutputStream(FILE_PATH);
            workbook.write(fileOutputStream);
            fileOutputStream.close();
            fileInputStream.close();

            return "Excel file updated successfully at: " + FILE_PATH;


        } catch (IOException e) {
            e.printStackTrace();

        }
        return "Error occurred while updating Excel file.";
    }


    //复制插入excel中间的行
    @GetMapping("/copyExcelUp")
    @ResponseBody
    private String copyExcelUp() {
        try (FileInputStream fileInputStream = new FileInputStream(FILE_PATH)) {
            XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream);
            Sheet sheet = workbook.getSheet("2.芯片FW版本");

            // 遍历所有行，查找包含“点击新增一行”的单元格
            int lastRowNum = sheet.getLastRowNum();
            for (int rowNum = 0; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row != null) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING && "点击新增一行".equals(cell.getStringCellValue())) {
                            // 向上插入新行并复制格式
                            sheet.shiftRows(rowNum, lastRowNum, 1);
                            Row newRow = sheet.createRow(rowNum);
                            copyRowFormat(workbook, row, newRow);
                            copyMergedRegions(sheet, row, newRow);
                            rowNum++; // 跳过新插入的行，避免再次处理
                            lastRowNum++; // 更新lastRowNum以反映新插入的行
                            break;
                        }
                    }
                }
            }

            // 保存到文件
            try (FileOutputStream fileOutputStream = new FileOutputStream(FILE_PATH)) {
                workbook.write(fileOutputStream);
            }

            workbook.close();
            return "Excel file updated successfully at: " + FILE_PATH;

        } catch (IOException e) {
            e.printStackTrace();
            return "Error occurred while updating Excel file.";
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

    @GetMapping("/deleteRows")
    @ResponseBody
    public String deleteRows() {
        try (FileInputStream fileInputStream = new FileInputStream(FILE_PATH)) {
            XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream);
            Sheet sheet = workbook.getSheet("2.芯片FW版本");

            int lastRowNum = sheet.getLastRowNum();
            for (int rowNum = 0; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row != null) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING && "删除上一行".equals(cell.getStringCellValue())) {
                            if (rowNum > 0) {
                                Row previousRow = sheet.getRow(rowNum - 1);
                                if (isRowEmpty(previousRow)) {
                                    List<CellRangeAddress> mergedRegionsToRemove = getMergedRegions(sheet, rowNum - 1);
                                    for (CellRangeAddress region : mergedRegionsToRemove) {
                                        sheet.removeMergedRegion(findMergedRegionIndex(sheet, region));
                                    }
                                    sheet.removeRow(previousRow);
                                    if (rowNum <= lastRowNum) {
                                        sheet.shiftRows(rowNum, lastRowNum, -1);
                                    }

                                    rowNum--;
                                    lastRowNum--;
                                }
                            }
                            break;
                        }
                    }
                }
            }

            try (FileOutputStream fileOutputStream = new FileOutputStream(FILE_PATH)) {
                workbook.write(fileOutputStream);
            }

            workbook.close();
            return "Row deleted successfully if it was empty at: " + FILE_PATH;

        } catch (IOException e) {
            e.printStackTrace();
            return "Error occurred while updating Excel file.";
        }
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

    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private void removeMergedRegions(Sheet sheet, int rowNum) {
        Iterator<CellRangeAddress> iterator = sheet.getMergedRegions().iterator();
        while (iterator.hasNext()) {
            CellRangeAddress mergedRegion = iterator.next();
            if (mergedRegion.getFirstRow() == rowNum && mergedRegion.getLastRow() == rowNum) {
                iterator.remove();
            }
        }
    }
}
