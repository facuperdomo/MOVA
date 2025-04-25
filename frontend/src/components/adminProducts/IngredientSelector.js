import React, { useState, useEffect } from "react";
import { X } from "lucide-react";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import "./ingredientSelectorStyle.css";

export default function IngredientSelector({ onClose, onSave, initial = [] }) {
  const [all, setAll] = useState([]);
  const [picked, setPicked] = useState(initial);

  useEffect(() => {
    customFetch(`${API_URL}/api/ingredients`)
      .then(setAll)
      .catch(console.error);
  }, []);

  const toggle = id =>
    setPicked(p =>
      p.includes(id) ? p.filter(x=>x!==id) : [...p, id]
    );

  return (
    <div className="popup-overlay">
      <div className="popup-content large">
        <X className="popup-close" onClick={onClose} />
        <h2>Selecciona Ingredientes</h2>
        <div className="ingredient-list">
          {all.map(ing => (
            <label key={ing.id} className="ingredient-item">
              <input
                type="checkbox"
                checked={picked.includes(ing.id)}
                onChange={() => toggle(ing.id)}
              />
              {ing.name}
            </label>
          ))}
        </div>
        <button className="save-btn" onClick={() => onSave(picked)}>Guardar</button>
      </div>
    </div>
  );
}