package com.group_2.repository.shopping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.shopping.ShoppingList;
import com.group_2.model.shopping.ShoppingListItem;

import java.util.List;

@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {

    /**
     * Find all items in a shopping list.
     */
    List<ShoppingListItem> findByShoppingList(ShoppingList shoppingList);
}
