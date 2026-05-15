package org.example.mendel_challenge.transaction.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The in-memory repository is shared across the Spring context, so each test
     * uses a unique id to stay isolated from siblings.
     */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000);

    private long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }

    @Test
    @DisplayName("PUT with a valid body returns 201 Created and {\"status\":\"ok\"}")
    void putValidBody_returns201() throws Exception {
        long id = nextId();
        String body = objectMapper.writeValueAsString(Map.of(
                "amount", 5000,
                "type", "cars"
        ));

        mockMvc.perform(put("/transactions/{transaction_id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("PUT with the same id twice returns 409 Conflict on the second call")
    void putDuplicateId_returns409() throws Exception {
        long id = nextId();
        String body = objectMapper.writeValueAsString(Map.of(
                "amount", 100,
                "type", "shopping"
        ));

        mockMvc.perform(put("/transactions/{transaction_id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/transactions/{transaction_id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString(String.valueOf(id))));
    }

    @Test
    @DisplayName("PUT with missing amount returns 400 with the field-error map")
    void putMissingAmount_returns400() throws Exception {
        long id = nextId();
        String body = objectMapper.writeValueAsString(Map.of(
                "type", "cars"
        ));

        mockMvc.perform(put("/transactions/{transaction_id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount").value("amount is required"));
    }

    @Test
    @DisplayName("PUT with missing type returns 400 with the field-error map")
    void putMissingType_returns400() throws Exception {
        long id = nextId();
        String body = objectMapper.writeValueAsString(Map.of(
                "amount", 200
        ));

        mockMvc.perform(put("/transactions/{transaction_id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("type is required"));
    }

    @Test
    @DisplayName("PUT with parent_id (snake_case) is accepted and returns 201")
    void putWithSnakeCaseParentId_returns201() throws Exception {
        long parentId = nextId();
        long childId  = nextId();
        String parentBody = objectMapper.writeValueAsString(Map.of(
                "amount", 200,
                "type", "cars"
        ));

        String childBody = objectMapper.writeValueAsString(Map.of(
                "amount", 250,
                "type", "cars",
                "parent_id", parentId
        ));


        mockMvc.perform(put("/transactions/{transaction_id}", parentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parentBody))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/transactions/{transaction_id}", childId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(childBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("PUT with a malformed JSON body returns 400")
    void putMalformedJson_returns400() throws Exception {
        long id = nextId();

        mockMvc.perform(put("/transactions/{transaction_id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Malformed JSON")));
    }
}
