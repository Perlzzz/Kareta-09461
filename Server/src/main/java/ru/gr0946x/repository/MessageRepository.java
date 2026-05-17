package ru.gr0946x.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.gr0946x.model.Message;
import ru.gr0946x.model.User;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    // Для получения последних сообщений (п.5 задания)
    List<Message> findTop20BySenderAndReceiverOrSenderAndReceiverOrderBySentAtAsc(
            User s1, User r1, User s2, User r2);
    
    // Для сообщений в общем чате
    List<Message> findTop20ByReceiverIsNullOrderBySentAtAsc();

    // Поиск по тексту в общем чате
    List<Message> findByReceiverIsNullAndTextContainingIgnoreCase(String text);

    // Поиск в личной переписке (п.10 задания)
    List<Message> findByTextContainingIgnoreCaseAndSenderInAndReceiverIn(
            String text, List<User> senders, List<User> receivers);
}