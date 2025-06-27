// src/components/common/SelectDeviceModal.js
import React from "react";
import { X } from "lucide-react";
import "./selectDeviceModalStyle.css";

export default function SelectDeviceModal({ devices, onSelect }) {
  return (
    <div className="select-device-overlay">
      <div className="select-device-content">
        <div className="select-device-header">
          <h2>Selecciona tu terminal</h2>
          <X
            className="select-device-close"
            size={24}
            onClick={() => onSelect(null)}
          />
        </div>
        <ul className="select-device-list">
          {devices.map(d => (
            <li key={d.id}>
              <button onClick={() => onSelect(d.id)}>
                {d.name || d.bridgeUrl}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
