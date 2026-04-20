package YccStudio.Cames.ChineseChess.controller;

import YccStudio.Cames.ChineseChess.model.User;
import YccStudio.Cames.ChineseChess.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String password = request.get("password");
        authService.register(name, password);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String password = request.get("password");
        
        Optional<User> userOpt = authService.login(name, password);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Generate new session ID to prevent multi-device login
            String newSessionId = UUID.randomUUID().toString();
            user.setSessionId(newSessionId);
            // Note: authService.save(user) should be called here if needed
            
            return ResponseEntity.ok(Map.of(
                "sessionId", newSessionId,
                "name", user.getName(),
                "message", "Login successful"
            ));
        } else {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
        }
    }
}
