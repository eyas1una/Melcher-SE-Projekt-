package com.group_2.dto.shopping;

import com.group_2.model.User;
import com.group_2.model.shopping.ShoppingList;
import com.group_2.model.shopping.ShoppingListItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for shopping domain DTOs.
 */
@Component
public class ShoppingMapper {

    public ShoppingListItemDTO toItemDTO(ShoppingListItem item) {
        if (item == null) {
            return null;
        }

        User creator = item.getCreator();
        String creatorName = creator != null ? getDisplayName(creator) : "Unknown";

        return new ShoppingListItemDTO(item.getId(), item.getName(), creator != null ? creator.getId() : null,
                creatorName, Boolean.TRUE.equals(item.getBought()));
    }

    public List<ShoppingListItemDTO> toItemDTOList(List<ShoppingListItem> items) {
        List<ShoppingListItemDTO> dtos = new ArrayList<>();
        if (items != null) {
            for (ShoppingListItem item : items) {
                dtos.add(toItemDTO(item));
            }
        }
        return dtos;
    }

    public ShoppingListDTO toDTO(ShoppingList list) {
        if (list == null) {
            return null;
        }

        User creator = list.getCreator();
        String creatorName = creator != null ? getDisplayName(creator) : "Unknown";

        List<Long> sharedWithIds = new ArrayList<>();
        for (User u : list.getSharedWith()) {
            sharedWithIds.add(u.getId());
        }

        List<ShoppingListItemDTO> itemDTOs = toItemDTOList(list.getItems());

        int pendingCount = 0;
        int boughtCount = 0;
        for (ShoppingListItemDTO item : itemDTOs) {
            if (item.bought()) {
                boughtCount++;
            } else {
                pendingCount++;
            }
        }

        return new ShoppingListDTO(list.getId(), list.getName(), creator != null ? creator.getId() : null, creatorName,
                list.isShared(), sharedWithIds, itemDTOs, itemDTOs.size(), pendingCount, boughtCount);
    }

    public List<ShoppingListDTO> toDTOList(List<ShoppingList> lists) {
        List<ShoppingListDTO> dtos = new ArrayList<>();
        if (lists != null) {
            for (ShoppingList list : lists) {
                dtos.add(toDTO(list));
            }
        }
        return dtos;
    }

    private String getDisplayName(User user) {
        if (user == null) {
            return "Unknown";
        }
        String name = user.getName();
        if (user.getSurname() != null && !user.getSurname().isEmpty()) {
            name += " " + user.getSurname();
        }
        return name;
    }
}
