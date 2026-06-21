package YccStudio.Cames.ChineseChess.service;

import YccStudio.Cames.ChineseChess.model.Room;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RoomService {
    
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userToRoom = new ConcurrentHashMap<>();
    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();
    private final ChessGameService chessGameService;
    
    /**
     * 【修正】獨立維護房間玩家清單，不依賴 JPA Entity 的 players 集合
     * （避免 Hibernate lazy loading 從資料庫覆蓋記憶體中的狀態）
     */
    private final Map<String, List<String>> roomPlayers = new ConcurrentHashMap<>();

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

        // 【新增】若房間只剩 0 人，返回 roomId 以便觸發棋盤歸零
        // 【既有】若遊戲中斷（原為 PLAYING），也返回 roomId
        if (room.getPlayers().isEmpty() || wasPlaying) {
            return roomId;
        }
        return null;
    }

    public void clearRoomPlayers(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null || room.getPlayers() == null) return;
        
        // 將房間中所有玩家從 userToRoom 中移除
        for (String player : room.getPlayers()) {
            userToRoom.remove(player);
        }
        room.getPlayers().clear();
        room.setStatus("WAITING");
    }

    public void deleteRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room != null && room.getPlayers() != null) {
            for (String player : room.getPlayers()) {
                userToRoom.remove(player);
            }
        }
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

    public void markOnline(String username) {
        onlineUsers.add(username);
    }

    public void markOffline(String username) {
        onlineUsers.remove(username);
    }

    public List<String> getOnlineUsers() {
        return new ArrayList<>(onlineUsers);
    }

    public boolean isOnline(String username) {
        return onlineUsers.contains(username);
    }

    /**
     * 建立 AI 對戰房間
     * @param username 玩家帳號
     * @param playerColor 玩家選擇的顏色 (1=紅, -1=黑)
     * @param difficulty AI 難度 (easy, normal, hard)
     */
    public Room createAIRoom(String username, int playerColor, String difficulty) {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        Room room = new Room();
        room.setRoomId(roomId);
        room.setOwner(username);
        room.setStatus("PLAYING");
        room.setAI(true);
        room.setAiDifficulty(difficulty);

        // 玩家選紅則玩家在 index 0, AI 在 index 1
        // 玩家選黑則 AI 在 index 0 (代表紅), 玩家在 index 1
        List<String> players = new ArrayList<>();
        if (playerColor == 1) {
            players.add(username);
            players.add("AI");
        } else {
            players.add("AI");
            players.add(username);
        }
        room.setPlayers(players);

        rooms.put(roomId, room);
        userToRoom.put(username, roomId);

        // 初始化棋盤遊戲
        chessGameService.createGame(roomId);

        return room;
    }

    /**
     * 判斷該房間是否為 AI 對戰房間
     */
    public boolean isAIRoom(String roomId) {
        Room room = rooms.get(roomId);
        return room != null && room.isAI();
    }

    /**
     * 獲取指定房間的 AI 難度
     */
    public String getAiDifficulty(String roomId) {
        Room room = rooms.get(roomId);
        return (room != null) ? room.getAiDifficulty() : "normal";
    }
}

