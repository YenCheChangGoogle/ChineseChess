package YccStudio.Cames.ChineseChess.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.admin.username}")
    private String adminUsername;

    @Value("${security.admin.password}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/index", 
                    "/lobby", 
                    "/game", 
                    "/admin",
                    "/api/auth/**", 
                    "/api/rooms/**", 
                    "/api/admin/**",
                    "/css/**", 
                    "/js/**", 
                    "/game-ws/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.loginPage("/").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/index").permitAll());
        
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.withDefaultPasswordEncoder()
                .username(adminUsername)
                .password(adminPassword)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
