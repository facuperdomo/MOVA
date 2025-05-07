import React, { useEffect, useState } from "react";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import { X } from "lucide-react";
import "./categoryManagerStyle.css";

const CategoryManager = ({ onClose }) => {
  const [categories, setCategories] = useState([]);
  const [newCategory, setNewCategory] = useState("");
  const [newHasIngredients, setNewHasIngredients] = useState(false);
  const [newEnableKitchenCommands, setNewEnableKitchenCommands] = useState(false);
  const [error, setError] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const fetchCategories = async () => {
    try {
      const res = await customFetch(`${API_URL}/api/categories`);
      setCategories(Array.isArray(res) ? res : []);
    } catch (err) {
      console.error("❌ Error al obtener categorías:", err);
      setError("No se pudieron cargar las categorías.");
    }
  };

  const createCategory = async () => {
    if (!newCategory.trim()) return setError("Escribe un nombre");
    try {
      await customFetch(`${API_URL}/api/categories`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: newCategory.trim(),
          hasIngredients: newHasIngredients,
          enableKitchenCommands: newEnableKitchenCommands
        }),
      });
      setNewCategory("");
      setNewHasIngredients(false);
      setNewEnableKitchenCommands(false);
      setError(null);
      fetchCategories();
    } catch (err) {
      console.error("❌ Error al crear categoría:", err);
      setError("No se pudo crear la categoría.");
    }
  };

  const updateCategoryFlags = async (cat, updates) => {
    try {
      await customFetch(`${API_URL}/api/categories/${cat.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: cat.name,
          hasIngredients: updates.hasIngredients,
          enableKitchenCommands: updates.enableKitchenCommands
        }),
      });
      fetchCategories();
    } catch (err) {
      console.error("❌ Error al actualizar categoría:", err);
      setError("No se pudo actualizar la categoría.");
    }
  };

  const toggleHasIngredients = (cat) => {
    updateCategoryFlags(cat, {
      hasIngredients: !cat.hasIngredients,
      enableKitchenCommands: cat.enableKitchenCommands
    });
  };

  const toggleKitchenCommands = (cat) => {
    updateCategoryFlags(cat, {
      hasIngredients: cat.hasIngredients,
      enableKitchenCommands: !cat.enableKitchenCommands
    });
  };

  const handleDeleteClick = async (category) => {
    try {
      const response = await customFetch(`${API_URL}/api/categories/${category.id}/has-products`);
      if (response.hasProducts) {
        setConfirmDelete(category);
      } else {
        await deleteCategory(category.id);
      }
    } catch (err) {
      console.error("❌ Error al verificar productos:", err);
      setError("No se pudo verificar si la categoría tiene productos.");
    }
  };

  const deleteCategory = async (id) => {
    try {
      await customFetch(`${API_URL}/api/categories/${id}`, { method: "DELETE" });
      setConfirmDelete(null);
      fetchCategories();
    } catch (err) {
      console.error("❌ Error al eliminar categoría:", err);
      setError("No se pudo eliminar la categoría.");
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  return (
    <div
      className="popup-overlay"
      onClick={e => e.target.classList.contains("popup-overlay") && onClose()}
    >
      <div className="popup-content">
        <X className="popup-close" onClick={onClose} />
        <h2>Categorías de Producto</h2>

        {error && <p className="form-error">{error}</p>}

        <div className="category-form">
          <div className="input-category-row">
            <input
              type="text"
              placeholder="Nueva categoría"
              value={newCategory}
              onChange={e => setNewCategory(e.target.value)}
            />
          </div>

          <div className="checkbox-row">
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={newHasIngredients}
                onChange={e => setNewHasIngredients(e.target.checked)}
              />
              Maneja ingredientes
            </label>
          </div>

          <div className="checkbox-row">
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={newEnableKitchenCommands}
                onChange={e => setNewEnableKitchenCommands(e.target.checked)}
              />
              Requiere comandería
            </label>
          </div>

          <div className="checkbox-row">
            <button
              className="popup-btn popup-btn-cash"
              onClick={createCategory}
            >
              Crear
            </button>
          </div>
        </div>

        <ul className="category-list">
          {categories.length > 0 ? (
            categories.map(cat => (
              <li key={cat.id} className="category-item">
                <span>{cat.name}</span>
                <label className="inline-checkbox">
                  <input
                    type="checkbox"
                    checked={cat.hasIngredients}
                    onChange={() => toggleHasIngredients(cat)}
                  />
                  Ingredientes
                </label>
                <label className="inline-checkbox">
                  <input
                    type="checkbox"
                    checked={cat.enableKitchenCommands}
                    onChange={() => toggleKitchenCommands(cat)}
                  />
                  Comandería
                </label>
                <button onClick={() => handleDeleteClick(cat)}>🗑️</button>
              </li>
            ))
          ) : (
            <li>No hay categorías aún.</li>
          )}
        </ul>

        {confirmDelete && (
          <div className="popup-warning">
            <p>
              La categoría <strong>{confirmDelete.name}</strong> tiene productos
              asociados. ¿Deseas eliminarla de todas formas? Los productos quedarán sin categoría.
            </p>
            <div className="popup-buttons">
              <button
                className="popup-btn popup-btn-cash"
                onClick={() => deleteCategory(confirmDelete.id)}
              >
                ✅ Sí, eliminar
              </button>
              <button
                className="popup-btn popup-btn-qr"
                onClick={() => setConfirmDelete(null)}
              >
                ❌ Cancelar
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CategoryManager;
