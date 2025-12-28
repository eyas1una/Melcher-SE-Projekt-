package com.group_2.service;

import com.group_2.repository.ShoppingListItemRepository;
import com.group_2.repository.ShoppingListRepository;
import com.model.ShoppingList;
import com.model.ShoppingListItem;
import com.model.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing shopping lists and their items.
 */
@Service
@Transactional
public class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository itemRepository;

    public ShoppingListService(ShoppingListRepository shoppingListRepository,
            ShoppingListItemRepository itemRepository) {
        this.shoppingListRepository = shoppingListRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Create a new shopping list.
     */
    public ShoppingList createList(String name, User creator, List<User> sharedWith) {
        ShoppingList list = new ShoppingList(name, creator, sharedWith);
        return shoppingListRepository.save(list);
    }

    /**
     * Create a private shopping list (no sharing).
     */
    public ShoppingList createPrivateList(String name, User creator) {
        return createList(name, creator, null);
    }

    /**
     * Get all shopping lists accessible to a user (own + shared).
     */
    public List<ShoppingList> getAccessibleLists(User user) {
        return shoppingListRepository.findAllAccessibleByUser(user);
    }

    /**
     * Get a shopping list by ID.
     */
    public Optional<ShoppingList> getList(Long id) {
        return shoppingListRepository.findById(id);
    }

    /**
     * Update the sharing settings for a list.
     */
    public ShoppingList shareList(ShoppingList list, List<User> users) {
        list.setSharedWith(users);
        return shoppingListRepository.save(list);
    }

    /**
     * Add a user to the sharedWith list.
     */
    public ShoppingList addSharedUser(ShoppingList list, User user) {
        list.addSharedUser(user);
        return shoppingListRepository.save(list);
    }

    /**
     * Remove a user from the sharedWith list.
     */
    public ShoppingList removeSharedUser(ShoppingList list, User user) {
        list.removeSharedUser(user);
        return shoppingListRepository.save(list);
    }

    /**
     * Delete a shopping list.
     */
    public void deleteList(ShoppingList list) {
        shoppingListRepository.delete(list);
    }

    /**
     * Add an item to a shopping list.
     */
    public ShoppingListItem addItem(ShoppingList list, String itemName, User creator) {
        ShoppingListItem item = new ShoppingListItem(itemName, creator, list);
        return itemRepository.save(item);
    }

    /**
     * Remove an item from a shopping list.
     */
    public void removeItem(ShoppingListItem item) {
        itemRepository.delete(item);
    }

    /**
     * Get all items in a shopping list.
     */
    public List<ShoppingListItem> getItemsForList(ShoppingList list) {
        return itemRepository.findByShoppingList(list);
    }

    /**
     * Update an item's name.
     */
    public ShoppingListItem updateItem(ShoppingListItem item, String newName) {
        item.setName(newName);
        return itemRepository.save(item);
    }

    /**
     * Toggle the bought status of an item.
     */
    public ShoppingListItem toggleBought(ShoppingListItem item) {
        item.setBought(!Boolean.TRUE.equals(item.getBought()));
        return itemRepository.save(item);
    }
}
