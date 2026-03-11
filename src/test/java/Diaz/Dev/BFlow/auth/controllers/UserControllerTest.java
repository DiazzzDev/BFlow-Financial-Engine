package Diaz.Dev.BFlow.auth.controllers;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.enums.UserStatus;
import bflow.auth.services.UserService;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserController.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private bflow.auth.controllers.UserController userController;

    private UUID userId;
    private UserProfileResponse userProfile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userProfile = UserProfileResponse.builder()
                .id(userId)
                .email("user@example.com")
                .roles(Set.of("ROLE_USER"))
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void testGetCurrentUser() {
        // Arrange
        when(authentication.getName()).thenReturn(userId.toString());
        when(userService.getProfile(userId)).thenReturn(userProfile);
        when(request.getRequestURI()).thenReturn("/api/users/me");

        // Act
        ResponseEntity<ApiResponse<UserProfileResponse>> response =
                userController.getCurrentUser(authentication, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userProfile.getEmail(), response.getBody().getData().getEmail());
        verify(userService).getProfile(userId);
    }

    @Test
    void testUpdateProfile() {
        // Arrange
        UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest();
        updateRequest.setEmail("updated@example.com");

        UserProfileResponse updatedProfile = UserProfileResponse.builder()
                .id(userId)
                .email("updated@example.com")
                .roles(Set.of("ROLE_USER"))
                .status(UserStatus.ACTIVE)
                .build();

        when(authentication.getName()).thenReturn(userId.toString());
        when(userService.updateProfile(eq(userId), any(UpdateUserProfileRequest.class)))
                .thenReturn(updatedProfile);
        when(request.getRequestURI()).thenReturn("/api/users/me");

        // Act
        ResponseEntity<ApiResponse<UserProfileResponse>> response =
                userController.updateProfile(authentication, updateRequest, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(updatedProfile.getEmail(), response.getBody().getData().getEmail());
        verify(userService).updateProfile(eq(userId), any(UpdateUserProfileRequest.class));
    }

    @Test
    void testDeleteAccount() {
        // Arrange
        when(authentication.getName()).thenReturn(userId.toString());
        doNothing().when(userService).softDelete(userId);
        when(request.getRequestURI()).thenReturn("/api/users/me");

        // Act
        ResponseEntity<ApiResponse<Void>> response =
                userController.deleteAccount(authentication, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(userService).softDelete(userId);
    }
}
