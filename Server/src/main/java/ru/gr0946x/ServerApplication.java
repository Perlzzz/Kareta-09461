package ru.gr0946x;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import ru.gr0946x.net.Server;
import ru.gr0946x.repository.MessageRepository;
import ru.gr0946x.repository.UserRepository;

@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(ServerApplication.class, args);
        UserRepository userRepository = context.getBean(UserRepository.class);
        MessageRepository messageRepository = context.getBean(MessageRepository.class);
        // Start the server socket listener
        Server server = new Server(9460, userRepository, messageRepository);
    }
}