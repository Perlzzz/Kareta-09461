package ru.gr0946x.ui;

import ru.gr0946x.net.MessageType;
import ru.gr0946x.net.ProtocolConstants;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SwingUi extends JFrame implements Ui {

    private final List<Consumer<String>> listeners = new ArrayList<>();
    private JTextArea chatArea;
    private JTextField inputField;
    private JTextField searchField;
    private DefaultListModel<String> usersListModel;
    private JList<String> usersList;

    public SwingUi() {
        setTitle("Мессенджер Карета");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        init();
    }

    private void init() {
        // Главная панель
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Панель поиска сверху
        JPanel topPanel = new JPanel(new BorderLayout());
        searchField = new JTextField();
        searchField.setToolTipText("Введите слово для поиска...");
        JButton searchButton = new JButton("Поиск в истории");
        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());

        topPanel.add(new JLabel(" Поиск: "), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(searchButton, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Область чата
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        mainPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // Список пользователей справа
        usersListModel = new DefaultListModel<>();
        usersList = new JList<>(usersListModel);
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        usersList.setPreferredSize(new Dimension(150, 0));
        
        // При клике на пользователя в списке, подставляем @имя в поле ввода
        usersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = usersList.getSelectedValue();
                if (selected != null && !selected.isBlank()) {
                    inputField.setText("@" + selected + ": " + inputField.getText().replaceAll("^@.*?: ", ""));
                    inputField.requestFocus();
                }
            }
        });

        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.add(new JLabel("Онлайн:", SwingConstants.CENTER), BorderLayout.NORTH);
        usersPanel.add(new JScrollPane(usersList), BorderLayout.CENTER);
        
        JButton clearSelectionBtn = new JButton("Общий чат");
        clearSelectionBtn.addActionListener(e -> {
            usersList.clearSelection();
            inputField.setText(inputField.getText().replaceAll("^@.*?: ", ""));
        });
        usersPanel.add(clearSelectionBtn, BorderLayout.SOUTH);

        mainPanel.add(usersPanel, BorderLayout.EAST);

        // Панель ввода внизу
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(e -> sendMessage());
        JButton sendButton = new JButton("Отправить");
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        String prefix = "SEARCH:";
        String selected = usersList.getSelectedValue();
        if (selected != null) {
            prefix += "@" + selected + ":";
        }

        for (var listener : listeners) {
            listener.accept(prefix + query);
        }
    }

    private void sendMessage() {
        String text = inputField.getText();
        if (!text.isBlank()) {
            for (var listener : listeners) {
                listener.accept(text);
            }
            // Очищаем только текст сообщения, сохраняя @префикс если нужно отправить еще
            if (text.startsWith("@") && text.contains(":")) {
                int colonIndex = text.indexOf(":");
                inputField.setText(text.substring(0, colonIndex + 2));
            } else {
                inputField.setText("");
            }
        }
    }

    @Override
    public void start() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    @Override
    public void showInfo(String data, MessageType type) {
        SwingUtilities.invokeLater(() -> {
            switch (type) {
                case MESSAGE -> {
                    var message = data.split(ProtocolConstants.AUTHOR_SEPARATOR, 2);
                    if (message.length == 2) {
                        chatArea.append(message[0] + ": " + message[1] + "\n");
                    } else {
                        chatArea.append(data + "\n");
                    }
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }
                case ERROR -> {
                    JOptionPane.showMessageDialog(this, data, "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
                case INFO -> {
                    chatArea.append("[ИНФО] " + data + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }
                case USERS -> {
                    usersListModel.clear();
                    String[] names = data.split(",");
                    for (String name : names) {
                        if (!name.isBlank()) {
                            usersListModel.addElement(name);
                        }
                    }
                }
                case REQUEST -> {
                    // Если сервер просит данные (имя или пароль)
                    String input = JOptionPane.showInputDialog(this, data);
                    if (input != null) {
                        for (var listener : listeners) {
                            listener.accept(input);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void addUserDataListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUserDataListener(Consumer<String> listener) {
        listeners.remove(listener);
    }
}