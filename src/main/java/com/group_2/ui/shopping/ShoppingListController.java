package com.group_2.ui.shopping;

import com.group_2.dto.shopping.ShoppingListDTO;
import com.group_2.dto.shopping.ShoppingListItemDTO;
import com.group_2.model.User;
import com.group_2.service.shopping.ShoppingListService;
import com.group_2.ui.core.Controller;
import com.group_2.ui.core.NavbarController;
import com.group_2.util.SessionManager;

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
 * Controller for the shopping list view. Manages shopping lists and their
 * items.
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

    private ShoppingListDTO selectedList;

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
        List<ShoppingListDTO> lists = shoppingListService.getAccessibleListsDTO(currentUser);

        if (lists.isEmpty()) {
            VBox emptyState = new VBox(10);
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(30));
            Text emptyIcon = new Text("SL");
            emptyIcon.getStyleClass().add("empty-state-icon-large");
            Text emptyText = new Text("Create your first list!");
            emptyText.getStyleClass().add("empty-state-subtitle");
            emptyState.getStyleClass().add("empty-state-card");
            emptyState.getChildren().addAll(emptyIcon, emptyText);
            listsContainer.getChildren().add(emptyState);
        } else {
            for (ShoppingListDTO list : lists) {
                listsContainer.getChildren().add(createListCard(list));
            }
            // Auto-select first list if none is selected
            if (selectedList == null) {
                selectList(lists.get(0));
            }
        }
    }

    private HBox createListCard(ShoppingListDTO list) {
        User currentUser = sessionManager.getCurrentUser();
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("list-item");
        card.setPadding(new Insets(12, 15, 12, 15));
        card.setCursor(javafx.scene.Cursor.HAND);

        // Icon
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().addAll("avatar", "avatar-amber");
        String iconInitial = (list.name() != null && !list.name().isEmpty())
                ? list.name().substring(0, 1).toUpperCase()
                : "?";
        Text iconText = new Text(iconInitial);
        iconText.getStyleClass().add("avatar-text");
        iconPane.getChildren().add(iconText);

        // Info
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Text nameText = new Text(list.name());
        nameText.getStyleClass().add("list-item-title");
        titleRow.getChildren().add(nameText);

        // Show shared indicator
        if (list.shared()) {
            Text sharedIcon = new Text("Shared");
            sharedIcon.getStyleClass().add("list-item-subtitle");
            titleRow.getChildren().add(sharedIcon);
        }

        boolean isOwn = currentUser != null && list.isCreator(currentUser.getId());
        Text creatorText = new Text(isOwn ? "Your list" : "Shared by " + list.creatorName());
        creatorText.getStyleClass().add("list-item-subtitle");

        Text itemCount = new Text(list.itemCount() + " items");
        itemCount.getStyleClass().add("list-item-subtitle");

        info.getChildren().addAll(titleRow, creatorText, itemCount);

        card.getChildren().addAll(iconPane, info);

        // Click handler
        card.setOnMouseClicked(e -> selectList(list));

        return card;
    }

    private void selectList(ShoppingListDTO list) {
        this.selectedList = list;
        User currentUser = sessionManager.getCurrentUser();

        // Show details view
        noListSelectedView.setVisible(false);
        noListSelectedView.setManaged(false);
        listDetailsView.setVisible(true);
        listDetailsView.setManaged(true);

        // Update header
        selectedListName.setText(list.name());
        boolean isOwn = currentUser != null && list.isCreator(currentUser.getId());
        selectedListCreator.setText(isOwn ? "Created by you" : "Shared by " + list.creatorName());

        // Show shared badge
        sharedBadge.setVisible(list.shared());
        sharedBadge.setManaged(list.shared());

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

        List<ShoppingListItemDTO> items = shoppingListService.getItemsForListDTO(selectedList.id());

        // Separate pending and bought items
        List<ShoppingListItemDTO> pendingItems = new ArrayList<>();
        List<ShoppingListItemDTO> boughtItems = new ArrayList<>();

        for (ShoppingListItemDTO item : items) {
            if (item.bought()) {
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
            for (ShoppingListItemDTO item : pendingItems) {
                itemsContainer.getChildren().add(createItemRow(item, false));
            }
        }

        // Display bought items
        for (ShoppingListItemDTO item : boughtItems) {
            boughtItemsContainer.getChildren().add(createItemRow(item, true));
        }
    }

    private HBox createItemRow(ShoppingListItemDTO item, boolean isBought) {
        User currentUser = sessionManager.getCurrentUser();
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-item");
        row.setPadding(new Insets(10, 12, 10, 12));

        if (isBought) {
            row.getStyleClass().add("list-item-bought");
        }

        // Circular checkbox
        Button checkBox = new Button();
        checkBox.setPrefSize(28, 28);
        checkBox.setMinSize(28, 28);
        checkBox.setMaxSize(28, 28);
        checkBox.getStyleClass().add("shopping-check-button");

        if (isBought) {
            checkBox.setText("X");
            checkBox.getStyleClass().remove("shopping-check-unchecked");
            checkBox.getStyleClass().add("shopping-check-checked");
        } else {
            checkBox.setText("");
            checkBox.getStyleClass().remove("shopping-check-checked");
            checkBox.getStyleClass().add("shopping-check-unchecked");
        }

        // Toggle bought on click
        checkBox.setOnAction(e -> toggleItemBought(item));

        // Item info
        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Text nameText = new Text(item.name());
        nameText.getStyleClass().add("list-item-title");

        if (isBought) {
            // Strikethrough and grayed out for bought items
            nameText.getStyleClass().add("bought-text");
        }

        boolean isOwnItem = currentUser != null && item.creatorId() != null
                && item.creatorId().equals(currentUser.getId());
        Text creatorText = new Text("Added by " + (isOwnItem ? "you" : item.creatorName()));
        creatorText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, creatorText);

        // Delete button
        Button deleteBtn = new Button("X");
        deleteBtn.getStyleClass().addAll("icon-button", "icon-button-danger");
        deleteBtn.setOnAction(e -> removeItem(item));

        row.getChildren().addAll(checkBox, info, deleteBtn);
        return row;
    }

    private void toggleItemBought(ShoppingListItemDTO item) {
        shoppingListService.toggleBoughtById(item.id());

        // Refresh
        Optional<ShoppingListDTO> refreshedList = shoppingListService.getListDTO(selectedList.id());
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
            showWarningAlert("Empty Item", "Please enter an item name.", getOwnerWindow(newItemField));
            return;
        }

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        shoppingListService.addItemByIds(selectedList.id(), itemName, currentUser.getId());
        newItemField.clear();

        // Refresh the list to get updated item count
        Optional<ShoppingListDTO> refreshedList = shoppingListService.getListDTO(selectedList.id());
        refreshedList.ifPresent(list -> {
            this.selectedList = list;
            loadItems();
            loadLists(); // Refresh left panel item counts
        });
    }

    private void removeItem(ShoppingListItemDTO item) {
        shoppingListService.removeItemById(item.id());

        // Refresh
        Optional<ShoppingListDTO> refreshedList = shoppingListService.getListDTO(selectedList.id());
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
            showErrorAlert("Error", "You must be in a WG to create shopping lists.", getOwnerWindow(listsContainer));
            return;
        }

        // Create dialog
        Dialog<ShoppingListDTO> dialog = new Dialog<>();
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
                CheckBox cb = new CheckBox(
                        member.getName() + (member.getSurname() != null ? " " + member.getSurname() : ""));
                cb.setUserData(member.getId()); // Store ID instead of User
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
                List<Long> sharedWithIds = new ArrayList<>();
                for (CheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        sharedWithIds.add((Long) cb.getUserData());
                    }
                }
                return shoppingListService.createListByUserIds(nameField.getText().trim(), currentUser.getId(),
                        sharedWithIds);
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
        if (currentUser == null || !selectedList.isCreator(currentUser.getId())) {
            showWarningAlert("Permission Denied", "Only the list creator can manage sharing.",
                    getOwnerWindow(listsContainer));
            return;
        }

        if (currentUser.getWg() == null)
            return;

        Dialog<List<Long>> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(listsContainer));
        dialog.setTitle("Manage Sharing");
        dialog.setHeaderText("Select members to share with");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        List<CheckBox> checkBoxes = new ArrayList<>();
        List<Long> currentlySharedIds = selectedList.sharedWithIds();

        for (User member : currentUser.getWg().getMitbewohner()) {
            if (!member.getId().equals(currentUser.getId())) {
                CheckBox cb = new CheckBox(
                        member.getName() + (member.getSurname() != null ? " " + member.getSurname() : ""));
                cb.setUserData(member.getId()); // Store ID instead of User
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
                List<Long> sharedWithIds = new ArrayList<>();
                for (CheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        sharedWithIds.add((Long) cb.getUserData());
                    }
                }
                return sharedWithIds;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(sharedWithIds -> {
            shoppingListService.shareListByIds(selectedList.id(), sharedWithIds);
            Optional<ShoppingListDTO> refreshedList = shoppingListService.getListDTO(selectedList.id());
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
        if (currentUser == null || !selectedList.isCreator(currentUser.getId())) {
            showWarningAlert("Permission Denied", "Only the list creator can delete this list.",
                    getOwnerWindow(listsContainer));
            return;
        }

        boolean confirmed = showConfirmDialog("Delete List", "Delete \"" + selectedList.name() + "\"?",
                "This will permanently delete the list and all its items.", getOwnerWindow(listsContainer));

        if (confirmed) {
            shoppingListService.deleteListById(selectedList.id());
            selectedList = null;

            // Hide details view
            listDetailsView.setVisible(false);
            listDetailsView.setManaged(false);
            noListSelectedView.setVisible(true);
            noListSelectedView.setManaged(true);

            loadLists();
        }
    }

}
