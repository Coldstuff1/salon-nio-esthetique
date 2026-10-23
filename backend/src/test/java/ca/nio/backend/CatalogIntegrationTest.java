package ca.nio.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ca.nio.backend.model.Product;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;

/**
 * Over HTTP against a real Postgres: the public service and product summaries, admin employee management, client profiles.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ServiceTypeRepository serviceTypes;
    @Autowired
    ProductRepository products;

    JwtRequestPostProcessor admin = keycloakJwt(UUID.randomUUID(), "admin");

    @Test
    void summarisesPricesAcrossEmployeesWhoOfferTheService() throws Exception {
        ServiceType waxing = serviceTypes.save(new ServiceType("Waxing " + UUID.randomUUID(), null, "wax"));
        ServiceType unoffered = serviceTypes.save(new ServiceType("Lashes " + UUID.randomUUID(), null, "eyes"));
        UUID cheap = UUID.randomUUID();
        UUID pricey = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        offer(cheap, waxing, 1500, 20);
        offer(pricey, waxing, 4000, 45);
        offer(inactive, waxing, 9900, 90);
        mockMvc.perform(delete("/api/admin/employees/" + inactive).with(admin)).andExpect(status().isNoContent());

        String path = "$[?(@.serviceTypeId == '" + waxing.getId() + "')]";
        mockMvc.perform(get("/api/public/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".minPriceCents").value(1500))
                .andExpect(jsonPath(path + ".maxPriceCents").value(4000))
                .andExpect(jsonPath(path + ".minDurationMinutes").value(20))
                .andExpect(jsonPath(path + ".maxDurationMinutes").value(45))
                .andExpect(jsonPath(path + ".employeeIds.length()").value(2))
                .andExpect(jsonPath("$[?(@.serviceTypeId == '" + unoffered.getId() + "')].minPriceCents")
                        .value((Object) null))
                .andExpect(jsonPath("$[?(@.serviceTypeId == '" + unoffered.getId() + "')].employeeIds.length()")
                        .value(0));
    }

    @Test
    void summarisesProductPricesWithoutRevealingCost() throws Exception {
        Product serum = products.save(new Product("Serum " + UUID.randomUUID(), null, "SE-" + UUID.randomUUID(), 900));
        UUID soldOut = UUID.randomUUID();
        UUID stocked = UUID.randomUUID();
        sell(soldOut, serum, 3000, 0);
        sell(stocked, serum, 3500, 4);

        String path = "$[?(@.productId == '" + serum.getId() + "')]";
        mockMvc.perform(get("/api/public/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".minPriceCents").value(3000))
                .andExpect(jsonPath(path + ".maxPriceCents").value(3500))
                .andExpect(jsonPath(path + ".inStock").value(true))
                .andExpect(jsonPath(path + ".employeeIds.length()").value(2))
                .andExpect(jsonPath("$[*].costCents").isEmpty())
                .andExpect(jsonPath("$[*].sku").isEmpty());
    }

    @Test
    void adminsSeeEditAndReactivateEmployees() throws Exception {
        UUID id = UUID.randomUUID();
        send(post("/api/admin/employees"), admin, Map.of("id", id, "displayName", "Nio"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(delete("/api/admin/employees/" + id).with(admin)).andExpect(status().isNoContent());

        String path = "$[?(@.id == '" + id + "')]";
        mockMvc.perform(get("/api/admin/employees").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".active").value(false));
        mockMvc.perform(get("/api/public/employees"))
                .andExpect(jsonPath(path).isEmpty());

        send(put("/api/admin/employees/" + id), admin, Map.of("displayName", "Nio B.", "bio", "Esthéticienne",
                "active", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nio B."))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/public/employees"))
                .andExpect(jsonPath(path + ".bio").value("Esthéticienne"));
    }

    @Test
    void clientsKeepTheirOwnProfile() throws Exception {
        JwtRequestPostProcessor client = keycloakJwt(UUID.randomUUID(), "client");

        mockMvc.perform(get("/api/me/profile").with(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
        send(put("/api/me/profile"), client, Map.of("displayName", "Marie", "phone", "not a phone"))
                .andExpect(status().isBadRequest());
        send(put("/api/me/profile"), client, Map.of("displayName", "Marie", "phone", "+1 (514) 555-0100"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/profile").with(client))
                .andExpect(jsonPath("$.displayName").value("Marie"))
                .andExpect(jsonPath("$.phone").value("+1 (514) 555-0100"));
        mockMvc.perform(get("/api/me/profile").with(keycloakJwt(UUID.randomUUID(), "employee")))
                .andExpect(status().isForbidden());
    }

    private void offer(UUID employeeId, ServiceType type, int priceCents, int durationMinutes) throws Exception {
        send(put("/api/staff/me/service-offerings/" + type.getId()), keycloakJwt(employeeId, "employee"),
                Map.of("priceCents", priceCents, "durationMinutes", durationMinutes))
                .andExpect(status().isOk());
    }

    private void sell(UUID employeeId, Product product, int priceCents, int quantityOnHand) throws Exception {
        send(put("/api/staff/me/product-offerings/" + product.getId()), keycloakJwt(employeeId, "employee"),
                Map.of("priceCents", priceCents, "quantityOnHand", quantityOnHand))
                .andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, JwtRequestPostProcessor auth, Object body)
            throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json(body)).with(auth));
    }

    private static JwtRequestPostProcessor keycloakJwt(UUID subject, String role) {
        return jwt()
                .jwt(token -> token
                        .subject(subject.toString())
                        .claim("preferred_username", role + "-" + subject)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

    private static String json(Object body) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body);
    }

}
