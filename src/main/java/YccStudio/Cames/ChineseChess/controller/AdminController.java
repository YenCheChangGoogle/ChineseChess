package YccStudio.Cames.ChineseChess.controller;

import YccStudio.Cames.ChineseChess.model.User;
import YccStudio.Cames.ChineseChess.model.Room;
import YccStudio.Cames.ChineseChess.repository.UserRepository;
import YccStudio.Cames.ChineseChess.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/online-users")
    public List<User> getOnlineUsers() {
        // 改用 RoomService 的 WebSocket 記憶體追蹤，只回傳真正有連線的使用者
        List<String> onlineUsernames = roomService.getOnlineUsers();
        return userRepository.findAll().stream()
                .filter(u -> onlineUsernames.contains(u.getUsername()))
                .collect(Collectors.toList());
    }

    @PostMapping("/users/create")
    public ResponseEntity<?> createUser(@RequestBody User user) {
        try {
            if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "使用者名稱已存在"));
            }
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "建立使用者成功"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "建立失敗: " + e.getMessage()));
        }
    }

    @PostMapping("/users/update")
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        return userRepository.findById(user.getId())
                .map(existingUser -> {
                    if (user.getUsername() != null) existingUser.setUsername(user.getUsername());
                    if (user.getPassword() != null) existingUser.setPassword(user.getPassword());
                    if (user.getRole() != null) existingUser.setRole(user.getRole());
                    userRepository.save(existingUser);
                    return ResponseEntity.ok(Map.of("message", "更新使用者成功"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/delete")
    public ResponseEntity<?> deleteUser(@RequestParam Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    userRepository.delete(user);
                    return ResponseEntity.ok(Map.of("message", "刪除使用者成功"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rooms/close")
    public ResponseEntity<?> closeRoom(@RequestParam String roomId) {
        try {
            // 1. 清除房間玩家狀態 (後端邏輯)
            roomService.clearRoomPlayers(roomId);
            // 2. 強制通知前端玩家離開 (WebSocket 廣播)
            gameWebSocketHandler.forceCloseRoom(roomId);
            return ResponseEntity.ok(Map.of("message", "房間已強制關閉，玩家已全部移出"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "關閉失敗: " + e.getMessage()));
        }
    }

    @PostMapping("/rooms/delete")
    public ResponseEntity<?> deleteRoom(@RequestParam String roomId) {
        try {
            // 1. 強制踢出玩家並通知
            gameWebSocketHandler.forceDeleteRoom(roomId);
            // 2. 刪除後端房間資料
            roomService.deleteRoom(roomId);
            return ResponseEntity.ok(Map.of("message", "房間已刪除"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "刪除失敗: " + e.getMessage()));
        }
    }

    @PostMapping("/rooms/create-ai")
    public ResponseEntity<?> createAIRoom(
            @RequestParam String username,
            @RequestParam int color,
            @RequestParam String difficulty) {
        try {
            Room room = roomService.createAIRoom(username, color, difficulty);
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "AI 房間建立失敗: " + e.getMessage()));
        }
    }
}
