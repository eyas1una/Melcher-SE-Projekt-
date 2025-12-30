package com.group_2.repository.shopping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.group_2.model.User;
import com.group_2.model.shopping.ShoppingList;

import java.util.List;

@Repository
public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {

    /**
     * Find all shopping lists created by a user.
     */
    List<ShoppingList> findByCreator(User creator);

    /**
     * Find all shopping lists where the user is in the sharedWith list.
     */
    List<ShoppingList> findBySharedWithContaining(User user);

    /**
     * Find all shopping lists accessible to a user (created by OR shared with).
     */
    @Query("SELECT DISTINCT sl FROM ShoppingList sl LEFT JOIN sl.sharedWith sw " +
            "WHERE sl.creator = :user OR sw = :user")
    List<ShoppingList> findAllAccessibleByUser(@Param("user") User user);
}
