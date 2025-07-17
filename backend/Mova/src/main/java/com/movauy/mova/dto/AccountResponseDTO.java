package com.movauy.mova.dto;

import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.ingredient.Ingredient;
import java.util.List;
import java.util.stream.Collectors;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountResponseDTO {

    private Long id;
    private String name;
    private boolean closed;
    private List<AccountItemDTO> items;

    public static AccountResponseDTO from(Account account) {
        List<AccountItemDTO> items = account.getItems().stream()
                .map(item -> {
                    List<Long> ingrIds = item.getIngredients()
                            .stream()
                            .map(Ingredient::getId)
                            .toList();
                    return new AccountItemDTO(
                            item.getId(),
                            item.getProduct().getId(),
                            item.getQuantity(),
                            item.getIngredients().stream()
                                    .map(Ingredient::getId)
                                    .collect(Collectors.toList()),
                            item.isPaid()
                            
                    );
                })
                .toList();

        return new AccountResponseDTO(
                account.getId(),
                account.getName(),
                account.isClosed(),
                items
        );
    }
}
