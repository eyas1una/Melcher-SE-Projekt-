package com.group_2.service.shopping;

import com.group_2.dto.shopping.ShoppingListDTO;
import com.group_2.dto.shopping.ShoppingListItemDTO;
import com.group_2.dto.shopping.ShoppingMapper;
import com.group_2.model.User;
import com.group_2.model.shopping.ShoppingList;
import com.group_2.model.shopping.ShoppingListItem;
import com.group_2.repository.UserRepository;
import com.group_2.repository.shopping.ShoppingListItemRepository;
import com.group_2.repository.shopping.ShoppingListRepository;

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
    private final UserRepository userRepository;
    private final ShoppingMapper shoppingMapper;

    public ShoppingListService(ShoppingListRepository shoppingListRepository, ShoppingListItemRepository itemRepository,
            UserRepository userRepository, ShoppingMapper shoppingMapper) {
        this.shoppingListRepository = shoppingListRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.shoppingMapper = shoppingMapper;
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

    // ========== DTO Methods ==========

    /**
     * Get all shopping lists accessible to a user as DTOs.
     */
    public List<ShoppingListDTO> getAccessibleListsDTO(User user) {
        return shoppingMapper.toDTOList(getAccessibleLists(user));
    }

    /**
     * Get a shopping list by ID as DTO.
     */
    public Optional<ShoppingListDTO> getListDTO(Long id) {
        return getList(id).map(shoppingMapper::toDTO);
    }

    /**
     * Get items for a list by list ID as DTOs.
     */
    public List<ShoppingListItemDTO> getItemsForListDTO(Long listId) {
        Optional<ShoppingList> list = getList(listId);
        if (list.isEmpty()) {
            return List.of();
        }
        return shoppingMapper.toItemDTOList(getItemsForList(list.get()));
    }

    /**
     * Create a shopping list with shared user IDs (for DTO-based controller).
     */
    public ShoppingListDTO createListByUserIds(String name, Long creatorId, List<Long> sharedWithIds) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Creator not found"));

        List<User> sharedWith = new java.util.ArrayList<>();
        if (sharedWithIds != null) {
            for (Long userId : sharedWithIds) {
                userRepository.findById(userId).ifPresent(sharedWith::add);
            }
        }

        ShoppingList list = createList(name, creator, sharedWith);
        return shoppingMapper.toDTO(list);
    }

    /**
     * Add an item to a shopping list by IDs (for DTO-based controller).
     */
    public ShoppingListItemDTO addItemByIds(Long listId, String itemName, Long creatorId) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Shopping list not found"));
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Creator not found"));

        ShoppingListItem item = addItem(list, itemName, creator);
        return shoppingMapper.toItemDTO(item);
    }

    /**
     * Toggle the bought status of an item by ID (for DTO-based controller).
     */
    public ShoppingListItemDTO toggleBoughtById(Long itemId) {
        ShoppingListItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        ShoppingListItem updated = toggleBought(item);
        return shoppingMapper.toItemDTO(updated);
    }

    /**
     * Remove an item by ID (for DTO-based controller).
     */
    public void removeItemById(Long itemId) {
        ShoppingListItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        removeItem(item);
    }

    /**
     * Delete a shopping list by ID (for DTO-based controller).
     */
    public void deleteListById(Long listId) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Shopping list not found"));
        deleteList(list);
    }

    /**
     * Update sharing settings by IDs (for DTO-based controller).
     */
    public ShoppingListDTO shareListByIds(Long listId, List<Long> sharedWithIds) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new IllegalArgumentException("Shopping list not found"));

        List<User> sharedWith = new java.util.ArrayList<>();
        if (sharedWithIds != null) {
            for (Long userId : sharedWithIds) {
                userRepository.findById(userId).ifPresent(sharedWith::add);
            }
        }

        ShoppingList updated = shareList(list, sharedWith);
        return shoppingMapper.toDTO(updated);
    }
}
