// src/components/AddMesaModal.jsx
import React from "react";
import ReactDOM from "react-dom";
import "./addMesaModalStyle.css"; // tus estilos dedicados

export default function AddMesaModal({ open, onClose, onCreate, newName, setNewName }) {
  if (!open) return null;

  return ReactDOM.createPortal(
    <div className="add-mesa-overlay" onClick={onClose}>
      <div className="add-mesa-container" onClick={e => e.stopPropagation()}>
        <button className="add-mesa-close" onClick={onClose}>✕</button>
        <h2>Agregar Cuenta</h2>
        <div className="add-mesa-form">
          <input
            type="text"
            placeholder="Ej: Cuenta 1"
            value={newName}
            onChange={e => setNewName(e.target.value)}
          />
          <button onClick={onCreate}>Crear</button>
        </div>
      </div>
    </div>,
    document.body
  );
}
