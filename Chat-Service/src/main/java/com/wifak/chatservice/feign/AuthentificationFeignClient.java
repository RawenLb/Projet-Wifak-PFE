package com.wifak.chatservice.feign;

import com.wifak.chatservice.config.FeignConfig;
import com.wifak.chatservice.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client vers authentification-service pour récupérer les utilisateurs
 * (contacts du chat : agents ↔ managers).
 */
@FeignClient(name = "authentification-service", path = "/api/admin", configuration = FeignConfig.class)
public interface AuthentificationFeignClient {

    @GetMapping("/users/{userId}")
    UserDTO getUserById(@PathVariable("userId") String userId);

    @GetMapping("/roles/{roleName}/users")
    List<UserDTO> getUsersByRole(@PathVariable("roleName") String roleName);
}
