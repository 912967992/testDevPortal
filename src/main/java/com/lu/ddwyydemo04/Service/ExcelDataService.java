package com.lu.ddwyydemo04.Service;


import org.apache.poi.ss.usermodel.*;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.io.*;

import java.util.*;

@Service
public class ExcelDataService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //    以下是用来上传任务文件按钮逻辑
    @Transactional
    public void importDataFromExcel(String filePath) {
        try {
            FileInputStream file = new FileInputStream(new File(filePath));
            Workbook workbook = WorkbookFactory.create(file);
            Sheet sheet = workbook.getSheetAt(0); // Assume the data is in the first sheet

            // Map column names to database field names
            String[] columnNames = {
                    "测试进度", "测试编号", "项目负责人", "供应商", "产品分类", "产品型号", "产品名称", "数量",
                    "样品类型", "需求完成时间", "送样人", "送样时间", "送样周期", "送样备注", "测试目的"
            };

            // Find the index of the columns with column names
            int[] columnIndexes = new int[columnNames.length];
            Row firstRow = sheet.getRow(0);
            for (int i = 0; i < columnNames.length; i++) {
                int columnIndex = -1;
                Iterator<Cell> cellIterator = firstRow.cellIterator();
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    if (cell.getStringCellValue().equals(columnNames[i])) {
                        columnIndex = cell.getColumnIndex();
                        break;
                    }
                }
                if (columnIndex == -1) {
                    System.out.println("Column '" + columnNames[i] + "' not found.");
                    return;
                }
                columnIndexes[i] = columnIndex;
            }

            // Read data from all rows
            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                // Check if this is the header row
                if (row.getRowNum() == 0) {
                    continue;
                }
                // Check if all required columns are present
                boolean hasNullColumn = false;
                for (int columnIndex : columnIndexes) {
                    if (row.getCell(columnIndex) == null) {
                        hasNullColumn = true;
                        break;
                    }
                }
                if (hasNullColumn) {
                    continue;
                }
                // Read data from the row
                List<Object> params = new ArrayList<>();
                for (int columnIndex : columnIndexes) {
                    Cell cell = row.getCell(columnIndex);
                    switch (cell.getCellType()) {
                        case STRING:
                            params.add(cell.getStringCellValue());
                            break;
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                Date date = cell.getDateCellValue();
                                params.add(new java.sql.Date(date.getTime()));
                            } else {
                                params.add(cell.getNumericCellValue());
                            }
                            break;
                        default:
                            params.add(""); // Treat other cell types as empty string
                            break;
                    }
                }
                // Check if test number already exists in database
                String testNumber = (String) params.get(0);
                if (isTestNumberExists(testNumber)) {
                    // Test number already exists, skip inserting this row
                    System.out.println("进来了说明存在这个测试编号:" + testNumber);
                    continue;
                }
                // Insert data into database
                System.out.println("在这说明进行了插入" + testNumber);
                insertRowToDatabase(params.toArray());
            }

            workbook.close();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void insertRowToDatabase(Object[] params) {
        String sql = "INSERT INTO quest (test_schedule,test_number, project_manager, supplier, product_category, " +
                "product_model, product_name, quantity, sample_type, due_date, sender, " +
                "send_date, send_cycle, send_remark, test_purpose, testman) " +  // 添加 testman 列
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // 检查参数列表长度，如果长度不够，手动添加空值到参数列表
        if (params.length < 16) {
            Object[] newParams = Arrays.copyOf(params, 16); // 长度扩展到 15
            newParams[15] = null; // 在最后添加空字符串或者 null，视数据库表的要求而定
            params = newParams;
        }
        jdbcTemplate.update(sql, params);
    }


    private boolean isTestNumberExists(String testNumber) {
        String sql = "SELECT COUNT(*) FROM quest WHERE test_number = ?";
        int count = jdbcTemplate.queryForObject(sql, Integer.class, testNumber);
        return count > 0;
    }






}