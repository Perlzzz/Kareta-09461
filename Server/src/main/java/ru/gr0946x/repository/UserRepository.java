package ru.gr0946x.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.gr0946x.model.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByNicknameIgnoreCase(String nickname);
}