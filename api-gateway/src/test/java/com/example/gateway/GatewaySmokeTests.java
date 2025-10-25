package com.example.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = GatewaySmokeTests.MockServerConfig.class)
class GatewaySmokeTests {

    static class MockServerConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        static final WireMockServer MOCK_SERVER = new WireMockServer(0);

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            MOCK_SERVER.start();
            int port = MOCK_SERVER.port();

            TestPropertyValues.of(
                    "spring.cloud.gateway.routes[0].id=mock-hotel",
                    "spring.cloud.gateway.routes[0].uri=http://localhost:" + port,
                    "spring.cloud.gateway.routes[0].predicates[0]=Path=/hotels/**",
                    "spring.cloud.gateway.routes[1].id=mock-booking",
                    "spring.cloud.gateway.routes[1].uri=http://localhost:" + port,
                    "spring.cloud.gateway.routes[1].predicates[0]=Path=/bookings/**"
            ).applyTo(context.getEnvironment());
        }
    }

    @Autowired
    private WebClient.Builder webClientBuilder;

    @BeforeEach
    void initMocks() {
        MockServerConfig.MOCK_SERVER.resetAll();

        MockServerConfig.MOCK_SERVER.stubFor(
                get("/hotels/test")
                        .withHeader("X-User-Token", matching("Token .*"))
                        .withHeader("X-Request-ID", matching(".*"))
                        .willReturn(okJson("{\"ok\":true}"))
        );
    }

    @AfterAll
    static void tearDown() {
        MockServerConfig.MOCK_SERVER.stop();
    }

    @Test
    void shouldForwardRoutesAndHeaders() {
        WebClient client = webClientBuilder.baseUrl("http://localhost:8080").build();

        String response = client.get()
                .uri("/hotels/test")
                .header("X-User-Token", "Token TokenJwt")
                .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        Assertions.assertTrue(response.contains("ok"));
    }
}
