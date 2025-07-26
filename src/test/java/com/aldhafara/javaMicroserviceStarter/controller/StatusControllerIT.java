package com.aldhafara.javaMicroserviceStarter.controller;

import com.aldhafara.javaMicroserviceStarter.service.StatusService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
class StatusControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StatusService statusService;

    @Test
    void shouldReturnStatusUp() throws Exception {
        mockMvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.uptime", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.uptimePretty", not(emptyString())))
                .andExpect(jsonPath("$.timestamp", not(emptyString())));
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        StatusService statusService() {
            return Mockito.mock(StatusService.class);
        }
    }
}
