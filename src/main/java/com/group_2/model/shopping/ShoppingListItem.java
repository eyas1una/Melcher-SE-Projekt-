package com.group_2.model.shopping;

import com.group_2.model.User;
import jakarta.persistence.*;

/**
 * Entity representing an item in a shopping list.
 */
@Entity
@Table(name = "shopping_list_item")
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopping_list_id", nullable = false)
    private ShoppingList shoppingList;

    @Column(nullable = false)
    private Boolean bought = false;

    public ShoppingListItem() {
    }

    public ShoppingListItem(String name, User creator, ShoppingList shoppingList) {
        this.name = name;
        this.creator = creator;
        this.shoppingList = shoppingList;
        this.bought = false;
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

    public ShoppingList getShoppingList() {
        return shoppingList;
    }

    public void setShoppingList(ShoppingList shoppingList) {
        this.shoppingList = shoppingList;
    }

    public Boolean getBought() {
        return bought;
    }

    public void setBought(Boolean bought) {
        this.bought = bought;
    }
}
