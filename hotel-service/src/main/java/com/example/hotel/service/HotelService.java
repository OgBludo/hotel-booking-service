package com.example.hotel.service;

import com.example.hotel.model.Hotel;
import com.example.hotel.model.Room;
import com.example.hotel.model.RoomReservationLock;
import com.example.hotel.repo.HotelRepository;
import com.example.hotel.repo.RoomRepository;
import com.example.hotel.repo.RoomReservationLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class HotelService {
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationLockRepository lockRepository;

    public HotelService(HotelRepository hotelRepository, RoomRepository roomRepository, RoomReservationLockRepository lockRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.lockRepository = lockRepository;
    }

    public List<Hotel> listHotels() { return hotelRepository.findAll(); }
    public Optional<Hotel> getHotel(Long id) { return hotelRepository.findById(id); }
    public Hotel saveHotel(Hotel h) { return hotelRepository.save(h); }
    public void deleteHotel(Long id) { hotelRepository.deleteById(id); }

    public List<Room> listRooms() { return roomRepository.findAll(); }
    public Optional<Room> getRoom(Long id) { return roomRepository.findById(id); }
    public Room saveRoom(Room r) { return roomRepository.save(r); }
    public void deleteRoom(Long id) { roomRepository.deleteById(id); }

    @Transactional
    public RoomReservationLock holdRoom(String requestId, Long roomId, LocalDate startDate, LocalDate endDate) {
        Optional<RoomReservationLock> existing = lockRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return existing.get();
        }
        List<RoomReservationLock> conflicts = lockRepository
                .findByRoomIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        roomId,
                        Arrays.asList(RoomReservationLock.Status.HELD, RoomReservationLock.Status.CONFIRMED),
                        endDate,
                        startDate
                );
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Room unavailable for this dates");
        }
        RoomReservationLock lock = new RoomReservationLock();
        lock.setRequestId(requestId);
        lock.setRoomId(roomId);
        lock.setStartDate(startDate);
        lock.setEndDate(endDate);
        lock.setStatus(RoomReservationLock.Status.HELD);
        return lockRepository.save(lock);
    }

    @Transactional
    public RoomReservationLock confirmHold(String requestId) {
        RoomReservationLock lock = lockRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("Hold not found"));
        if (lock.getStatus() == RoomReservationLock.Status.CONFIRMED) {
            return lock;
        }
        if (lock.getStatus() == RoomReservationLock.Status.RELEASED) {
            throw new IllegalStateException("Reservation already released");
        }
        lock.setStatus(RoomReservationLock.Status.CONFIRMED);
        roomRepository.findById(lock.getRoomId()).ifPresent(room -> {
            room.setTimesBooked(room.getTimesBooked() + 1);
            roomRepository.save(room);
        });
        return lockRepository.save(lock);
    }

    @Transactional
    public RoomReservationLock releaseHold(String requestId) {
        RoomReservationLock lock = lockRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("Hold not found"));
        if (lock.getStatus() == RoomReservationLock.Status.RELEASED) {
            return lock;
        }
        if (lock.getStatus() == RoomReservationLock.Status.CONFIRMED) {
            return lock;
        }
        lock.setStatus(RoomReservationLock.Status.RELEASED);
        return lockRepository.save(lock);
    }
}


