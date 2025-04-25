import React, { useState, useEffect } from "react";
import { X } from "lucide-react";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import "./ingredientManagerStyle.css";

export default function IngredientManager({ onClose }) {
  const [ingredients, setIngredients] = useState([]);
  const [newName, setNewName] = useState("");
  const [error, setError] = useState("");
  const [fullNamePopup, setFullNamePopup] = useState(null);

  useEffect(() => {
    fetchIngredients();
  }, []);

  const fetchIngredients = async () => {
    try {
      const data = await customFetch(`${API_URL}/api/ingredients`);
      setIngredients(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error("Error al cargar ingredientes:", err);
    }
  };

  const handleAdd = async () => {
    if (!newName.trim()) return setError("Escribe un nombre");
    try {
      await customFetch(`${API_URL}/api/ingredients`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
        body: JSON.stringify({ name: newName }),
      });
      setNewName("");
      setError("");
      fetchIngredients();
    } catch (err) {
      console.error("Error al agregar:", err);
      setError("No se pudo agregar");
    }
  };

  const handleDelete = async (id) => {
    try {
      await customFetch(`${API_URL}/api/ingredients/${id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${localStorage.getItem("token")}` },
      });
      fetchIngredients();
    } catch (err) {
      console.error("Error al borrar:", err);
    }
  };

  const showFullName = (name) => {
    setFullNamePopup(name);
  };

  const closeFullName = () => {
    setFullNamePopup(null);
  };

  return (
    <div className="popup-overlay">
      <div className="popup-content large">
        <X className="popup-close" size={24} onClick={onClose} />
        <h2>Gestionar Ingredientes</h2>

        <div className="ingredient-form">
          <input
            type="text"
            placeholder="Nuevo ingrediente"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <button onClick={handleAdd}>➕ Agregar</button>
        </div>

        <div className="ingredient-list">
          {ingredients.length === 0 && <p>No hay ingredientes aún.</p>}
          {ingredients.map((ing) => (
            <div key={ing.id} className="ingredient-item">
              <span
                className="ingredient-name"
                title={ing.name}
                onClick={() => showFullName(ing.name)}
              >
                {ing.name}
              </span>
              <button
                className="delete-btn"
                onClick={() => handleDelete(ing.id)}
              >
                🗑️
              </button>
            </div>
          ))}
        </div>

        {error && <div className="form-error">{error}</div>}
      </div>

      {fullNamePopup && (
        <div className="full-name-overlay" onClick={closeFullName}>
          <div className="full-name-box">
            <p>{fullNamePopup}</p>
            <button className="close-fullname-btn" onClick={closeFullName}>
              Cerrar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
