package com.movauy.mova.dto;

import com.movauy.mova.model.account.Account;
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
                .map(item -> new AccountItemDTO(
                item.getId(),
                item.getProduct().getId(),
                item.getQuantity()
        ))
                .collect(Collectors.toList());

        return new AccountResponseDTO(account.getId(), account.getName(), account.isClosed(), items);
    }
}
