package ru.gr0946x.net;

import ru.gr0946x.repository.MessageRepository;
import ru.gr0946x.repository.UserRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;

public class Server {

    private boolean isActive;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public Server(int port, UserRepository userRepository, MessageRepository messageRepository){
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        isActive = true;
        new Thread(()->{
            try (var serverSocket = new ServerSocket(port)) {
                System.out.println("Сервер запущен");
                while (isActive) {
                    try{
                        var socket = serverSocket.accept();
                        System.out.println("Клиент подключен");
                        var connClient = new ConnectedClient(socket, userRepository, messageRepository);
                        connClient.start();
                    } catch (Exception e) {
                        System.out.println("Ошибка подключения клиентов...");
                        System.out.println(e.getMessage());
                        isActive = false;
                    }
                }
            } catch (IOException e) {
                System.out.println("Ошибка включения сервера");
            }
        }).start();
    }
}
