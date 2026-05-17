package ru.gr0946x.net;

import ru.gr0946x.model.Message;
import ru.gr0946x.model.User;
import ru.gr0946x.repository.MessageRepository;
import ru.gr0946x.repository.UserRepository;

import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ConnectedClient {
    private final Communicator communicator;
    private final static List<ConnectedClient> clients = new ArrayList<>();
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    
    private String name = null;
    private String tempNickname = null;
    private boolean isWaitingForPassword = false;

    public ConnectedClient(Socket socket, UserRepository userRepository, MessageRepository messageRepository) throws IOException {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        communicator = new Communicator(socket);
        communicator.addDataListener(this::parseData);
        synchronized (clients) {
            clients.add(this);
        }
    }
    public void start(){
        communicator.start();
        requestNickname();
    }

    private void requestNickname() {
        sendData(MessageType.REQUEST
                + ProtocolConstants.COMMAND_SEPARATOR
                + "Введите имя (должно начинаться с буквы):");
    }

    private void requestPassword() {
        sendData(MessageType.REQUEST
                + ProtocolConstants.COMMAND_SEPARATOR
                + "Введите пароль:");
    }

    public void sendData(String data){
        communicator.sendData(data);
    }

    private void parseData(String data){
        if (name == null){
            handleAuth(data);
        } else {
            handleChatMessage(data);
        }
    }

    private void handleChatMessage(String data) {
        // Запрос на поиск? (протокол: "SEARCH:слово" или "SEARCH:@имя:слово")
        if (data.startsWith("SEARCH:")) {
            handleSearchRequest(data.substring(7));
            return;
        }

        // Личное сообщение? (например, "@Dima: привет")
        if (data.startsWith("@") && data.contains(":")) {
            int colonIndex = data.indexOf(":");
            String targetName = data.substring(1, colonIndex);
            String privateText = data.substring(colonIndex + 1).trim();
            sendPrivate(targetName, privateText);
        } else {
            // Сохраняем сообщение в базу (для общего чата receiver = null)
            saveMessageToDb(null, data);
            sendForAll(MessageType.MESSAGE, data);
        }
    }

    private void handleSearchRequest(String query) {
        String targetName = null;
        String searchText = query;

        if (query.startsWith("@") && query.contains(":")) {
            int colonIndex = query.indexOf(":");
            targetName = query.substring(1, colonIndex);
            searchText = query.substring(colonIndex + 1).trim();
        }

        List<Message> results;
        var me = userRepository.findByNicknameIgnoreCase(name).orElse(null);

        if (targetName == null) {
            // Поиск в общем чате
            results = messageRepository.findByReceiverIsNullAndTextContainingIgnoreCase(searchText);
        } else {
            // Поиск в ЛС с конкретным человеком
            var other = userRepository.findByNicknameIgnoreCase(targetName).orElse(null);
            if (other != null && me != null) {
                results = messageRepository.findByTextContainingIgnoreCaseAndSenderInAndReceiverIn(
                        searchText, List.of(me, other), List.of(me, other));
            } else {
                sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR + "Собеседник для поиска не найден");
                return;
            }
        }

        sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR + "--- Результаты поиска (" + searchText + ") ---");
        if (results.isEmpty()) {
            sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR + "Ничего не найдено");
        } else {
            results.forEach(msg -> {
                sendData(MessageType.MESSAGE 
                        + ProtocolConstants.COMMAND_SEPARATOR 
                        + "[Найдено] " + msg.getSender().getNickname() 
                        + ProtocolConstants.AUTHOR_SEPARATOR 
                        + msg.getText());
            });
        }
        sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR + "--------------------------------------");
    }

    private void saveMessageToDb(String receiverName, String text) {
        var sender = userRepository.findByNicknameIgnoreCase(name).orElse(null);
        if (sender != null) {
            Message msg = new Message();
            msg.setSender(sender);
            if (receiverName != null) {
                userRepository.findByNicknameIgnoreCase(receiverName).ifPresent(msg::setReceiver);
            }
            msg.setText(text);
            msg.setSentAt(LocalDateTime.now());
            msg.setReceived(true);
            messageRepository.save(msg);
        }
    }

    private void sendPrivate(String targetName, String text) {
        synchronized (clients) {
            var target = clients.stream()
                    .filter(c -> c.name != null && c.name.equalsIgnoreCase(targetName))
                    .findFirst();
            
            if (target.isPresent()) {
                saveMessageToDb(targetName, text);
                String fullData = MessageType.MESSAGE + ProtocolConstants.COMMAND_SEPARATOR 
                        + "[ЛС от " + name + "]" + ProtocolConstants.AUTHOR_SEPARATOR + text;
                target.get().sendData(fullData);
                // Отправителю тоже подтверждаем
                sendData(MessageType.MESSAGE + ProtocolConstants.COMMAND_SEPARATOR 
                        + "[ЛС для " + targetName + "]" + ProtocolConstants.AUTHOR_SEPARATOR + text);
            } else {
                sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR + "Пользователь " + targetName + " не найден");
            }
        }
    }

    private void handleAuth(String data) {
        if (!isWaitingForPassword) {
            // Проверка ника
            if (data.isBlank() || !Character.isLetter(data.charAt(0))) {
                sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR + "Имя должно начинаться с буквы!");
                requestNickname();
                return;
            }
            if (isOnline(data)) {
                sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR + "Этот пользователь уже в сети!");
                requestNickname();
                return;
            }
            tempNickname = data;
            isWaitingForPassword = true;
            requestPassword();
        } else {
            // Проверка пароля / Регистрация
            String password = data;
            var userOpt = userRepository.findByNicknameIgnoreCase(tempNickname);
            
            if (userOpt.isPresent()) {
                // Пользователь есть, проверяем пароль
                if (userOpt.get().getPassword().equals(password)) {
                    login(tempNickname);
                } else {
                    sendData(MessageType.ERROR + ProtocolConstants.COMMAND_SEPARATOR + "Неверный пароль!");
                    isWaitingForPassword = false;
                    requestNickname();
                }
            } else {
                // Пользователя нет, регистрируем
                User newUser = new User();
                newUser.setNickname(tempNickname);
                newUser.setPassword(password);
                userRepository.save(newUser);
                sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR + "Вы успешно зарегистрированы!");
                login(tempNickname);
            }
        }
    }

    private void login(String nickname) {
        this.name = nickname;
        this.isWaitingForPassword = false;
        
        // Показываем историю последних сообщений (п.5 задания)
        sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR + "--- Последние сообщения в чате ---");
        messageRepository.findTop20ByReceiverIsNullOrderBySentAtAsc().forEach(msg -> {
            sendData(MessageType.MESSAGE 
                    + ProtocolConstants.COMMAND_SEPARATOR 
                    + msg.getSender().getNickname() 
                    + ProtocolConstants.AUTHOR_SEPARATOR 
                    + msg.getText());
        });
        sendData(MessageType.INFO + ProtocolConstants.COMMAND_SEPARATOR + "----------------------------------");

        sendForAll(MessageType.INFO, "Пользователь " + name + " вошел в чат");
        broadcastUserList();
    }

    private void broadcastUserList() {
        synchronized (clients) {
            List<String> names = clients.stream()
                    .filter(c -> c.name != null)
                    .map(c -> c.name)
                    .toList();
            String data = String.join(",", names);
            clients.forEach(c -> c.sendData(MessageType.USERS + ProtocolConstants.COMMAND_SEPARATOR + data));
        }
    }

    private void sendForAll(MessageType type, String data){
        var author = (type == MessageType.MESSAGE) ?
                name + ProtocolConstants.AUTHOR_SEPARATOR :
                "";
        synchronized (clients) {
            clients.stream()
                    .filter(c -> c.name != null)
                    .forEach(client -> {
                        client.sendData(type
                                + ProtocolConstants.COMMAND_SEPARATOR
                                + author
                                + data);
                    });
        }
    }
    
    private boolean isOnline(String name){
        synchronized (clients) {
            return clients.stream()
                    .anyMatch(c -> c.name != null && c.name.equalsIgnoreCase(name));
        }
    }

    public void stop(){
        synchronized (clients) {
            clients.remove(this);
        }
        if (name != null) {
            sendForAll(MessageType.INFO, "Пользователь " + name + " покинул чат");
            broadcastUserList();
        }
        communicator.stop();
    }
}
