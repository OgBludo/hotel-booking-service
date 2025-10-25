package com.example.booking.service;

import com.example.booking.model.Booking;
import com.example.booking.repo.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final WebClient webClient;
    private final Duration timeout;
    private final int maxRetries;
    private final String hotelBaseUrl;

    public BookingService(
            BookingRepository bookingRepository,
            WebClient.Builder clientBuilder,
            @Value("${hotel.base-url}") String hotelBaseUrl,
            @Value("${hotel.timeout-ms}") int timeoutMs,
            @Value("${hotel.retries}") int retries
    ) {
        this.bookingRepository = bookingRepository;
        this.webClient = clientBuilder.baseUrl(hotelBaseUrl).build();
        this.hotelBaseUrl = hotelBaseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxRetries = retries;
    }

    @Transactional
    public Booking createBooking(Long userId, Long roomId, LocalDate start, LocalDate end, String requestId) {
        Booking foundBooking = bookingRepository.findByRequestId(requestId).orElse(null);
        if (foundBooking != null) return foundBooking;

        String correlationId = UUID.randomUUID().toString();
        Booking booking = initBooking(userId, roomId, start, end, requestId, correlationId);
        booking = bookingRepository.save(booking);

        log.info("Booking initialized with PENDING status, correlationId={}", correlationId);

        try {
            holdRoom(roomId, requestId, start, end, correlationId)
                    .then(confirmRoom(roomId, requestId, correlationId))
                    .block(timeout);

            booking.setStatus(Booking.Status.CONFIRMED);
            bookingRepository.save(booking);
            log.info("Booking CONFIRMED, correlationId={}", correlationId);

        } catch (Exception e) {
            log.warn("Booking process failed, correlationId={}, reason={}", correlationId, e.toString());
            releaseRoom(roomId, requestId, correlationId);
            booking.setStatus(Booking.Status.CANCELLED);
            bookingRepository.save(booking);
            log.info("Booking CANCELLED and room released, correlationId={}", correlationId);
        }

        return booking;
    }

    private Booking initBooking(Long userId, Long roomId, LocalDate start, LocalDate end, String requestId, String correlationId) {
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setRoomId(roomId);
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setRequestId(requestId);
        booking.setStatus(Booking.Status.PENDING);
        booking.setCorrelationId(correlationId);
        booking.setCreatedAt(OffsetDateTime.now());
        return booking;
    }

    private Mono<String> holdRoom(Long roomId, String requestId, LocalDate start, LocalDate end, String correlationId) {
        Map<String, String> payload = Map.of(
                "requestId", requestId,
                "startDate", start.toString(),
                "endDate", end.toString()
        );
        return callHotel("/rooms/" + roomId + "/hold", payload, correlationId);
    }

    private Mono<String> confirmRoom(Long roomId, String requestId, String correlationId) {
        return callHotel("/rooms/" + roomId + "/confirm", Map.of("requestId", requestId), correlationId);
    }

    private void releaseRoom(Long roomId, String requestId, String correlationId) {
        try {
            callHotel("/rooms/" + roomId + "/release", Map.of("requestId", requestId), correlationId)
                    .block(timeout);
        } catch (Exception ignored) {}
    }

    private Mono<String> callHotel(String path, Map<String, String> payload, String correlationId) {
        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(300)).maxBackoff(Duration.ofSeconds(2)));
    }

    public record RoomView(Long id, String number, long timesBooked) {}

    public Mono<java.util.List<RoomView>> getRoomSuggestions() {
        return webClient.get()
                .uri("/hotels/rooms")
                .retrieve()
                .bodyToFlux(RoomView.class)
                .collectList()
                .map(list -> list.stream()
                        .sorted(java.util.Comparator.comparingLong(RoomView::timesBooked)
                                .thenComparing(RoomView::id))
                        .toList());
    }
}
