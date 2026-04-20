package YccStudio.Cames.ChineseChess.controller;

import YccStudio.Cames.ChineseChess.model.Room;
import YccStudio.Cames.ChineseChess.service.RoomService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/create")
    public ResponseEntity<Room> createRoom(@RequestParam String username) {
        return ResponseEntity.ok(roomService.createRoom(username));
    }

    @PostMapping("/join")
    public ResponseEntity<Void> joinRoom(@RequestParam String roomId, @RequestParam String username) {
        Room room = roomService.joinRoom(roomId, username);
        if (room == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/list")
    public List<Room> listRooms() {
        return roomService.getAllRooms();
    }
}
