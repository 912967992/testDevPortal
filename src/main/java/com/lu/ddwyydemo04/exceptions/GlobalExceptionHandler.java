package com.lu.ddwyydemo04.exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExcelOperationException.class)
    public ResponseEntity<String> handleExcelOperationException(ExcelOperationException e) {
        logger.error("操作文件失败:", e.getMessage(), e);
        return ResponseEntity.status(e.getStatusCode()).body(e.getMessage());
    }

}
