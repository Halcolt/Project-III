package com.example.cafe_management;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CheckStockController {

    @FXML
    private AnchorPane rootPane;

    @FXML
    private Label itemNameLabel;

    @FXML
    private ComboBox<String> ItemComboBox;

    @FXML
    private TextField old_amountTextfield;

    @FXML
    private ListView<String> cartListView;

    @FXML
    private ComboBox<String> unitComboBox;

    @FXML
    private TextField unitTextfield;

    @FXML
    private TextField new_amountTextfield;

    @FXML
    private Button confirmButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button backButton;

    private Connection connection;

    private PreparedStatement fetchAmountStatement;
    private PreparedStatement updateStatement;
    private PreparedStatement insertStatement;

    private String unit;

    @FXML
    private void initialize() {
        try {
            connection = DatabaseUtil.connect();
            if (connection != null) {
                setupComboBox();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void setupComboBox() {
        ObservableList<String> stockIngredients = FXCollections.observableArrayList(fetchIngredientStock());
        ItemComboBox.setItems(stockIngredients);

        ItemComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                String unit = fetchUnitForItem(newValue);
                unitTextfield.setText(unit);

                // Fetch and display the amount from the stock table
                float stockAmount = fetchAmountForItem(newValue);
                old_amountTextfield.setText(String.valueOf(stockAmount));
            }
        });
    }

    // Add this method to fetch the amount for the selected item
    private float fetchAmountForItem(String item) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT amount FROM stock WHERE ingredient = ?")) {
            statement.setString(1, item);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getFloat("amount");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0f; // Default value if not found
    }

    private List<String> fetchIngredientStock() {
        List<String> stockIngredients = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement("SELECT ingredient FROM stock");
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String menuItem = resultSet.getString("ingredient");
                stockIngredients.add(menuItem);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stockIngredients;
    }

    private String fetchUnitForItem(String item) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT unit FROM stock WHERE ingredient = ?")) {
            statement.setString(1, item);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getString("unit");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    @FXML
    private void returnButtonClicked() {
        Stage stage = (Stage) ItemComboBox.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void AddToCart() {
        String selectedItem = ItemComboBox.getValue();
        unit = unitTextfield.getText();
        float oldAmount = Float.parseFloat(old_amountTextfield.getText());
        float newAmount = Float.parseFloat(new_amountTextfield.getText());

        if (selectedItem != null && !selectedItem.isEmpty() && oldAmount >= 0 && newAmount >= 0) {
            String cartItem = selectedItem + " - Old Amount: " + oldAmount + " " + unit + " - New Amount: " + newAmount + " " + unit;
            cartListView.getItems().add(cartItem);
            clearInputFields();
        } else {
            showInputErrorAlert();
        }
    }

    private void clearInputFields() {
        ItemComboBox.setValue(null);
        unitTextfield.clear();
        old_amountTextfield.clear();
        new_amountTextfield.clear();
    }

    private void showInputErrorAlert() {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Lỗi");
        errorAlert.setContentText("Vui lòng điền đầy đủ thông tin.");
        errorAlert.showAndWait();
    }

    @FXML
    private void handleDelete() {
        int selectedIndex = cartListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            cartListView.getItems().remove(selectedIndex);
        } else {
            showNoItemSelectedAlert();
        }
    }

    private void showNoItemSelectedAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText("Vui lòng chọn một mục để xóa.");
        alert.showAndWait();
    }

    @FXML
    private void handleConfirm() {
        java.util.Date currentDate = new java.util.Date();
        java.sql.Date sqlDate = new java.sql.Date(currentDate.getTime());
        java.sql.Time sqlTime = new java.sql.Time(currentDate.getTime());

        String username = LoginController.loggedInUserData.getUsername();

        for (String cartItem : cartListView.getItems()) {
            String[] parts = cartItem.split(" - ");
            if (parts.length == 3) {
                String ingredient = parts[0];
                String oldAmountStr = parts[1].replace("Old Amount: ", "").split(" ")[0];
                String newAmountStr = parts[2].replace("New Amount: ", "").split(" ")[0];
                float oldAmount = Float.parseFloat(oldAmountStr);
                float newAmount = Float.parseFloat(newAmountStr);

                // Insert into stock_change with quantity as NULL and price as 0
                insertStockChange(username, sqlDate, sqlTime, ingredient, unit, oldAmount, newAmount);

                // Update the amount in the stock table
                updateStockTable(ingredient, newAmount);
            }
        }
        cartListView.getItems().clear();
        showConfirmationAlert();
    }

    // Define the insertStockChange method to insert data into stock_change table
    private void insertStockChange(String username, java.sql.Date date, java.sql.Time time, String ingredient, String unit, float oldAmount, float newAmount) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO stock_change (username, changedate, changetime, ingredient, unit, old_amount, new_amount, quantity, price) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, username);
            statement.setDate(2, date);
            statement.setTime(3, time);
            statement.setString(4, ingredient);
            statement.setString(5, unit);
            statement.setFloat(6, oldAmount);
            statement.setFloat(7, newAmount);
            statement.setFloat(8, 0);
            statement.setFloat(9, 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Define the updateStockTable method to update the amount in the stock table
    private void updateStockTable(String ingredient, float newAmount) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE stock SET amount = ? WHERE ingredient = ?")) {
            statement.setFloat(1, newAmount);
            statement.setString(2, ingredient);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showConfirmationAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Xác nhận");
        alert.setHeaderText(null);
        alert.setContentText("Cập nhật kho hàng thành công.");
        alert.showAndWait();
    }
}