package com.example.users.service;

import com.example.users.dto.PageResponse;
import com.example.users.dto.UserRequest;
import com.example.users.dto.UserResponse;
import com.example.users.entity.UserEntity;
import com.example.users.exception.DuplicateEmailException;
import com.example.users.exception.InvalidParameterException;
import com.example.users.exception.UserNotFoundException;
import com.example.users.repository.UserRepository;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Singleton
public class UserService {

    /** Allow-list from API field name to entity property (technical spec §8.4). */
    private static final Map<String, String> SORTABLE = Map.of(
            "firstName", "firstName",
            "lastName", "lastName",
            "email", "email",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"
    );

    private static final Set<String> DIRECTIONS = Set.of("asc", "desc");

    public static final String DEFAULT_SORT = "createdAt";
    public static final String DEFAULT_DIRECTION = "desc";
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        String email = request.email();
        // A clean 409 for the common case; the unique index below is the real guarantee.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }
        UserEntity entity = new UserEntity(
                request.firstName(),
                request.lastName(),
                email,
                blankToNull(request.phone())
        );
        return UserResponse.from(persist(entity, email));
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        String email = request.email();
        // Re-submitting the user's own address is fine; taking someone else's is not.
        boolean emailChanged = !entity.getEmail().equalsIgnoreCase(email);
        if (emailChanged && userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }

        // Full replacement (FR-3): every editable field is overwritten, so an
        // absent phone clears the stored one.
        entity.setFirstName(request.firstName());
        entity.setLastName(request.lastName());
        entity.setEmail(email);
        entity.setPhone(blankToNull(request.phone()));

        return UserResponse.from(persist(entity, email));
    }

    @Transactional
    public UserResponse get(UUID id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public PageResponse<UserResponse> list(Integer page, Integer size, String sort, String direction, String search) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        String sortField = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort;
        String sortDirection = (direction == null || direction.isBlank()) ? DEFAULT_DIRECTION : direction.toLowerCase(Locale.ROOT);

        if (pageNumber < 0) {
            throw new InvalidParameterException("page", "page must be zero or greater.");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new InvalidParameterException("size", "size must be between 1 and " + MAX_SIZE + ".");
        }
        String property = SORTABLE.get(sortField);
        if (property == null) {
            throw new InvalidParameterException("sort",
                    "sort must be one of " + String.join(", ", SORTABLE.keySet().stream().sorted().toList()) + ".");
        }
        if (!DIRECTIONS.contains(sortDirection)) {
            throw new InvalidParameterException("direction", "direction must be asc or desc.");
        }
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new InvalidParameterException("search", "search must be at most " + MAX_SEARCH_LENGTH + " characters.");
        }

        Sort.Order order = "asc".equals(sortDirection)
                ? Sort.Order.asc(property)
                : Sort.Order.desc(property);
        Pageable pageable = Pageable.from(pageNumber, pageSize, Sort.of(order));

        String term = blankToNull(search);
        Page<UserEntity> result = term == null
                ? userRepository.findAll(pageable)
                : userRepository.search("%" + term.toLowerCase(Locale.ROOT) + "%", pageable);

        List<UserResponse> content = result.getContent().stream().map(UserResponse::from).toList();
        return new PageResponse<>(
                content,
                pageNumber,
                pageSize,
                result.getTotalSize(),
                result.getTotalPages(),
                sortField,
                sortDirection
        );
    }

    /**
     * Flushes inside the transaction so a unique-index violation surfaces here
     * rather than at commit, where it could no longer be mapped to a 409. This
     * is what closes the race two concurrent creates would otherwise win.
     */
    private UserEntity persist(UserEntity entity, String email) {
        try {
            return userRepository.saveAndFlush(entity);
        } catch (DataAccessException | jakarta.persistence.PersistenceException ex) {
            if (isUniqueViolation(ex)) {
                throw new DuplicateEmailException(email);
            }
            throw ex;
        }
    }

    private static boolean isUniqueViolation(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) {
                return true;
            }
            String message = t.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("ux_users_email_lower")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
