package com.movauy.mova.model.sale;

import com.movauy.mova.model.sale.SaleItem;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sale_item_ingredient")
public class SaleItemIngredient {
  @Id @GeneratedValue private Long id;

  @ManyToOne @JoinColumn(name="sale_item_id")
  private SaleItem saleItem;

  private Long ingredientId; // almacena sólo el ID, o incluso enlaza a tu entidad Ingredient
}
