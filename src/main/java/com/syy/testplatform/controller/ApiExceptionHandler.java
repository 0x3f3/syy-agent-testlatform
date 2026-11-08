package com.syy.testplatform.controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,Object>> badRequest(IllegalArgumentException ex){return ResponseEntity.badRequest().body(Map.of("timestamp",LocalDateTime.now().toString(),"status",400,"error","Bad Request","message",safe(ex)));}
 @ExceptionHandler(IllegalStateException.class) public ResponseEntity<Map<String,Object>> conflict(IllegalStateException ex){return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("timestamp",LocalDateTime.now().toString(),"status",422,"error","Unprocessable Entity","message",safe(ex)));}
 @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,Object>> internal(Exception ex){return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("timestamp",LocalDateTime.now().toString(),"status",500,"error","Internal Server Error","message",safe(ex)));}
 private String safe(Exception ex){String value=ex.getMessage();return value==null?ex.getClass().getSimpleName():value.substring(0,Math.min(value.length(),1500));}
}
