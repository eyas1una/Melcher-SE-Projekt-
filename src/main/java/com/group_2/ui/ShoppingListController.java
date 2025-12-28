package com.group_2.ui;

import com.group_2.service.ShoppingListService;
import com.group_2.util.SessionManager;
import com.model.ShoppingList;
import com.model.ShoppingListItem;
import com.model.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.fxml.FXML;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the shopping list view.
 * Manages shopping lists and their items.
 */
@Component
public class ShoppingListController extends Controller {

    private final ShoppingListService shoppingListService;
    private final SessionManager sessionManager;

    // Left panel - lists
    @FXML
    private VBox listsContainer;

    // Right panel - selected list
    @FXML
    private VBox noListSelectedView;
    @FXML
    private VBox listDetailsView;
    @FXML
    private Text selectedListName;
    @FXML
    private Text selectedListCreator;
    @FXML
    private StackPane sharedBadge;
    @FXML
    private TextField newItemField;
    @FXML
    private VBox itemsContainer;
    @FXML
    private Text itemCountText;
    @FXML
    private Button deleteListButton;
    @FXML
    private VBox boughtItemsContainer;
    @FXML
    private Text boughtCountText;
    @FXML
    private VBox boughtSection;

    // Navbar
    @FXML
    private NavbarController navbarController;

    private ShoppingList selectedList;

    public ShoppingListController(ShoppingListService shoppingListService, SessionManager sessionManager) {
        this.shoppingListService = shoppingListService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setTitle("🛒 Shopping Lists");
        }
        loadLists();
    }

    private void loadLists() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        listsContainer.getChildren().clear();
        List<ShoppingList> lists = shoppingListService.getAccessibleLists(currentUser);

        if (lists.isEmpty()) {
            VBox emptyState = new VBox(10);
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(30));
            Text emptyIcon = new Text("📝");
            emptyIcon.setStyle("-fx-font-size: 32px;");
            Text emptyText = new Text("Create your first list!");
            emptyText.getStyleClass().add("card-subtitle");
            emptyText.setStyle("-fx-text-alignment: center;");
            emptyState.getChildren().addAll(emptyIcon, emptyText);
            listsContainer.getChildren().add(emptyState);
        } else {
            for (ShoppingList list : lists) {
                listsContainer.getChildren().add(createListCard(list));
            }
            // Auto-select first list if none is selected
            if (selectedList == null) {
                selectList(lists.get(0));
            }
        }
    }

    private HBox createListCard(ShoppingList list) {
        User currentUser = sessionManager.getCurrentUser();
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("list-item");
        card.setPadding(new Insets(12, 15, 12, 15));
        card.setCursor(javafx.scene.Cursor.HAND);

        // Icon
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("avatar");
        iconPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #f59e0b, #d97706);");
        Text iconText = new Text("🛒");
        iconText.setStyle("-fx-font-size: 16px;");
        iconPane.getChildren().add(iconText);

        // Info
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Text nameText = new Text(list.getName());
        nameText.getStyleClass().add("list-item-title");
        titleRow.getChildren().add(nameText);

        // Show shared indicator
        if (list.isShared()) {
            Text sharedIcon = new Text("👥");
            sharedIcon.setStyle("-fx-font-size: 12px;");
            titleRow.getChildren().add(sharedIcon);
        }

        String creatorName = list.getCreator().getName();
        boolean isOwn = currentUser != null && list.getCreator().getId().equals(currentUser.getId());
        Text creatorText = new Text(isOwn ? "Your list" : "Shared by " + creatorName);
        creatorText.getStyleClass().add("list-item-subtitle");

        Text itemCount = new Text(list.getItems().size() + " items");
        itemCount.getStyleClass().add("list-item-subtitle");

        info.getChildren().addAll(titleRow, creatorText, itemCount);

        card.getChildren().addAll(iconPane, info);

        // Click handler
        card.setOnMouseClicked(e -> selectList(list));

        return card;
    }

    private void selectList(ShoppingList list) {
        this.selectedList = list;
        User currentUser = sessionManager.getCurrentUser();

        // Show details view
        noListSelectedView.setVisible(false);
        noListSelectedView.setManaged(false);
        listDetailsView.setVisible(true);
        listDetailsView.setManaged(true);

        // Update header
        selectedListName.setText(list.getName());
        boolean isOwn = currentUser != null && list.getCreator().getId().equals(currentUser.getId());
        selectedListCreator.setText(isOwn ? "Created by you" : "Shared by " + list.getCreator().getName());

        // Show shared badge
        sharedBadge.setVisible(list.isShared());
        sharedBadge.setManaged(list.isShared());

        // Only owner can delete
        deleteListButton.setVisible(isOwn);
        deleteListButton.setManaged(isOwn);

        // Load items
        loadItems();
    }

    private void loadItems() {
        if (selectedList == null)
            return;

        itemsContainer.getChildren().clear();
        boughtItemsContainer.getChildren().clear();

        List<ShoppingListItem> items = shoppingListService.getItemsForList(selectedList);

        // Separate pending and bought items
        List<ShoppingListItem> pendingItems = new ArrayList<>();
        List<ShoppingListItem> boughtItems = new ArrayList<>();

        for (ShoppingListItem item : items) {
            if (Boolean.TRUE.equals(item.getBought())) {
                boughtItems.add(item);
            } else {
                pendingItems.add(item);
            }
        }

        // Update counts
        itemCountText.setText(pendingItems.size() + " items");
        boughtCountText.setText(boughtItems.size() + " items");

        // Show/hide bought section based on whether there are bought items
        boughtSection.setVisible(!boughtItems.isEmpty());
        boughtSection.setManaged(!boughtItems.isEmpty());

        // Display pending items
        if (pendingItems.isEmpty()) {
            VBox emptyState = new VBox(10);
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(20));
            Text emptyText = new Text("No items yet. Add some!");
            emptyText.getStyleClass().add("card-subtitle");
            emptyState.getChildren().add(emptyText);
            itemsContainer.getChildren().add(emptyState);
        } else {
            for (ShoppingListItem item : pendingItems) {
                itemsContainer.getChildren().add(createItemRow(item, false));
            }
        }

        // Display bought items
        for (ShoppingListItem item : boughtItems) {
            boughtItemsContainer.getChildren().add(createItemRow(item, true));
        }
    }

    private HBox createItemRow(ShoppingListItem item, boolean isBought) {
        User currentUser = sessionManager.getCurrentUser();
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-item");
        row.setPadding(new Insets(10, 12, 10, 12));

        if (isBought) {
            row.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 8;");
        }

        // Circular checkbox
        Button checkBox = new Button();
        checkBox.setPrefSize(28, 28);
        checkBox.setMinSize(28, 28);
        checkBox.setMaxSize(28, 28);

        if (isBought) {
            // Checked state - green with checkmark
            checkBox.setText("✓");
            checkBox.setStyle("-fx-background-color: #22c55e; -fx-background-radius: 14; " +
                    "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand;");
        } else {
            // Unchecked state - empty circle
            checkBox.setText("");
            checkBox.setStyle("-fx-background-color: white; -fx-border-color: #d1d5db; " +
                    "-fx-border-radius: 14; -fx-background-radius: 14; -fx-cursor: hand;");
        }

        // Toggle bought on click
        checkBox.setOnAction(e -> toggleItemBought(item));

        // Item info
        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Text nameText = new Text(item.getName());
        nameText.getStyleClass().add("list-item-title");

        if (isBought) {
            // Strikethrough and grayed out for bought items
            nameText.setStyle("-fx-strikethrough: true; -fx-fill: #9ca3af;");
        }

        boolean isOwnItem = currentUser != null && item.getCreator().getId().equals(currentUser.getId());
        Text creatorText = new Text("Added by " + (isOwnItem ? "you" : item.getCreator().getName()));
        creatorText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, creatorText);

        // Delete button
        Button deleteBtn = new Button("✕");
        deleteBtn.getStyleClass().add("icon-button");
        deleteBtn.setStyle("-fx-text-fill: #ef4444; -fx-cursor: hand;");
        deleteBtn.setOnAction(e -> removeItem(item));

        row.getChildren().addAll(checkBox, info, deleteBtn);
        return row;
    }

    private void toggleItemBought(ShoppingListItem item) {
        shoppingListService.toggleBought(item);

        // Refresh
        Optional<ShoppingList> refreshedList = shoppingListService.getList(selectedList.getId());
        refreshedList.ifPresent(list -> {
            this.selectedList = list;
            loadItems();
            loadLists(); // Update item counts in left panel
        });
    }

    @FXML
    public void addItem() {
        if (selectedList == null)
            return;

        String itemName = newItemField.getText().trim();
        if (itemName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Item", "Please enter an item name.",
                    getOwnerWindow(newItemField));
            return;
        }

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        shoppingListService.addItem(selectedList, itemName, currentUser);
        newItemField.clear();

        // Refresh the list to get updated item count
        Optional<ShoppingList> refreshedList = shoppingListService.getList(selectedList.getId());
        refreshedList.ifPresent(list -> {
            this.selectedList = list;
            loadItems();
            loadLists(); // Refresh left panel item counts
        });
    }

    private void removeItem(ShoppingListItem item) {
        shoppingListService.removeItem(item);

        // Refresh
        Optional<ShoppingList> refreshedList = shoppingListService.getList(selectedList.getId());
        refreshedList.ifPresent(list -> {
            this.selectedList = list;
            loadItems();
            loadLists();
        });
    }

    @FXML
    public void showCreateListDialog() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "You must be in a WG to create shopping lists.",
                    getOwnerWindow(listsContainer));
            return;
        }

        // Create dialog
        Dialog<ShoppingList> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(listsContainer));
        dialog.setTitle("Create New Shopping List");
        dialog.setHeaderText("Enter list details");

        // Set button types
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Create content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("List name (e.g., Weekly Groceries)");
        nameField.getStyleClass().add("modern-text-field");

        // Member selection for sharing
        VBox sharingSection = new VBox(10);
        Text sharingLabel = new Text("Share with WG members (optional):");
        sharingLabel.getStyleClass().add("card-subtitle");

        VBox memberCheckboxes = new VBox(8);
        List<CheckBox> checkBoxes = new ArrayList<>();

        for (User member : currentUser.getWg().getMitbewohner()) {
            if (!member.getId().equals(currentUser.getId())) {
                CheckBox cb = new CheckBox(member.getName() +
                        (member.getSurname() != null ? " " + member.getSurname() : ""));
                cb.setUserData(member);
                checkBoxes.add(cb);
                memberCheckboxes.getChildren().add(cb);
            }
        }

        if (memberCheckboxes.getChildren().isEmpty()) {
            Text noMembers = new Text("No other WG members to share with");
            noMembers.getStyleClass().add("card-subtitle");
            memberCheckboxes.getChildren().add(noMembers);
        }

        sharingSection.getChildren().addAll(sharingLabel, memberCheckboxes);
        content.getChildren().addAll(new Text("List Name:"), nameField, sharingSection);

        dialog.getDialogPane().setContent(content);

        // Enable/disable create button based on input
        dialog.getDialogPane().lookupButton(createButtonType).setDisable(true);
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            dialog.getDialogPane().lookupButton(createButtonType).setDisable(newVal.trim().isEmpty());
        });

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                List<User> sharedWith = new ArrayList<>();
                for (CheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        sharedWith.add((User) cb.getUserData());
                    }
                }
                return shoppingListService.createList(nameField.getText().trim(), currentUser, sharedWith);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(list -> {
            loadLists();
            selectList(list);
        });
    }

    @FXML
    public void showSharingDialog() {
        if (selectedList == null)
            return;

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || !selectedList.getCreator().getId().equals(currentUser.getId())) {
            showAlert(Alert.AlertType.WARNING, "Permission Denied", "Only the list creator can manage sharing.",
                    getOwnerWindow(listsContainer));
            return;
        }

        if (currentUser.getWg() == null)
            return;

        Dialog<List<User>> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(listsContainer));
        dialog.setTitle("Manage Sharing");
        dialog.setHeaderText("Select members to share with");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        List<CheckBox> checkBoxes = new ArrayList<>();
        List<Long> currentlySharedIds = selectedList.getSharedWith().stream()
                .map(User::getId).toList();

        for (User member : currentUser.getWg().getMitbewohner()) {
            if (!member.getId().equals(currentUser.getId())) {
                CheckBox cb = new CheckBox(member.getName() +
                        (member.getSurname() != null ? " " + member.getSurname() : ""));
                cb.setUserData(member);
                cb.setSelected(currentlySharedIds.contains(member.getId()));
                checkBoxes.add(cb);
                content.getChildren().add(cb);
            }
        }

        if (content.getChildren().isEmpty()) {
            Text noMembers = new Text("No other WG members available");
            noMembers.getStyleClass().add("card-subtitle");
            content.getChildren().add(noMembers);
        }

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                List<User> sharedWith = new ArrayList<>();
                for (CheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        sharedWith.add((User) cb.getUserData());
                    }
                }
                return sharedWith;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(sharedWith -> {
            shoppingListService.shareList(selectedList, sharedWith);
            Optional<ShoppingList> refreshedList = shoppingListService.getList(selectedList.getId());
            refreshedList.ifPresent(list -> {
                this.selectedList = list;
                selectList(list);
                loadLists();
            });
        });
    }

    @FXML
    public void deleteSelectedList() {
        if (selectedList == null)
            return;

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || !selectedList.getCreator().getId().equals(currentUser.getId())) {
            showAlert(Alert.AlertType.WARNING, "Permission Denied", "Only the list creator can delete this list.",
                    getOwnerWindow(listsContainer));
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialogOwner(confirm, getOwnerWindow(listsContainer));
        confirm.setTitle("Delete List");
        confirm.setHeaderText("Delete \"" + selectedList.getName() + "\"?");
        confirm.setContentText("This will permanently delete the list and all its items.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                shoppingListService.deleteList(selectedList);
                selectedList = null;

                // Hide details view
                listDetailsView.setVisible(false);
                listDetailsView.setManaged(false);
                noListSelectedView.setVisible(true);
                noListSelectedView.setManaged(true);

                loadLists();
            }
        });
    }

}
