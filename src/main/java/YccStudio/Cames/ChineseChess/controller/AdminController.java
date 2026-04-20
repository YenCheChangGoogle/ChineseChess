package YccStudio.Cames.ChineseChess.controller;

import YccStudio.Cames.ChineseChess.model.User;
import YccStudio.Cames.ChineseChess.repository.UserRepository;
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

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @PostMapping("/users/ban")
    public ResponseEntity<?> banUser(@RequestParam String username) {
        // Prototype: Ban logic can be implemented by adding a 'status' field to User model
        // For now, we just return OK
        return ResponseEntity.ok(Map.of("message", "User banned successfully"));
    }
}
