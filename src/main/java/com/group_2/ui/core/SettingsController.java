package com.group_2.ui.core;

import com.group_2.dto.cleaning.RoomDTO;
import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.service.core.HouseholdSetupService;
import com.group_2.service.core.WGService;
import com.group_2.util.SessionManager;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Controller for the WG settings view. Shows rooms, members, invitation code,
 * and admin controls.
 */
@Component
public class SettingsController extends Controller {

    private final SessionManager sessionManager;
    private final WGService wgService;
    private final HouseholdSetupService householdSetupService;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML
    private Text wgNameHeader;
    @FXML
    private Text inviteCodeText;
    @FXML
    private Text roomCountText;
    @FXML
    private Text memberCountText;
    @FXML
    private VBox roomsBox;
    @FXML
    private VBox membersBox;
    @FXML
    private VBox adminCard;
    @FXML
    private javafx.scene.control.Button addRoomButton;

    @Autowired
    public SettingsController(SessionManager sessionManager, WGService wgService,
            HouseholdSetupService householdSetupService) {
        this.sessionManager = sessionManager;
        this.wgService = wgService;
        this.householdSetupService = householdSetupService;
    }

    public void initView() {
        sessionManager.refreshCurrentUser();
        loadWGData();
    }

    private void loadWGData() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        WG wg = currentUser.getWg();
        if (wg == null) {
            showWarningAlert("No WG", "You are not a member of any WG.", getOwnerWindow(wgNameHeader));
            return;
        }

        // Header
        wgNameHeader.setText(wg.name);
        inviteCodeText.setText(wg.getInviteCode());

        // Check if admin
        boolean isAdmin = wg.admin != null && wg.admin.getId().equals(currentUser.getId());
        adminCard.setVisible(isAdmin);
        adminCard.setManaged(isAdmin);

        // Show/hide Add Room button based on admin status
        if (addRoomButton != null) {
            addRoomButton.setVisible(isAdmin);
            addRoomButton.setManaged(isAdmin);
        }

        // Load rooms
        loadRooms(wg);

        // Load members
        loadMembers(wg);
    }

    private void loadRooms(WG wg) {
        roomsBox.getChildren().clear();
        User currentUser = sessionManager.getCurrentUser();
        boolean isAdmin = wg.admin != null && currentUser != null && wg.admin.getId().equals(currentUser.getId());

        java.util.List<RoomDTO> rooms = householdSetupService.getRoomsForWgDTO(wg);
        if (!rooms.isEmpty()) {
            roomCountText.setText(rooms.size() + " room" + (rooms.size() > 1 ? "s" : "") + " in your WG");
            for (RoomDTO room : rooms) {
                roomsBox.getChildren().add(createRoomListItem(room, isAdmin));
            }
        } else {
            roomCountText.setText("No rooms yet");
            Text noRooms = new Text(isAdmin ? "Add your first room above!" : "No rooms have been added yet.");
            noRooms.getStyleClass().add("list-item-subtitle");
            roomsBox.getChildren().add(noRooms);
        }
    }

    private void loadMembers(WG wg) {
        membersBox.getChildren().clear();
        if (wg.mitbewohner != null && !wg.mitbewohner.isEmpty()) {
            memberCountText.setText(
                    wg.mitbewohner.size() + " member" + (wg.mitbewohner.size() > 1 ? "s" : "") + " in your WG");
            for (User user : wg.mitbewohner) {
                membersBox.getChildren().add(createMemberListItem(user, wg));
            }
        } else {
            memberCountText.setText("No members yet");
        }
    }

    private HBox createRoomListItem(RoomDTO room, boolean isAdmin) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("list-item");
        item.setPadding(new Insets(10, 15, 10, 15));

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().addAll("avatar", "avatar-green");
        Text iconText = new Text("??");
        iconText.getStyleClass().add("avatar-text");
        iconPane.getChildren().add(iconText);

        VBox info = new VBox(3);
        javafx.scene.layout.HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        Text nameText = new Text(room.name());
        nameText.getStyleClass().add("list-item-title");
        Text idText = new Text("Room ID: " + room.id());
        idText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, idText);

        item.getChildren().addAll(iconPane, info);

        // Admin actions for rooms
        if (isAdmin) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);

            javafx.scene.control.Button editBtn = new javafx.scene.control.Button("Edit");
            editBtn.setTooltip(new javafx.scene.control.Tooltip("Edit Room"));
            editBtn.getStyleClass().addAll("table-action-button", "table-edit-button");
            editBtn.setOnAction(e -> editRoom(room));
            actions.getChildren().add(editBtn);

            javafx.scene.control.Button deleteBtn = new javafx.scene.control.Button("Delete");
            deleteBtn.setTooltip(new javafx.scene.control.Tooltip("Delete Room"));
            deleteBtn.getStyleClass().addAll("table-action-button", "table-delete-button");
            deleteBtn.setOnAction(e -> deleteRoom(room));
            actions.getChildren().add(deleteBtn);

            item.getChildren().add(actions);
        }

        return item;
    }

    private HBox createMemberListItem(User user, WG wg) {
        User currentUser = sessionManager.getCurrentUser();
        boolean currentUserIsAdmin = wg.admin != null && wg.admin.getId().equals(currentUser.getId());
        boolean isMemberAdmin = wg.admin != null && wg.admin.getId().equals(user.getId());
        boolean isSelf = user.getId().equals(currentUser.getId());

        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("list-item");
        item.setPadding(new Insets(10, 15, 10, 15));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar");
        String initial = user.getName() != null && !user.getName().isEmpty()
                ? user.getName().substring(0, 1).toUpperCase()
                : "?";
        Text avatarText = new Text(initial);
        avatarText.getStyleClass().add("avatar-text");
        avatar.getChildren().add(avatarText);

        VBox info = new VBox(3);
        javafx.scene.layout.HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        String fullName = user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "");
        Text nameText = new Text(fullName + (isMemberAdmin ? " (Admin)" : "") + (isSelf ? " (You)" : ""));
        nameText.getStyleClass().add("list-item-title");
        Text emailText = new Text(user.getEmail() != null ? user.getEmail() : "No email");
        emailText.getStyleClass().add("list-item-subtitle");
        info.getChildren().addAll(nameText, emailText);

        item.getChildren().addAll(avatar, info);

        // Admin actions (only show if current user is admin and not viewing themselves)
        if (currentUserIsAdmin && !isSelf) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);

            if (!isMemberAdmin) {
                // Make Admin button
                javafx.scene.control.Button makeAdminBtn = new javafx.scene.control.Button("Make Admin");
                makeAdminBtn.setTooltip(new javafx.scene.control.Tooltip("Make Admin"));
                makeAdminBtn.getStyleClass().addAll("table-action-button", "table-warning-button");
                makeAdminBtn.setOnAction(e -> handleMakeAdmin(user));
                actions.getChildren().add(makeAdminBtn);
            }

            // Remove button
            javafx.scene.control.Button removeBtn = new javafx.scene.control.Button("Remove");
            removeBtn.setTooltip(new javafx.scene.control.Tooltip("Remove from WG"));
            removeBtn.getStyleClass().addAll("table-action-button", "table-delete-button");
            removeBtn.setOnAction(e -> handleRemoveMember(user));
            actions.getChildren().add(removeBtn);

            item.getChildren().add(actions);
        }

        return item;
    }

    private void handleMakeAdmin(User user) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        WG wg = currentUser.getWg();
        String userName = user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "");

        boolean confirmed = showConfirmDialog("Transfer Admin Rights", "Make " + userName + " the new admin?",
                "You will no longer be the admin of this WG.", getOwnerWindow(membersBox));

        if (confirmed) {
            try {
                wgService.updateWG(wg.getId(), wg.name, user);
                sessionManager.refreshCurrentUser();
                loadWGData();
                showSuccessAlert("Success", userName + " is now the admin!", getOwnerWindow(membersBox));
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to transfer admin rights: " + e.getMessage(),
                        getOwnerWindow(membersBox));
            }
        }
    }

    private void handleRemoveMember(User user) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        WG wg = currentUser.getWg();
        String userName = user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "");

        boolean confirmed = showConfirmDialog("Remove Member", "Remove " + userName + " from the WG?",
                "This action cannot be undone.", getOwnerWindow(membersBox));

        if (confirmed) {
            try {
                wgService.removeMitbewohner(wg.getId(), user.getId());
                sessionManager.refreshCurrentUser();
                loadWGData();
                showSuccessAlert("Success", userName + " has been removed from the WG.", getOwnerWindow(membersBox));
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to remove member: " + e.getMessage(), getOwnerWindow(membersBox));
            }
        }
    }

    @FXML
    public void copyInviteCode() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getWg() != null) {
            String code = currentUser.getWg().getInviteCode();
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            clipboard.setContent(content);
            showSuccessAlert("Copied!", "Invitation code copied to clipboard: " + code, getOwnerWindow(inviteCodeText));
        }
    }

    @FXML
    public void addRoom() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        // Only admin can add rooms
        WG wg = currentUser.getWg();
        if (wg.admin == null || !wg.admin.getId().equals(currentUser.getId())) {
            showWarningAlert("Permission Denied", "Only the admin can add rooms.", getOwnerWindow(roomsBox));
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        configureDialogOwner(dialog, getOwnerWindow(roomsBox));
        dialog.setTitle("Add Room");
        dialog.setHeaderText("Add a new room to your WG");
        dialog.setContentText("Room name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(roomName -> {
            if (!roomName.trim().isEmpty()) {
                try {
                    RoomDTO newRoom = householdSetupService.createRoomDTO(roomName.trim());
                    wgService.addRoomById(currentUser.getWg().getId(), newRoom.id());
                    sessionManager.refreshCurrentUser();
                    loadWGData();
                    showSuccessAlert("Success", "Room '" + roomName + "' added!", getOwnerWindow(roomsBox));
                } catch (Exception e) {
                    showErrorAlert("Error", "Failed to add room: " + e.getMessage(), getOwnerWindow(roomsBox));
                }
            }
        });
    }

    private void editRoom(RoomDTO room) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        TextInputDialog dialog = new TextInputDialog(room.name());
        configureDialogOwner(dialog, getOwnerWindow(roomsBox));
        dialog.setTitle("Edit Room");
        dialog.setHeaderText("Edit room name");
        dialog.setContentText("Room name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                try {
                    householdSetupService.updateRoom(room.id(), newName.trim());
                    sessionManager.refreshCurrentUser();
                    loadWGData();
                    showSuccessAlert("Success", "Room renamed to '" + newName + "'!", getOwnerWindow(roomsBox));
                } catch (Exception e) {
                    showErrorAlert("Error", "Failed to rename room: " + e.getMessage(), getOwnerWindow(roomsBox));
                }
            }
        });
    }

    private void deleteRoom(RoomDTO room) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        boolean confirmed = showConfirmDialog("Delete Room", "Delete room '" + room.name() + "'?",
                "This will also remove any cleaning tasks and templates associated with this room.",
                getOwnerWindow(roomsBox));

        if (confirmed) {
            try {
                // Use the consolidated deletion method that handles everything in one
                // transaction
                householdSetupService.deleteRoomById(room.id(), currentUser.getWg());
                sessionManager.refreshCurrentUser();
                loadWGData();
                showSuccessAlert("Success", "Room '" + room.name() + "' deleted!", getOwnerWindow(roomsBox));
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to delete room: " + e.getMessage(), getOwnerWindow(roomsBox));
            }
        }
    }

    @FXML
    public void editWgName() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        TextInputDialog dialog = new TextInputDialog(currentUser.getWg().name);
        configureDialogOwner(dialog, getOwnerWindow(wgNameHeader));
        dialog.setTitle("Edit WG Name");
        dialog.setHeaderText("Change your WG name");
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                try {
                    WG wg = currentUser.getWg();
                    wgService.updateWG(wg.getId(), newName.trim(), wg.admin);
                    sessionManager.refreshCurrentUser();
                    loadWGData();
                    showSuccessAlert("Success", "WG name updated!", getOwnerWindow(wgNameHeader));
                } catch (Exception e) {
                    showErrorAlert("Error", "Failed to update WG name: " + e.getMessage(),
                            getOwnerWindow(wgNameHeader));
                }
            }
        });
    }

    @FXML
    public void deleteWG() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        boolean confirmed = showConfirmDialog("Delete WG", "Are you sure you want to delete this WG?",
                "This action cannot be undone. All members will be removed.", getOwnerWindow(wgNameHeader));

        if (confirmed) {
            try {
                Long wgId = currentUser.getWg().getId();
                wgService.deleteWG(wgId);
                sessionManager.refreshCurrentUser();
                showSuccessAlert("Success", "WG deleted.", getOwnerWindow(wgNameHeader));
                loadScene(wgNameHeader.getScene(), "/core/no_wg.fxml");
            } catch (Exception e) {
                showErrorAlert("Error", "Failed to delete WG: " + e.getMessage(), getOwnerWindow(wgNameHeader));
            }
        }
    }

    @FXML
    public void backToHome() {
        loadScene(wgNameHeader.getScene(), "/core/main_screen.fxml");
        javafx.application.Platform.runLater(() -> {
            MainScreenController mainController = applicationContext.getBean(MainScreenController.class);
            mainController.initView();
        });
    }
}
