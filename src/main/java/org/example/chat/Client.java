package org.example.chat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;

public class Client extends Application {
    private VBox chatBox;
    private TextField messageField;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;

    private final String serverIP = "192.168.0.108";
    private final int serverPort = 9806;

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(10);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 15; -fx-background-color: #fdf4ff;");

        ScrollPane chatScrollPane = new ScrollPane();
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setPrefHeight(400);
        chatScrollPane.setStyle("-fx-background: #ffffff; -fx-border-color: #fbcfe8; -fx-border-width: 1;");

        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        chatBox.setStyle("-fx-background-color: #ffffff;");
        chatScrollPane.setContent(chatBox);

        messageField = new TextField();
        messageField.setPromptText("Type a message...");
        messageField.setPrefWidth(360);
        messageField.setStyle("-fx-background-color: #ffffff; -fx-border-color: #f472b6; -fx-border-radius: 5;");

        Button sendButton = new Button("Send");
        Button attachButton = new Button("Attach File");

        styleButton(sendButton);
        styleButton(attachButton);

        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        attachButton.setOnAction(e -> attachFile());

        HBox inputRow = new HBox(10, messageField, sendButton, attachButton);
        inputRow.setAlignment(Pos.CENTER);
        inputRow.setPadding(new Insets(10));

        root.getChildren().addAll(chatScrollPane, inputRow);

        primaryStage.setScene(new Scene(root, 600, 520));
        primaryStage.setTitle("JavaFX Client Chat");
        primaryStage.show();

        new Thread(this::startClient).start();
    }

    private void styleButton(Button button) {
        button.setStyle("""
                -fx-background-color: #ec4899;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-background-radius: 6;
                -fx-cursor: hand;
                -fx-padding: 6 12;
                """);

        button.setOnMouseEntered(e -> button.setStyle("""
                -fx-background-color: #db2777;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-background-radius: 6;
                -fx-cursor: hand;
                -fx-padding: 6 12;
                """));

        button.setOnMouseExited(e -> styleButton(button));
    }

    private void startClient() {
        try {
            socket = new Socket(serverIP, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("[FILE]:")) {
                    String[] parts = message.split(":", 4);
                    String fileType = parts[1];
                    String fileName = parts[2];
                    String encodedData = parts[3];
                    Platform.runLater(() -> displayReceivedFile(fileName, encodedData, fileType));
                } else {
                    String finalMessage = "Server: " + message;
                    Platform.runLater(() -> displayMessage(finalMessage, Pos.CENTER_LEFT, "#fee2e2"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            displayMessage("You: " + message, Pos.CENTER_RIGHT, "#fbcfe8");
            messageField.clear();
            if (out != null) {
                out.println(message);
            }
        }
    }

    private void attachFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File");
        File file = fileChooser.showOpenDialog(null);

        if (file != null && out != null) {
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String encoded = Base64.getEncoder().encodeToString(fileBytes);
                String fileType = getFileType(file.getName());
                String message = "[FILE]:" + fileType + ":" + file.getName() + ":" + encoded;
                out.println(message);
                displaySentFile(file.getName(), encoded, fileType);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void displayMessage(String text, Pos alignment, String color) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setFont(new Font(14));
        label.setStyle("-fx-background-color: " + color + "; -fx-padding: 10; -fx-background-radius: 10;");
        HBox container = new HBox(label);
        container.setAlignment(alignment);
        chatBox.getChildren().add(container);
    }

    private void displaySentFile(String fileName, String encodedData, String fileType) {
        byte[] decodedBytes = Base64.getDecoder().decode(encodedData);
        if (fileType.equals("IMAGE")) {
            Image image = new Image(new ByteArrayInputStream(decodedBytes));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setPreserveRatio(true);
            Button downloadButton = new Button("Download Image");
            downloadButton.setOnAction(e -> saveFile(decodedBytes, fileName));
            VBox imageContainer = new VBox(5, new Label("Client sent: " + fileName), imageView, downloadButton);
            imageContainer.setStyle("-fx-padding: 8; -fx-background-color: #fce7f3; -fx-background-radius: 8;");
            HBox imageBox = new HBox(imageContainer);
            imageBox.setAlignment(Pos.CENTER_RIGHT);
            chatBox.getChildren().add(imageBox);
        } else if (fileType.equals("VIDEO")) {
            Button downloadButton = new Button("Download Video: " + fileName);
            downloadButton.setOnAction(e -> saveFile(decodedBytes, fileName));
            VBox box = new VBox(new Label("Client sent a video:"), downloadButton);
            box.setStyle("-fx-padding: 8; -fx-background-color: #fef3c7; -fx-background-radius: 8;");
            HBox container = new HBox(box);
            container.setAlignment(Pos.CENTER_RIGHT);
            chatBox.getChildren().add(container);
        } else {
            displayMessage("Client sent a file: " + fileName + " (" + decodedBytes.length / 1024 + " KB)", Pos.CENTER_RIGHT, "#fce7f3");
        }
    }

    private void displayReceivedFile(String fileName, String encodedData, String fileType) {
        byte[] decodedBytes = Base64.getDecoder().decode(encodedData);
        if (fileType.equals("IMAGE")) {
            Image image = new Image(new ByteArrayInputStream(decodedBytes));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setPreserveRatio(true);
            Button downloadButton = new Button("Download Image");
            downloadButton.setOnAction(e -> saveFile(decodedBytes, fileName));
            VBox imageContainer = new VBox(5, new Label("Server sent: " + fileName), imageView, downloadButton);
            imageContainer.setStyle("-fx-padding: 8; -fx-background-color: #fce7f3; -fx-background-radius: 8;");
            HBox imageBox = new HBox(imageContainer);
            imageBox.setAlignment(Pos.CENTER_LEFT);
            chatBox.getChildren().add(imageBox);
        } else if (fileType.equals("VIDEO")) {
            Button downloadButton = new Button("Download Video: " + fileName);
            downloadButton.setOnAction(e -> saveFile(decodedBytes, fileName));
            VBox box = new VBox(new Label("Server sent a video:"), downloadButton);
            box.setStyle("-fx-padding: 8; -fx-background-color: #fef3c7; -fx-background-radius: 8;");
            HBox container = new HBox(box);
            container.setAlignment(Pos.CENTER_LEFT);
            chatBox.getChildren().add(container);
        } else {
            displayMessage("Server sent a file: " + fileName + " (" + decodedBytes.length / 1024 + " KB)", Pos.CENTER_LEFT, "#e9d5ff");
        }
    }

    private String getFileType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) return "IMAGE";
        else if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".mkv")) return "VIDEO";
        else return "FILE";
    }

    private void saveFile(byte[] data, String originalName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        fileChooser.setInitialFileName(originalName);
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                Files.write(file.toPath(), data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
