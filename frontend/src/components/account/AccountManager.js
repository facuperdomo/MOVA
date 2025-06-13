import React, { useState, useEffect } from 'react';
import { customFetch } from '../../utils/api';

export default function AccountManager() {
  const [accounts, setAccounts] = useState([]);
  const [branchId, setBranchId] = useState(null); // poné el ID real de la sucursal actual
  const [newAccountName, setNewAccountName] = useState('');

  useEffect(() => {
    if (branchId) loadAccounts();
  }, [branchId]);

  const loadAccounts = async () => {
    const res = await customFetch(`/api/accounts?branchId=${branchId}&closed=false`);
    const data = await res.json();
    setAccounts(data);
  };

  const createAccount = async () => {
    const res = await customFetch('/api/accounts', {
      method: 'POST',
      body: JSON.stringify({ branchId, name: newAccountName }),
    });
    if (res.ok) {
      setNewAccountName('');
      loadAccounts();
    }
  };

  return (
    <div>
      <h2>Cuentas Abiertas</h2>

      <div>
        <input
          type="text"
          value={newAccountName}
          onChange={(e) => setNewAccountName(e.target.value)}
          placeholder="Nombre de la cuenta (Ej: Mesa 1)"
        />
        <button onClick={createAccount}>Crear cuenta</button>
      </div>

      <div>
        {accounts.map((account) => (
          <div key={account.id} style={{ border: '1px solid #ccc', margin: '10px', padding: '10px' }}>
            <strong>{account.name}</strong> – ID: {account.id}
            <br />
            Productos: {account.items?.length || 0}
            <br />
            {/* Acá más adelante agregamos ver detalle, cerrar, eliminar */}
          </div>
        ))}
      </div>
    </div>
  );
}
