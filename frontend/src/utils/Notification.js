// utils/Notification.jsx
import { useState, useEffect } from "react";
import { CheckCircle, XCircle } from 'lucide-react';

function Notification({ message, type, onClose, style }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const t1 = setTimeout(() => setVisible(true), 20);
    const t2 = setTimeout(() => setVisible(false), 4020);
    const t3 = setTimeout(onClose, 4520);
    return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); };
  }, []);

  return (
    <div
      className={`notification ${type} ${visible ? 'show' : 'hide'}`}
      style={style}
    >
      {type === 'success'
        ? <CheckCircle size={24} className="icon" />
        : <XCircle size={24} className="icon" />}
      <span>{message}</span>
    </div>
  );
}

export default Notification;
