package Diaz.Dev.BFlow.auth.entities;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserEntityTest {

    @Test
    void testUserEntityCreation() {

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setRoles(Set.of("ROLE_USER"));
        user.setStatus(UserStatus.ACTIVE);

        assertNotNull(user.getId());

        assertEquals(
                "test@example.com",
                user.getEmail()
        );

        assertEquals(
                UserStatus.ACTIVE,
                user.getStatus()
        );

        assertTrue(
                user.getRoles().contains("ROLE_USER")
        );
    }

    @Test
    void testUserDefaultRoles() {

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .build();

        assertNotNull(user.getRoles());
    }
}