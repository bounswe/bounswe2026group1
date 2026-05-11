package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.ObjectTypeInfoResponse;
import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.ObjectTypeService;
import com.bounswe2026group1.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ObjectTypeController.class)
class ObjectTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObjectTypeService objectTypeService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RegisteredUserRepository registeredUserRepository;

    @Value("${app.api.key}")
    private String validApiKey;

    @Test
    void getAll_returnsCatalogueJson() throws Exception {
        ObjectTypeInfoResponse entry = new ObjectTypeInfoResponse(
                ObjectType.RAMP,
                "Ramp",
                List.of(new ObjectTypeInfoResponse.IssueInfoResponse(IssueType.TOO_STEEP, "Too steep")),
                Map.of("type", "object"));

        when(objectTypeService.getAll()).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/object-types").header("Mapcess-Key", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].objectType").value("RAMP"))
                .andExpect(jsonPath("$[0].issues[0].issueType").value("TOO_STEEP"));
    }
}
