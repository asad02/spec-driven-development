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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The business rules, with the repository mocked. No Spring context and no
 * database, so these run in milliseconds and fail for exactly one reason.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;

    private static UserEntity stored(String first, String last, String email, String phone) {
        UserEntity entity = new UserEntity(first, last, email, phone);
        entity.setId(UUID.randomUUID());
        return entity;
    }

    // ---------- create ----------

    @Test
    @DisplayName("create persists the submitted fields and returns the stored record")
    void createPersistsFields() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.create(
                new UserRequest("Jane", "Doe", "jane@example.com", "555-0100"));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(response.lastName()).isEqualTo("Doe");
    }

    @Test
    @DisplayName("create rejects an email another user already holds")
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserRequest("A", "B", "taken@example.com", null)))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a blank phone is stored as null, so an omitted phone clears it (FR-3)")
    void blankPhoneBecomesNull() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        userService.create(new UserRequest("A", "B", "a@example.com", "   "));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPhone()).isNull();
    }

    // ---------- update ----------

    @Test
    @DisplayName("update of an unknown id is a not-found, not a create")
    void updateUnknownIsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(id, new UserRequest("A", "B", "a@example.com", null)))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update overwrites every editable field and clears an omitted phone")
    void updateReplacesAllFields() {
        UserEntity existing = stored("Old", "Name", "old@example.com", "555-0000");
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        userService.update(existing.getId(), new UserRequest("New", "Person", "new@example.com", null));

        assertThat(existing.getFirstName()).isEqualTo("New");
        assertThat(existing.getLastName()).isEqualTo("Person");
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getPhone()).isNull();
    }

    @Test
    @DisplayName("update rejects an email that belongs to a different user")
    void updateRejectsOtherUsersEmail() {
        UserEntity existing = stored("A", "B", "mine@example.com", null);
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCase("theirs@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(existing.getId(),
                new UserRequest("A", "B", "theirs@example.com", null)))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("re-submitting a user's own email is allowed; the uniqueness check is skipped")
    void updateKeepsOwnEmail() {
        UserEntity existing = stored("A", "B", "mine@example.com", null);
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        userService.update(existing.getId(), new UserRequest("A", "C", "MINE@example.com", null));

        // Case-insensitive match on the caller's own address must not consult the index.
        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    // ---------- get ----------

    @Test
    @DisplayName("get of an unknown id is a not-found")
    void getUnknownIsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(id)).isInstanceOf(UserNotFoundException.class);
    }

    // ---------- list ----------

    @Test
    @DisplayName("list defaults to newest first, 20 per page")
    void listDefaults() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        PageResponse<UserResponse> page = userService.list(null, null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(page.sort()).isEqualTo("createdAt");
        assertThat(page.direction()).isEqualTo("desc");
    }

    @Test
    @DisplayName("paging bounds are enforced")
    void pagingBounds() {
        assertThatThrownBy(() -> userService.list(-1, null, null, null, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> userService.list(0, 0, null, null, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> userService.list(0, 101, null, null, null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    @DisplayName("an unknown sort field is rejected rather than passed to the query builder")
    void unknownSortRejected() {
        assertThatThrownBy(() -> userService.list(0, 20, "ssn", null, null))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> userService.list(0, 20, "createdAt", "sideways", null))
                .isInstanceOf(InvalidParameterException.class);

        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("a search term is lowercased and wrapped in wildcards before it reaches SQL")
    void searchTermIsNormalised() {
        when(userRepository.search(eq("%jane%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        userService.list(0, 20, null, null, "JANE");

        verify(userRepository).search(eq("%jane%"), any(Pageable.class));
    }

    @Test
    @DisplayName("a blank search term is treated as no filter at all")
    void blankSearchIsNoFilter() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        userService.list(0, 20, null, null, "   ");

        verify(userRepository).findAll(any(Pageable.class));
        verify(userRepository, never()).search(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("an over-long search term is rejected, not truncated")
    void overlongSearchRejected() {
        assertThatThrownBy(() -> userService.list(0, 20, null, null, "x".repeat(101)))
                .isInstanceOf(InvalidParameterException.class);
    }
}
