package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ca.nio.backend.model.Product;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;

/**
 * End-to-end over HTTP against a real Postgres: employee setup, quoting, booking, overlap and stock guards.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ServiceTypeRepository serviceTypes;
    @Autowired
    ProductRepository products;
    @Autowired
    EmployeeRepository employees;

    UUID employeeId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    ServiceType facial;
    Product cream;
    LocalDate monday = LocalDate.now(ZONE).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUpEmployee() throws Exception {
        facial = serviceTypes.save(new ServiceType("Facial", null, "face"));
        cream = products.save(new Product("Cream", null, "CR-" + UUID.randomUUID()));

        send(put("/api/staff/me/availability"), employee(), List.of(
                Map.of("dayOfWeek", 1, "startTime", "09:00", "endTime", "12:00")))
                .andExpect(status().isOk());
        send(put("/api/staff/me/service-offerings/" + facial.getId()), employee(),
                Map.of("priceCents", 8000, "durationMinutes", 60))
                .andExpect(status().isOk());
        send(put("/api/staff/me/product-offerings/" + cream.getId()), employee(),
                Map.of("priceCents", 2500, "quantityOnHand", 3))
                .andExpect(status().isOk());
    }

    @Test
    void quotesAndBooksWithTheEmployeesPrices() throws Exception {
        send(post("/api/public/quote"), null, Map.of("employeeId", employeeId, "serviceIds", List.of(facial.getId()),
                "products", List.of(Map.of("productId", cream.getId(), "quantity", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalCents").value(13000))
                .andExpect(jsonPath("$.totalDurationMinutes").value(60));

        mockMvc.perform(get("/api/public/employees/" + employeeId + "/slots")
                        .param("date", monday.toString()).param("serviceIds", facial.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));

        book(at(10, 0), 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotalCents").value(13000))
                .andExpect(jsonPath("$.endsAt").value(at(11, 0).toString()))
                .andExpect(jsonPath("$.services[0].nameSnapshot").value("Facial"));

        mockMvc.perform(get("/api/bookings/mine").with(customer()))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/staff/me/schedule").with(employee())
                        .param("from", at(0, 0).toString()).param("to", at(23, 0).toString()))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(stock()).isEqualTo(1);
    }

    @Test
    void rejectsOverlappingBooking() throws Exception {
        book(at(9, 0), 0).andExpect(status().isCreated());

        book(at(9, 30), 0).andExpect(status().isConflict());
    }

    @Test
    void rejectsBookingOutsideWorkingHours() throws Exception {
        book(at(11, 30), 0).andExpect(status().isConflict());
    }

    @Test
    void rejectsMoreStockThanOnHandAndRestoresStockOnCancel() throws Exception {
        book(at(9, 0), 4).andExpect(status().isConflict());

        String id = com.jayway.jsonpath.JsonPath.read(book(at(9, 0), 3)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(stock()).isZero();

        mockMvc.perform(post("/api/bookings/" + id + "/cancel").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stock()).isEqualTo(3);

        // the freed slot can be booked again
        book(at(9, 0), 0).andExpect(status().isCreated());
    }

    @Test
    void clientsCannotUseStaffEndpoints() throws Exception {
        mockMvc.perform(get("/api/staff/me").with(customer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeesOnlySeeTheirOwnSchedule() throws Exception {
        book(at(9, 0), 0).andExpect(status().isCreated());

        mockMvc.perform(get("/api/staff/me/schedule").with(keycloakJwt(UUID.randomUUID(), "employee"))
                        .param("from", at(0, 0).toString()).param("to", at(23, 0).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions book(Instant startsAt, int creams) throws Exception {
        List<Map<String, Object>> selection = creams == 0 ? List.of()
                : List.of(Map.of("productId", cream.getId(), "quantity", creams));
        return send(post("/api/bookings"), customer(), Map.of("employeeId", employeeId, "startsAt", startsAt,
                "serviceIds", List.of(facial.getId()), "products", selection));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               JwtRequestPostProcessor auth, Object body) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        return mockMvc.perform(auth == null ? request : request.with(auth));
    }

    private int stock() {
        return employees.findById(employeeId).orElseThrow().productOffering(cream.getId()).orElseThrow()
                .quantityOnHand();
    }

    private Instant at(int hour, int minute) {
        return ZonedDateTime.of(monday, LocalTime.of(hour, minute), ZONE).toInstant();
    }

    private JwtRequestPostProcessor employee() {
        return keycloakJwt(employeeId, "employee");
    }

    private JwtRequestPostProcessor customer() {
        return keycloakJwt(customerId, "client");
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
