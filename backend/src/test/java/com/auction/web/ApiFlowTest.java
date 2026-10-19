package com.auction.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String name) throws Exception {
        String body = """
                {"username":"%s","email":"%s@example.com","password":"secret123"}""".formatted(name, name);
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private static String uniq(String p) {
        return p + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void registerLoginAndMe() throws Exception {
        String name = uniq("u");
        String token = register(name);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secret123\"}".formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"wrong\"}".formatted(name)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String name = uniq("dup");
        register(name);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"email\":\"other_%s@example.com\",\"password\":\"secret123\"}".formatted(name, name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("taken")));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auctions/1/bids").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auctions")).andExpect(status().isOk());
        mvc.perform(get("/api/stats")).andExpect(status().isOk());
    }

    @Test
    void adminEndpointsAreForbiddenForNormalUsers() throws Exception {
        String token = register(uniq("plain"));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullAuctionLifecycle() throws Exception {
        String sellerToken = register(uniq("seller"));
        String bidderToken = register(uniq("bidder"));
        String rivalToken = register(uniq("rival"));

        String auctionJson = """
                {"title":"Test Vase","description":"A fine vase","category":"Home","startingPrice":50,
                 "minIncrement":5,"durationMinutes":30}""";
        String created = mvc.perform(post("/api/auctions").header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(auctionJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.minNextBid").value(50.0))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        // seller can't bid on own auction
        bid(sellerToken, id, "60").andExpect(status().isForbidden());
        // bid below the starting price
        bid(bidderToken, id, "49.99").andExpect(status().isBadRequest());
        // valid bid
        bid(bidderToken, id, "50").andExpect(status().isCreated());
        // same bidder can't outbid self
        bid(bidderToken, id, "70").andExpect(status().isBadRequest());
        // rival must beat price + increment
        bid(rivalToken, id, "54").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("55")));
        bid(rivalToken, id, "55").andExpect(status().isCreated());

        mvc.perform(get("/api/auctions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice").value(55.0))
                .andExpect(jsonPath("$.bidCount").value(2))
                .andExpect(jsonPath("$.minNextBid").value(60.0));

        mvc.perform(get("/api/auctions/" + id + "/bids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].amount").value(55.0));

        // an auction that already has bids can't be cancelled by the seller
        mvc.perform(delete("/api/auctions/" + id).header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isBadRequest());
        // and strangers can't cancel it either
        mvc.perform(delete("/api/auctions/" + id).header("Authorization", "Bearer " + bidderToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/users/me/bids").header("Authorization", "Bearer " + rivalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void sellerCanCancelAuctionWithoutBids() throws Exception {
        String sellerToken = register(uniq("canceller"));
        String created = mvc.perform(post("/api/auctions").header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"description\":\"y\",\"category\":\"z\",\"startingPrice\":1,\"minIncrement\":1,\"durationMinutes\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(created);
        mvc.perform(delete("/api/auctions/" + node.get("id").asLong()).header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void validationErrorsAreReadable() throws Exception {
        String token = register(uniq("val"));
        mvc.perform(post("/api/auctions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"d\",\"category\":\"c\",\"startingPrice\":-1,\"minIncrement\":1,\"durationMinutes\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    private org.springframework.test.web.servlet.ResultActions bid(String token, long id, String amount) throws Exception {
        return mvc.perform(post("/api/auctions/" + id + "/bids").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"));
    }
}
