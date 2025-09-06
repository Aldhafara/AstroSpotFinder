package com.aldhafara.astroSpotFinder.controller;

import com.aldhafara.astroSpotFinder.service.AstroSpotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AstroSpotController.class)
class AstroSpotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AstroSpotService astroSpotService;

    @Test
    void shouldReturnBadRequest_whenLatitudeIsTooLow() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "-91")
                        .param("longitude", "20")
                        .param("radiusKm", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andExpect(jsonPath("$.message").value("searchBestSpotsWithClusters.latitude: must be greater than or equal to -90"));
    }

    @Test
    void shouldReturnBadRequest_whenLatitudeIsTooHigh() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "91")
                        .param("longitude", "20")
                        .param("radiusKm", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andExpect(jsonPath("$.message").value("searchBestSpotsWithClusters.latitude: must be less than or equal to 90"));
    }

    @Test
    void shouldReturnBadRequest_whenLongitudeIsTooLow() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "20")
                        .param("longitude", "-181")
                        .param("radiusKm", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andExpect(jsonPath("$.message").value("searchBestSpotsWithClusters.longitude: must be greater than or equal to -180"));
    }

    @Test
    void shouldReturnBadRequest_whenLongitudeIsTooHigh() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "20")
                        .param("longitude", "181")
                        .param("radiusKm", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andExpect(jsonPath("$.message").value("searchBestSpotsWithClusters.longitude: must be less than or equal to 180"));
    }

    @Test
    void shouldReturnBadRequest_whenRadiusIsTooLow() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "20")
                        .param("longitude", "20")
                        .param("radiusKm", "-2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andExpect(jsonPath("$.message").value("searchBestSpotsWithClusters.radiusKm: must be greater than or equal to 0"));
    }

    @Test
    void shouldReturnBadRequest_whenRadiusIsTooHigh() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "20")
                        .param("longitude", "20")
                        .param("radiusKm", "151")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andExpect(jsonPath("$.message").value("searchBestSpotsWithClusters.radiusKm: must be less than or equal to 150"));
    }

    @Test
    void shouldReturnBadRequest_whenLongitudeIsBadType() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "20")
                        .param("longitude", "cow")
                        .param("radiusKm", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: longitude"));
    }

    @Test
    void shouldReturnBadRequest_whenLatitudeIsBadType() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "cow")
                        .param("longitude", "20")
                        .param("radiusKm", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: latitude"));
    }

    @Test
    void shouldReturnBadRequest_whenRadiusIsBadType() throws Exception {
        mockMvc.perform(get("/astrospots/best")
                        .param("latitude", "20")
                        .param("longitude", "20")
                        .param("radiusKm", "cow")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: radiusKm"));
    }

}