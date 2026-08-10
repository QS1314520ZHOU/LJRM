package com.smartcare.icustats.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void okShouldReturnCode200() {
        ApiResponse<String> response = ApiResponse.ok("test data");
        assertEquals(200, response.getCode());
        assertEquals("success", response.getMsg());
        assertEquals("test data", response.getData());
    }

    @Test
    void okWithMsgShouldReturnCustomMsg() {
        ApiResponse<String> response = ApiResponse.ok("ok", "data");
        assertEquals(200, response.getCode());
        assertEquals("ok", response.getMsg());
        assertEquals("data", response.getData());
    }

    @Test
    void errorShouldReturnCorrectCode() {
        ApiResponse<Void> response = ApiResponse.error(400, "参数缺失");
        assertEquals(400, response.getCode());
        assertEquals("参数缺失", response.getMsg());
        assertNull(response.getData());
    }

    @Test
    void jsonSerializationShouldMatchNodeJs() throws Exception {
        ApiResponse<String> response = ApiResponse.ok("test");
        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"code\":200"));
        assertTrue(json.contains("\"msg\":\"success\""));
        assertTrue(json.contains("\"data\":\"test\""));
    }
}
