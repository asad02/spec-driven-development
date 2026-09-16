package com.example.users.unit;

import com.example.users.dto.PageResponse;
import com.example.users.dto.UserRequest;
import com.example.users.dto.UserResponse;
import com.example.users.entity.UserEntity;
import com.example.users.exception.DuplicateEmailException;
import com.example.users.exception.InvalidParameterException;
import com.example.users.exception.UserNotFoundException;
import com.example.users.repository.UserRepository;
import com.example.users.service.UserService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository);
    }

    private static UserEntity stored(String first, String last, String email, String phone) {
        UserEntity e = new UserEntity(first, last, email, phone);
        e.setId(UUID.randomUUID());
        e.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return e;
    }

    @Test
    @DisplayName("create persists the submitted fields and returns the stored record")
    void createPersists() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenAnswer(inv -> {
                    UserEntity e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return e;
                });

        UserResponse result = service.create(
                new UserRequest("Jane", "Doe", "jane@example.com", "+1 555 0100"));

        assertThat(result.firstName()).isEqualTo("Jane");
        assertThat(result.email()).isEqualTo("jane@example.com");
        assertThat(result.phone()).isEqualTo("+1 555 0100");
        assertThat(result.id()).isNotNull();
    }

    @Test
    @DisplayName("create rejects an email another user already holds")
    void createRejectsDuplicate() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new UserRequest("Jane", "Doe", "taken@example.com", null)))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a blank phone is stored as null, so an omitted phone clears it (FR-3)")
    void blankPhoneBecomesNull() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new UserRequest("Jane", "Doe", "jane@example.com", "   "));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPhone()).isNull();
    }

    @Test
    @DisplayName("update overwrites every editable field and clears an omitted phone")
    void updateReplacesFields() {
        UserEntity existing = stored("Jane", "Doe", "jane@example.com", "+1 555 0100");
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = service.update(existing.getId(),
                new UserRequest("Janet", "Roe", "jane@example.com", null));

        assertThat(result.firstName()).isEqualTo("Janet");
        assertThat(result.lastName()).isEqualTo("Roe");
        assertThat(result.phone()).isNull();
    }

    @Test
    @DisplayName("re-submitting a user's own email is allowed; the uniqueness check is skipped")
    void updateAllowsOwnEmail() {
        UserEntity existing = stored("Jane", "Doe", "Jane@Example.com", null);
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(existing.getId(), new UserRequest("Jane", "Doe", "jane@example.com", null));

        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("update rejects an email that belongs to a different user")
    void updateRejectsOtherUsersEmail() {
        UserEntity existing = stored("Jane", "Doe", "jane@example.com", null);
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCase("someone@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.update(existing.getId(),
                new UserRequest("Jane", "Doe", "someone@example.com", null)))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("update of an unknown id is a not-found, not a create")
    void updateUnknownId() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UserRequest("A", "B", "a@b.com", null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("get of an unknown id is a not-found")
    void getUnknownId() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("list defaults to newest first, 20 per page")
    void listDefaults() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(Page.of(List.of(), Pageable.from(0, 20), 0L));

        PageResponse<UserResponse> page = service.list(null, null, null, null, null);

        assertThat(page.sort()).isEqualTo("createdAt");
        assertThat(page.direction()).isEqualTo("desc");
        assertThat(page.size()).isEqualTo(20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderBy())
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.getProperty()).isEqualTo("createdAt");
                    assertThat(o.isAscending()).isFalse();
                });
    }

    @Test
    @DisplayName("a search term is lowercased and wrapped in wildcards before it reaches SQL")
    void listBuildsSearchPattern() {
        when(userRepository.search(anyString(), any(Pageable.class)))
                .thenReturn(Page.of(List.of(), Pageable.from(0, 20), 0L));

        service.list(0, 20, null, null, "  JaNe ");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).search(captor.capture(), any(Pageable.class));
        // The DTO layer trims; the service only lowercases and wraps.
        assertThat(captor.getValue()).isEqualTo("%  jane %");
    }

    @Test
    @DisplayName("a blank search term is treated as no filter at all")
    void blankSearchIsNoFilter() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(Page.of(List.of(), Pageable.from(0, 20), 0L));

        service.list(0, 20, null, null, "   ");

        verify(userRepository).findAll(any(Pageable.class));
        verify(userRepository, never()).search(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("an unknown sort field is rejected rather than passed to the query builder")
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> service.list(0, 20, "password", "asc", null))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("sort must be one of");
    }

    @Test
    @DisplayName("paging bounds are enforced")
    void rejectsBadPaging() {
        assertThatThrownBy(() -> service.list(-1, 20, null, null, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.list(0, 0, null, null, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.list(0, 101, null, null, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.list(0, 20, "email", "sideways", null))
                .isInstanceOf(InvalidParameterException.class);
    }
}
