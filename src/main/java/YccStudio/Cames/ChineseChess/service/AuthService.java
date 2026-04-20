package YccStudio.Cames.ChineseChess.service;

import YccStudio.Cames.ChineseChess.model.User;
import YccStudio.Cames.ChineseChess.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;

    public User register(String name, String password) {
        User user = new User();
        user.setUsername(name);
        user.setPassword(password);
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public Optional<User> login(String name, String password) {
        Optional<User> userOpt = userRepository.findByUsername(name);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            User user = userOpt.get();
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public void logout(User user) {
        user.setSessionId(null);
        userRepository.save(user);
    }
}
