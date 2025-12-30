package com.group_2.model.shopping;

import com.group_2.model.User;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a shopping list.
 * Lists can be private (creator only) or shared with specific WG members.
 */
@Entity
@Table(name = "shopping_list")
public class ShoppingList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "shopping_list_shared_with", joinColumns = @JoinColumn(name = "shopping_list_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private List<User> sharedWith = new ArrayList<>();

    @OneToMany(mappedBy = "shoppingList", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ShoppingListItem> items = new ArrayList<>();

    public ShoppingList() {
    }

    public ShoppingList(String name, User creator) {
        this.name = name;
        this.creator = creator;
    }

    public ShoppingList(String name, User creator, List<User> sharedWith) {
        this.name = name;
        this.creator = creator;
        this.sharedWith = sharedWith != null ? new ArrayList<>(sharedWith) : new ArrayList<>();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public List<User> getSharedWith() {
        return new ArrayList<>(sharedWith);
    }

    public void setSharedWith(List<User> sharedWith) {
        this.sharedWith = sharedWith != null ? new ArrayList<>(sharedWith) : new ArrayList<>();
    }

    public void addSharedUser(User user) {
        if (!sharedWith.contains(user)) {
            sharedWith.add(user);
        }
    }

    public void removeSharedUser(User user) {
        sharedWith.remove(user);
    }

    public List<ShoppingListItem> getItems() {
        return new ArrayList<>(items);
    }

    public void addItem(ShoppingListItem item) {
        items.add(item);
        item.setShoppingList(this);
    }

    public void removeItem(ShoppingListItem item) {
        items.remove(item);
        item.setShoppingList(null);
    }

    /**
     * Check if a user has access to this list.
     * A user has access if they are the creator or in the sharedWith list.
     */
    public boolean hasAccess(User user) {
        if (user == null)
            return false;
        if (creator != null && creator.getId().equals(user.getId()))
            return true;
        return sharedWith.stream().anyMatch(u -> u.getId().equals(user.getId()));
    }

    /**
     * Check if this list is shared with anyone.
     */
    public boolean isShared() {
        return !sharedWith.isEmpty();
    }
}
