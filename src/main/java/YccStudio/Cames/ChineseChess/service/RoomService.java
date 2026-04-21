package YccStudio.Cames.ChineseChess.service;

import YccStudio.Cames.ChineseChess.model.Room;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userToRoom = new ConcurrentHashMap<>();
    private final ChessGameService chessGameService;

    public RoomService(ChessGameService chessGameService) {
        this.chessGameService = chessGameService;
    }

    public Room createRoom(String ownerUsername) {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        Room room = new Room();
        room.setRoomId(roomId);
        room.setOwner(ownerUsername);
        room.setStatus("WAITING");
        rooms.put(roomId, room);
        
        // Initialize the game board when the room is created
        chessGameService.createGame(roomId);
        
        return room;
    }

    public Room joinRoom(String roomId, String username) {
        Room room = rooms.get(roomId);
        if (room == null) return null;
        if ("FULL".equals(room.getStatus())) return null;

        // Assign Role: First one is Red (1), Second is Black (-1)
        // This is a simplified assignment for the prototype
        if (room.getPlayers() == null) {
            room.setPlayers(new ArrayList<>());
        }
        
        if (!room.getPlayers().contains(username)) {
            room.getPlayers().add(username);
        }

        if (room.getPlayers().size() >= 2) {
            room.setStatus("PLAYING");
        }
        
        userToRoom.put(username, roomId);
        return room;
    }

    public String leaveRoom(String username) {
        String roomId = userToRoom.remove(username);
        if (roomId == null) return null;

        Room room = rooms.get(roomId);
        if (room == null) return null;

        boolean wasPlaying = "PLAYING".equals(room.getStatus());
        
        if (room.getPlayers() != null) {
            room.getPlayers().remove(username);
        }

        if (room.getPlayers().size() < 2) {
            room.setStatus("WAITING");
        }

        return wasPlaying ? roomId : null; // Return roomId if a game was interrupted
    }

    public String getRoomIdByUsername(String username) {
        return userToRoom.get(username);
    }

    public int getPlayerColor(String username) {
        String roomId = userToRoom.get(username);
        if (roomId == null) return 0;
        Room room = rooms.get(roomId);
        if (room == null || room.getPlayers() == null) return 0;
        
        int index = room.getPlayers().indexOf(username);
        if (index == 0) return 1;  // First player = Red
        if (index == 1) return -1; // Second player = Black
        return 0;
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }
}
