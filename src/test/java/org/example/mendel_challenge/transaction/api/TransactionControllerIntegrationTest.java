package org.example.mendel_challenge.transaction.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
     * uses a unique id (and, for GET-by-type tests, a unique type) to stay isolated
     * from siblings.
     */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000);
    private static final AtomicLong TYPE_GENERATOR = new AtomicLong();

    private long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }

    private String nextType(String prefix) {
        return prefix + "-" + TYPE_GENERATOR.incrementAndGet();
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

    @Test
    @DisplayName("GET /transactions/types/{type} returns 200 and every id stored under that type")
    void getByType_returnsAllIds() throws Exception {
        String type = nextType("cars");
        long firstId  = nextId();
        long secondId = nextId();
        long thirdId  = nextId();

        putTransaction(firstId,  100, type);
        putTransaction(secondId, 200, type);
        putTransaction(thirdId,  300, type);

        mockMvc.perform(get("/transactions/types/{type}", type))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$", containsInAnyOrder(
                        (int) firstId, (int) secondId, (int) thirdId
                )));
    }

    @Test
    @DisplayName("GET /transactions/types/{type} returns 200 and an empty array when no transactions match")
    void getByType_returnsEmptyArray_whenNoMatch() throws Exception {
        String type = nextType("unknown");

        mockMvc.perform(get("/transactions/types/{type}", type))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /transactions/types/{type} returns only ids of the requested type")
    void getByType_returnsOnlyMatchingType() throws Exception {
        String carsType = nextType("cars");
        String shoppingType = nextType("shopping");
        long carsId      = nextId();
        long shoppingId  = nextId();

        putTransaction(carsId,     500, carsType);
        putTransaction(shoppingId, 600, shoppingType);

        mockMvc.perform(get("/transactions/types/{type}", carsType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value((int) carsId));

        mockMvc.perform(get("/transactions/types/{type}", shoppingType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value((int) shoppingId));
    }

    @Test
    @DisplayName("GET /transactions/sum/{id} returns the transitive sum of the subtree (spec example shape)")
    void getSum_returnsAggregatedSum_forSpecShapedTree() throws Exception {
        // Reproduces the spec's 10 -> 11 -> 12 example with unique ids so the test
        // stays isolated from the shared in-memory store.
        long rootId   = nextId();
        long midId    = nextId();
        long leafId   = nextId();
        String type   = nextType("shopping");

        putTransaction(rootId, 5000,  type);
        putTransaction(midId,  10000, type, rootId);
        putTransaction(leafId, 5000,  type, midId);

        mockMvc.perform(get("/transactions/sum/{transaction_id}", rootId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sum").value(20000.0));

        mockMvc.perform(get("/transactions/sum/{transaction_id}", midId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(15000.0));

        mockMvc.perform(get("/transactions/sum/{transaction_id}", leafId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(5000.0));
    }

    @Test
    @DisplayName("GET /transactions/sum/{id} on a node with no children returns just its own amount")
    void getSum_returnsRootAmount_whenNoChildren() throws Exception {
        long id = nextId();
        String type = nextType("cars");

        putTransaction(id, 750, type);

        mockMvc.perform(get("/transactions/sum/{transaction_id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(750.0));
    }

    @Test
    @DisplayName("GET /transactions/sum/{id} returns 404 when the id is not in the store")
    void getSum_returns404_whenIdUnknown() throws Exception {
        long id = nextId(); // never PUT

        mockMvc.perform(get("/transactions/sum/{transaction_id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString(String.valueOf(id))));
    }

    private void putTransaction(long id, double amount, String type) throws Exception {
        putTransaction(id, amount, type, null);
    }

    private void putTransaction(long id, double amount, String type, Long parentId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        payload.put("type", type);
        if (parentId != null) {
            payload.put("parent_id", parentId);
        }
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(put("/transactions/{transaction_id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
