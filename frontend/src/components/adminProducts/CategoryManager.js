// src/components/CategoryManager.jsx
import React, { useEffect, useState } from "react";
import { X } from "lucide-react";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import "./categoryManagerStyle.css";

export default function CategoryManager({ onClose }) {
  const [categories, setCategories] = useState([]);
  const [newCategory, setNewCategory] = useState("");
  const [newHasIngredients, setNewHasIngredients] = useState(false);
  const [newEnableKitchenCommands, setNewEnableKitchenCommands] = useState(false);
  const [error, setError] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      const res = await customFetch(`${API_URL}/api/categories`);
      setCategories(Array.isArray(res) ? res : []);
    } catch {
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
    } catch {
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
    } catch {
      setError("No se pudo actualizar la categoría.");
    }
  };

  const toggleHasIngredients = cat =>
    updateCategoryFlags(cat, {
      hasIngredients: !cat.hasIngredients,
      enableKitchenCommands: cat.enableKitchenCommands
    });

  const toggleKitchenCommands = cat =>
    updateCategoryFlags(cat, {
      hasIngredients: cat.hasIngredients,
      enableKitchenCommands: !cat.enableKitchenCommands
    });

  const handleDeleteClick = async cat => {
    try {
      const resp = await customFetch(
        `${API_URL}/api/categories/${cat.id}/has-products`
      );
      if (resp.hasProducts) setConfirmDelete(cat);
      else await deleteCategory(cat.id);
    } catch {
      setError("No se pudo verificar productos.");
    }
  };

  const deleteCategory = async id => {
    try {
      await customFetch(`${API_URL}/api/categories/${id}`, { method: "DELETE" });
      setConfirmDelete(null);
      fetchCategories();
    } catch {
      setError("No se pudo eliminar la categoría.");
    }
  };

  return (
    <div
      className="popup-overlay"
      onClick={e => e.target.classList.contains("popup-overlay") && onClose()}
    >
      {/* ─── MODAL DE CREACIÓN ─── */}
      <div className="popup-content create-panel">
        <X className="popup-close" onClick={onClose} />
        <h2>Categorías de Producto</h2>

        {error && <p className="form-error">{error}</p>}

        <div className="category-form">
          <input
            type="text"
            placeholder="Nueva categoría"
            value={newCategory}
            onChange={e => setNewCategory(e.target.value)}
          />
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={newHasIngredients}
              onChange={e => setNewHasIngredients(e.target.checked)}
            />
            Maneja ingredientes
          </label>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={newEnableKitchenCommands}
              onChange={e => setNewEnableKitchenCommands(e.target.checked)}
            />
            Requiere comandería
          </label>
          <button
            className="popup-btn popup-btn-cash"
            onClick={createCategory}
          >
            Crear
          </button>
        </div>
      </div>

      {/* ─── PANEL DE LISTADO ─── */}
      <div className="popup-content list-panel">
        <div className="side-header">
          <h3>Listado de Categorías</h3>
        </div>

        <ul className="category-list">
          {categories.length === 0 && <li>No hay categorías.</li>}
          {categories.map(cat => (
            <li key={cat.id} className="category-item">
              <span className="category-name">{cat.name}</span>
              <div className="actions-row">
                <label className="inline-checkbox">
                  <input
                    type="checkbox"
                    checked={cat.hasIngredients}
                    onChange={() => toggleHasIngredients(cat)}
                  />
                  Ingred.
                </label>
                <label className="inline-checkbox">
                  <input
                    type="checkbox"
                    checked={cat.enableKitchenCommands}
                    onChange={() => toggleKitchenCommands(cat)}
                  />
                  Comand.
                </label>
                <button
                  className="delete-btn"
                  onClick={() => handleDeleteClick(cat)}
                >
                  🗑️
                </button>
              </div>
            </li>
          ))}
        </ul>

        {confirmDelete && (
          <div className="popup-warning">
            <p>
              La categoría <strong>{confirmDelete.name}</strong> tiene productos.
              ¿Eliminarla igualmente?
            </p>
            <div className="popup-buttons">
              <button
                className="popup-btn popup-btn-cash"
                onClick={() => deleteCategory(confirmDelete.id)}
              >
                ✅ Sí
              </button>
              <button
                className="popup-btn popup-btn-qr"
                onClick={() => setConfirmDelete(null)}
              >
                ❌ No
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
