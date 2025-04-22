import React, { useEffect, useState } from "react";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import { X } from "lucide-react";
import "./categoryManagerStyle.css";

const CategoryManager = ({ onClose }) => {
  const [categories, setCategories] = useState([]);
  const [newCategory, setNewCategory] = useState("");
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
    if (!newCategory.trim()) return;
    try {
      await customFetch(`${API_URL}/api/categories`, {
        method: "POST",
        body: JSON.stringify({ name: newCategory.trim() }),
      });
      setNewCategory("");
      fetchCategories();
    } catch (err) {
      console.error("❌ Error al crear categoría:", err);
      setError("No se pudo crear la categoría.");
    }
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
      await customFetch(`${API_URL}/api/categories/${id}`, {
        method: "DELETE",
      });
      fetchCategories();
      setConfirmDelete(null);
    } catch (err) {
      console.error("❌ Error al eliminar categoría:", err);
      setError("No se pudo eliminar la categoría.");
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  return (
    <div className="popup-overlay" onClick={(e) => e.target.classList.contains("popup-overlay") && onClose()}>
      <div className="popup-content">
        <X className="popup-close" onClick={onClose} />
        <h2>Categorías de Producto</h2>

        {error && <p style={{ color: "red", marginBottom: "10px" }}>{error}</p>}

        <input
          type="text"
          placeholder="Nueva categoría"
          value={newCategory}
          onChange={(e) => setNewCategory(e.target.value)}
        />
        <button className="popup-btn popup-btn-cash" onClick={createCategory}>
          Crear
        </button>

        <ul className="category-list">
          {categories.length > 0 ? (
            categories.map((cat) => (
              <li key={cat.id}>
                {cat.name}
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
              La categoría <strong>{confirmDelete.name}</strong> tiene productos asociados. ¿Deseas eliminarla de todas
              formas? Los productos quedarán sin categoría.
            </p>
            <div className="popup-buttons">
              <button className="popup-btn popup-btn-cash" onClick={() => deleteCategory(confirmDelete.id)}>
                ✅ Sí, eliminar
              </button>
              <button className="popup-btn popup-btn-qr" onClick={() => setConfirmDelete(null)}>
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
