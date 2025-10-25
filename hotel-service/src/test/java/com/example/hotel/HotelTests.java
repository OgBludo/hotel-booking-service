package com.example.hotel;

import com.example.hotel.model.Hotel;
import com.example.hotel.model.Room;
import com.example.hotel.model.RoomReservationLock;
import com.example.hotel.repo.HotelRepository;
import com.example.hotel.repo.RoomRepository;
import com.example.hotel.service.HotelService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
public class HotelTests {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private HotelService hotelService;

    @Test
    @Transactional
    void dateConflictReturns409LikeBehavior() {
        Hotel h = new Hotel();
        h.setName("Hotel Alpha");
        h.setCity("Metropolis");
        h = hotelRepository.save(h);

        Room r = new Room();
        r.setHotel(h);
        r.setNumber("301");
        r.setCapacity(2);
        r = hotelService.saveRoom(r);

        LocalDate s1 = LocalDate.now().plusDays(1);
        LocalDate e1 = s1.plusDays(2);
        hotelService.holdRoom("req-a", r.getId(), s1, e1);


        Assertions.assertThrows(IllegalStateException.class, () ->
                hotelService.holdRoom("req-b", r.getId(), s1.plusDays(1), e1.plusDays(1))
        );
    }

    @Test
    @Transactional
    void availableFlagDoesNotAffectDateOccupancy() {
        Hotel h = new Hotel();
        h.setName("Hotel Beta");
        h.setCity("Gotham");
        h = hotelRepository.save(h);

        Room r = new Room();
        r.setHotel(h);
        r.setNumber("302");
        r.setCapacity(2);
        r.setAvailable(false);
        r = hotelService.saveRoom(r);

        LocalDate s1 = LocalDate.now().plusDays(1);
        LocalDate e1 = s1.plusDays(1);
        RoomReservationLock lock = hotelService.holdRoom("req-c", r.getId(), s1, e1);
        Assertions.assertEquals(RoomReservationLock.Status.HELD, lock.getStatus());
    }

    @Test
    @Transactional
    void statsPopularRoomsOrder() {
        Hotel h = new Hotel();
        h.setName("Hotel Gamma");
        h.setCity("Star City");
        h = hotelRepository.save(h);

        Room r1 = new Room();
        r1.setHotel(h);
        r1.setNumber("401");
        r1.setCapacity(2);
        r1 = hotelService.saveRoom(r1);

        Room r2 = new Room();
        r2.setHotel(h);
        r2.setNumber("402");
        r2.setCapacity(2);
        r2 = hotelService.saveRoom(r2);


        hotelService.holdRoom("req-d", r1.getId(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        hotelService.confirmHold("req-d");

        hotelService.holdRoom("req-e", r1.getId(), LocalDate.now().plusDays(3), LocalDate.now().plusDays(4));
        hotelService.confirmHold("req-e");

        hotelService.holdRoom("req-f", r2.getId(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        hotelService.confirmHold("req-f");


        List<Room> rooms = roomRepository.findAll();
        rooms.sort((a, b) -> Long.compare(
                hotelService.countConfirmedBookings(b.getId()),
                hotelService.countConfirmedBookings(a.getId())
        ));

        Assertions.assertEquals(2, rooms.size());
        Assertions.assertEquals(r1.getId(), rooms.get(0).getId());
        Assertions.assertEquals(r2.getId(), rooms.get(1).getId());
    }
}
