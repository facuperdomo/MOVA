import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { customFetch } from '../../utils/api';
import './superAdminDashboardStyle.css';

const SuperAdminDashboard = () => {
    const [companies, setCompanies] = useState([]);
    const [showPopup, setShowPopup] = useState(false);
    const [form, setForm] = useState({ name: '', contactEmail: '', contactPhone: '' });
    const [editingCompany, setEditingCompany] = useState(null);
    const [formError, setFormError] = useState('');
    const [showDeletePopup, setShowDeletePopup] = useState(false);
    const [companyToDelete, setCompanyToDelete] = useState(null);
    const [errorMessage, setErrorMessage] = useState('');
    const [branches, setBranches] = useState([]);
    const [selectedCompany, setSelectedCompany] = useState(null);
    const [showBranchesModal, setShowBranchesModal] = useState(false);
    const [showBranchForm, setShowBranchForm] = useState(false);
    const [branchForm, setBranchForm] = useState({
        name: '', username: '', password: '',
        mercadoPagoAccessToken: '', location: '', phone: '',
        enableIngredients: false, enableKitchenCommands: false
    });
    const [selectedBranch, setSelectedBranch] = useState(null);
    const [showBranchDeletePopup, setShowBranchDeletePopup] = useState(false);
    const [branchToDelete, setBranchToDelete] = useState(null);

    // — Usuarios por Sucursal —
    const [users, setUsers] = useState([]);
    const [showUserForm, setShowUserForm] = useState(false);
    const [userForm, setUserForm] = useState({ username: '', password: '', role: 'USER' });
    const [selectedUser, setSelectedUser] = useState(null);
    const [showUserDeletePopup, setShowUserDeletePopup] = useState(false);
    const [userToDelete, setUserToDelete] = useState(null);
    const [showUsersModal, setShowUsersModal] = useState(false);

    const [showForceDeleteBranchPopup, setShowForceDeleteBranchPopup] = useState(false);
    const [showForceDeleteCompanyPopup, setShowForceDeleteCompanyPopup] = useState(false);

    const [companyFilter, setCompanyFilter] = useState('all');
    const [branchFilter, setBranchFilter] = useState('all');
    const [companySearch, setCompanySearch] = useState('');
    const [showForceDisableCompanyModal, setShowForceDisableCompanyModal] = useState(false);
    const [companyToDisable, setCompanyToDisable] = useState(null);
    const [branchSearch, setBranchSearch] = useState('');

    const [companyStats, setCompanyStats] = useState(null);
    const [showStatsModal, setShowStatsModal] = useState(false);

    const navigate = useNavigate();

    useEffect(() => {
        fetchCompanies();
    }, []);

    const fetchCompanyStats = async (companyId) => {
        try {
            const stats = await customFetch(`/api/statistics/by-company/${companyId}`);
            setCompanyStats(stats);
            setShowStatsModal(true);
        } catch (err) {
            console.error('Error obteniendo estadísticas de empresa:', err);
            setErrorMessage('No se pudieron cargar las estadísticas.');
        }
    };

    const fetchCompanies = async () => {
        try {
            const res = await customFetch('/api/companies');
            const data = Array.isArray(res) ? res : [];
            // Normalizamos enabled a booleano puro:
            const normalized = data.map(c => ({
                ...c,
                enabled: c.enabled === true || c.enabled === 1 || c.enabled === '1'
            }));
            console.log('RAW companies:', data);
            console.log('NORMALIZED companies:', normalized);
            setCompanies(normalized);
        } catch (err) {
            console.error('Error cargando empresas:', err);
            setCompanies([]);
        }
    };

    const fetchBranchesForCompany = async (company) => {
        try {
            const res = await customFetch(`/api/branches?companyId=${company.id}`);
            const data = Array.isArray(res) ? res : [];

            data.forEach(b =>
                console.log(`branch #${b.id} raw enabled=`, b.enabled, `typeof=`, typeof b.enabled)
            );

            const normalized = data.map(b => ({
                ...b,
                enabled: String(b.enabled).toLowerCase() === 'true'
            }));
            setBranches(normalized);
            setSelectedCompany(company);
            setShowBranchesModal(true);
        } catch (err) {
            console.error('Error al traer sucursales:', err);
            setBranches([]);
        }
    };

    const visibleCompanies = companies
        .filter(c => {
            const isEnabled = !!c.enabled;
            if (companyFilter === 'enabled') return isEnabled;
            if (companyFilter === 'disabled') return !isEnabled;
            return true;
        })
        .filter(c => c.name.toLowerCase().includes(companySearch.toLowerCase()));

    const visibleBranches = branches
        .filter(b => {
            if (branchFilter === 'enabled') return b.enabled;
            if (branchFilter === 'disabled') return !b.enabled;
            return true;
        })
        .filter(b =>
            b.name.toLowerCase().includes(branchSearch.toLowerCase())
        );

    const handleLogout = () => {
        localStorage.clear();
        navigate('/login');
    };

    const handleToggleBranch = async (branch) => {
        try {
            // Hago el PUT invertido sobre enabled
            await customFetch(
                `/api/branches/${branch.id}/enabled?enabled=${!branch.enabled}`,
                { method: 'PUT' }
            );
            // Re-fetch para que venga siempre el valor actualizado
            fetchBranchesForCompany(selectedCompany);
        } catch (err) {
            console.error('Error toggling branch enabled:', err);
            const msg = err.data?.message || err.message || 'No se pudo cambiar el estado de la sucursal.';
            setErrorMessage(msg);
        }
    };

    const handleToggleCompany = async (c) => {
        // Si vamos a DESHABILITAR y hay sucursales habilitadas, pedimos confirmación
        if (c.enabled) {
            try {
                const res = await customFetch(`/api/branches?companyId=${c.id}`);
                const raw = Array.isArray(res) ? res : [];
                // normalizamos in-line para poder filtrar
                const enabledBranches = raw.filter(b =>
                    b.enabled === true || b.enabled === 1 || b.enabled === '1'
                );
                if (enabledBranches.length > 0) {
                    setCompanyToDisable(c);
                    return setShowForceDisableCompanyModal(true);
                }
            } catch (err) {
                console.error('Error comprobando sucursales:', err);
                // seguimos al toggle “normal”
            }
        }

        // O bien estamos habilitando o no hay sucursales habilitadas
        try {
            await customFetch(
                `/api/companies/${c.id}/enabled?enabled=${!c.enabled}`,
                { method: 'PUT' }
            );
            fetchCompanies();
        } catch (err) {
            console.error('Error toggling enabled:', err);
            const msg = err.data?.message || err.message || 'No se pudo cambiar el estado.';
            setErrorMessage(msg);
        }
    };

    const openAddPopup = () => {
        setEditingCompany(null);
        setForm({ name: '', contactEmail: '', contactPhone: '' });
        setFormError('');
        setShowPopup(true);
    };

    const openEditPopup = (company) => {
        setEditingCompany(company);
        setForm({
            name: company.name || '',
            contactEmail: company.contactEmail || '',
            contactPhone: company.contactPhone || ''
        });
        setFormError('');
        setShowPopup(true);
    };

    const closePopup = () => {
        setShowPopup(false);
        setEditingCompany(null);
        setForm({ name: '', contactEmail: '', contactPhone: '' });
        setFormError('');
    };

    const handleChange = e => {
        const { name, value, type, checked } = e.target;
        // decide qué setter usar según el modal abierto
        if (showPopup) {
            setForm(prev => ({ ...prev, [name]: value }));
        } else if (showBranchForm) {
            setBranchForm(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
        } else if (showUserForm) {
            setUserForm(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
        }
    };

    const handleBranchChange = (e) => {
        const { name, value, type, checked } = e.target;
        setBranchForm(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
    };

    const isValidEmail = email => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

    const handleSubmit = async () => {
        const { name, contactEmail, contactPhone } = form;
        if (!name.trim() || !contactEmail.trim() || !contactPhone.trim()) {
            setFormError('Todos los campos son obligatorios.');
            return;
        }
        if (!isValidEmail(contactEmail)) {
            setFormError('Email inválido.');
            return;
        }
        try {
            if (editingCompany) {
                await customFetch(`/api/companies/${editingCompany.id}`, { method: 'PUT', body: JSON.stringify(form) });
            } else {
                await customFetch('/api/companies', { method: 'POST', body: JSON.stringify(form) });
            }
            closePopup();
            fetchCompanies();
        } catch (err) {
            console.error('Error guardando empresa:', err);
            setFormError('Error al guardar la empresa.');
        }
    };

    const confirmDeleteCompany = (company) => {
        setCompanyToDelete(company);
        setShowDeletePopup(true);
    };

    const handleDeleteConfirmed = async () => {
        try {
            // primer intento SIN force
            await customFetch(`/api/companies/${companyToDelete.id}`, { method: 'DELETE' });
            fetchCompanies();
        } catch (err) {
            console.error('Error eliminando empresa:', err);
            // si viene nuestro conflicto "TieneSucursales"
            if (err.data?.error === 'TieneSucursales') {
                setShowForceDeleteCompanyPopup(true);
            } else {
                setErrorMessage('Ocurrió un error al eliminar la empresa.');
            }
        } finally {
            setShowDeletePopup(false);
        }
    };

    const openBranchForm = () => {
        setBranchForm({ name: '', username: '', password: '', mercadoPagoAccessToken: '', location: '', phone: '', enableIngredients: false, enableKitchenCommands: false });
        setSelectedBranch(null);
        setShowBranchForm(true);
    };

    const submitNewBranch = async () => {
        const payload = { ...branchForm, company: { id: selectedCompany.id } };
        if (selectedBranch && !branchForm.password) {
            delete payload.password;
        }
        try {
            if (selectedBranch) {
                await customFetch(`/api/branches/${selectedBranch.id}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
            } else {
                await customFetch('/api/branches', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
            }
            setShowBranchForm(false);
            fetchBranchesForCompany(selectedCompany);
        } catch (err) {
            console.error('Error al guardar sucursal:', err);
            setErrorMessage('Error al guardar sucursal.');
        }
    };

    const openEditBranch = (b) => {
        setSelectedBranch(b);
        setBranchForm({
            name: b.name,
            username: b.username,
            password: '',                                  // no pre-llenamos la contraseña
            mercadoPagoAccessToken: b.mercadoPagoAccessToken || '',
            location: b.location || '',
            phone: b.phone || '',
            enableIngredients: b.enableIngredients,
            enableKitchenCommands: b.enableKitchenCommands
        });
        setShowBranchForm(true);
    };

    const confirmDeleteBranch = (branch) => {
        setBranchToDelete(branch);
        setShowBranchDeletePopup(true);
    };

    const handleBranchDeleteConfirmed = async () => {
        try {
            // primer intento SIN force
            await customFetch(`/api/branches/${branchToDelete.id}`, { method: 'DELETE' });
            fetchBranchesForCompany(selectedCompany);
        } catch (err) {
            console.error('Error eliminando sucursal:', err);
            if (err.data?.error === 'TieneUsuarios') {
                // si viene nuestro conflicto, abrimos el modal de force-delete
                setShowForceDeleteBranchPopup(true);
            } else {
                setErrorMessage('Ocurrió un error al eliminar la sucursal.');
            }
        } finally {
            setShowBranchDeletePopup(false);
        }
    };

    const handleForceDeleteBranch = async () => {
        try {
            await customFetch(
                `/api/branches/${branchToDelete.id}?force=true`,
                { method: 'DELETE' }
            );
            fetchBranchesForCompany(selectedCompany);
        } catch (err) {
            console.error('Error forzando eliminación de sucursal:', err);
            setErrorMessage('No se pudo borrar la sucursal.');
        } finally {
            setShowForceDeleteBranchPopup(false);
            setBranchToDelete(null);
        }
    };

    const handleForceDeleteCompany = async () => {
        try {
            // segundo intento CON force=true
            await customFetch(
                `/api/companies/${companyToDelete.id}?force=true`,
                { method: 'DELETE' }
            );
            fetchCompanies();
        } catch (err) {
            console.error('Error forzando eliminación de empresa:', err);
            setErrorMessage('No se pudo borrar la empresa.');
        } finally {
            setShowForceDeleteCompanyPopup(false);
            setCompanyToDelete(null);
        }
    };

    const closeBranchForm = () => {
        setShowBranchForm(false);
        setSelectedBranch(null);
    };

    // —— CRUD Usuarios ——
    const fetchUsersForBranch = async (branch) => {
        try {
            const res = await customFetch(`/api/users?branchId=${branch.id}`);
            setUsers(Array.isArray(res) ? res : []);
            setSelectedBranch(branch);
            setShowBranchesModal(false);  // cierras sucursales
            setShowUsersModal(true);      // abres usuarios
        } catch {
            setUsers([]);
        }
    };

    const openAddUser = () => {
        setSelectedUser(null);
        setUserForm({ username: '', password: '', role: 'USER' });
        setShowUserForm(true);
    };

    const openEditUser = (u) => {
        setSelectedUser(u);
        setUserForm({ username: u.username, password: '', role: u.role });
        setShowUserForm(true);
    };

    const submitUser = async () => {
        // 1) Prepara y valida el payload
        const username = userForm.username.trim();
        if (!username) {
            setErrorMessage('El nombre de usuario no puede estar vacío.');
            return;
        }

        const payload = {
            username,
            password: userForm.password,
            role: userForm.role,
            branchId: selectedBranch.id
        };

        try {
            // 2) Llamada al API
            await customFetch(
                selectedUser
                    ? `/api/users/${selectedUser.id}`
                    : '/api/users',
                {
                    method: selectedUser ? 'PUT' : 'POST',
                    body: JSON.stringify(payload)
                }
            );

            // 3) Si todo OK: cierra modal y recarga lista
            setShowUserForm(false);
            fetchUsersForBranch(selectedBranch);

        } catch (err) {
            console.error('Error guardando usuario:', err);

            // 4) Extrae status / message de tu customFetch
            const status = err.status || err.response?.status;
            const message = err.data?.message || err.response?.data?.message;

            if (status === 409) {
                // conflicto: nombre duplicado en esta sucursal
                setErrorMessage(message || 'Ya existe un usuario con ese nombre en esta sucursal.');
            } else {
                // cualquier otro error
                setErrorMessage('Error al guardar usuario.');
            }
        }
    };

    const confirmDeleteUser = (u) => {
        setUserToDelete(u);
        setShowUserDeletePopup(true);
    };

    const handleUserDeleteConfirmed = async () => {
        try {
            await customFetch(`/api/users/${userToDelete.id}`, { method: 'DELETE' });
            fetchUsersForBranch(selectedBranch);
        } catch { }
        setShowUserDeletePopup(false);
        setUserToDelete(null);
    };

    console.log('Todas las empresas en estado:', companies);
    console.log('Empresas tras aplicar filtro:', visibleCompanies);

    return (
        <div className="app-container">
            <div className="superadmin-wrapper">
                <div className="main-content">
                    <h2>Administración de Empresas</h2>
                    <div className="companies-table-container">
                        {/* Filtro de empresas */}
                        <div className="filter-bar">
                            <span>Filtros:</span>
                            <label>
                                <input
                                    type="radio"
                                    name="companyFilter"
                                    value="all"
                                    checked={companyFilter === 'all'}
                                    onChange={e => setCompanyFilter(e.target.value)}
                                /> Todas
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    name="companyFilter"
                                    value="enabled"
                                    checked={companyFilter === 'enabled'}
                                    onChange={e => setCompanyFilter(e.target.value)}
                                /> Habilitadas
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    name="companyFilter"
                                    value="disabled"
                                    checked={companyFilter === 'disabled'}
                                    onChange={e => setCompanyFilter(e.target.value)}
                                /> Deshabilitadas
                            </label>
                            <label className="search-label">
                                Buscar por nombre:
                                <input
                                    type="text"
                                    placeholder="Empresa..."
                                    value={companySearch}
                                    onChange={e => setCompanySearch(e.target.value)}
                                />
                            </label>
                        </div>

                        <button className="add-btn" onClick={openAddPopup}>Agregar Empresa</button>

                        <table className="companies-table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Nombre</th>
                                    <th>Email</th>
                                    <th>Teléfono</th>
                                    <th>Acciones</th>
                                </tr>
                            </thead>
                            <tbody>
                                {visibleCompanies.map(c => (
                                    <tr key={c.id}>
                                        <td>{c.id}</td>
                                        <td>{c.name}</td>
                                        <td>{c.contactEmail}</td>
                                        <td>{c.contactPhone}</td>
                                        <td className="actions-cell">
                                            <button
                                                className="toggle-btn"
                                                onClick={() => handleToggleCompany(c)}
                                            >
                                                {c.enabled ? 'Deshabilitar' : 'Habilitar'}
                                            </button>
                                            <button className="edit-btn" onClick={() => openEditPopup(c)}>Editar</button>
                                            <button className="delete-btn" onClick={() => confirmDeleteCompany(c)}>Eliminar</button>
                                            <button className="edit-btn" onClick={() => fetchBranchesForCompany(c)}>Ver Sucursales</button>
                                            <button className="edit-btn" onClick={() => navigate(`/company-statistics/${c.id}`)}>Ver Estadísticas</button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        {!companies.length && <div className="no-results">No hay empresas registradas.</div>}
                    </div>
                </div>
            </div>

            {/* Modal Crear/Editar Empresa */}
            {showPopup && (
                <div
                    className="popup-overlay"
                    onClick={e => e.target.classList.contains('popup-overlay') && closePopup()}
                >
                    <div className="popup-content">
                        <h3>{editingCompany ? 'Editar Empresa' : 'Nueva Empresa'}</h3>
                        <input
                            name="name"
                            placeholder="Nombre"
                            value={form.name}
                            onChange={handleChange}
                        />
                        <input
                            name="contactEmail"
                            placeholder="Email"
                            value={form.contactEmail}
                            onChange={handleChange}
                        />
                        <input
                            name="contactPhone"
                            placeholder="Teléfono"
                            value={form.contactPhone}
                            onChange={handleChange}
                        />
                        {formError && <div className="form-error">{formError}</div>}
                        <div className="popup-buttons">
                            <button className="popup-btn popup-btn-save" onClick={handleSubmit}>Guardar</button>
                            <button className="popup-btn popup-btn-cancel" onClick={closePopup}>Cancelar</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Confirmar eliminación de empresa */}
            {showDeletePopup && (
                <div className="popup-overlay">
                    <div className="popup-content confirm-modal">
                        <p>¿Seguro que quieres eliminar la empresa “{companyToDelete.name}”?</p>
                        <button onClick={handleDeleteConfirmed} className="popup-btn popup-btn-save">Sí, eliminar</button>
                        <button onClick={() => setShowDeletePopup(false)} className="popup-btn popup-btn-cancel">Cancelar</button>
                    </div>
                </div>
            )}

            <div className="logout-button-container">
                <div className="logout-button" onClick={handleLogout}>🚪</div>
            </div>

            {/* Modal Sucursales */}
            {showBranchesModal && (
                <div
                    className="popup-overlay"
                    onClick={e => e.target.classList.contains('popup-overlay') && setShowBranchesModal(false)}
                >
                    <div className="popup-content branch-modal">
                        <h3>Sucursales de {selectedCompany.name}</h3>

                        {/* Filtro de sucursales */}
                        <div className="filter-bar">
                            <span>Filtros:</span>

                            <label>
                                <input
                                    type="radio"
                                    name="branchFilter"
                                    value="all"
                                    checked={branchFilter === 'all'}
                                    onChange={e => setBranchFilter(e.target.value)}
                                />
                                Todas
                            </label>

                            <label>
                                <input
                                    type="radio"
                                    name="branchFilter"
                                    value="enabled"
                                    checked={branchFilter === 'enabled'}
                                    onChange={e => setBranchFilter(e.target.value)}
                                />
                                Habilitadas
                            </label>

                            <label>
                                <input
                                    type="radio"
                                    name="branchFilter"
                                    value="disabled"
                                    checked={branchFilter === 'disabled'}
                                    onChange={e => setBranchFilter(e.target.value)}
                                />
                                Deshabilitadas
                            </label>

                            <label className="search-label">
                                Buscar por nombre:
                                <input
                                    type="text"
                                    placeholder="Sucursal..."
                                    value={branchSearch}
                                    onChange={e => setBranchSearch(e.target.value)}
                                />
                            </label>
                        </div>

                        <table className="branch-table">
                            <thead>
                                <tr>
                                    <th>Nombre</th>
                                    <th>Ubicación</th>
                                    <th>Teléfono</th>
                                    <th>Acciones</th>
                                </tr>
                            </thead>
                            <tbody>
                                {visibleBranches.map(b => (
                                    <tr key={b.id}>
                                        <td>{b.name}</td>
                                        <td>{b.location}</td>
                                        <td>{b.phone}</td>
                                        <td className="actions-cell">
                                            <button
                                                className="toggle-btn"
                                                onClick={() => handleToggleBranch(b)}
                                            >
                                                {b.enabled ? 'Deshabilitar' : 'Habilitar'}
                                            </button>
                                            <button className="edit-btn" onClick={() => openEditBranch(b)}>Editar</button>
                                            <button className="delete-btn" onClick={() => confirmDeleteBranch(b)}>Eliminar</button>
                                            <button className="edit-btn" onClick={() => fetchUsersForBranch(b)}>Ver Usuarios</button>
                                            <button className="edit-btn" onClick={() => navigate(`/branch-statistics/${b.id}`)}>Ver Estadísticas</button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>

                        <button className="popup-btn popup-btn-save" onClick={openBranchForm}>Agregar Sucursal</button>
                    </div>
                </div>
            )}
            {/* ==== Modal Usuario (nuevo/editar) ==== */}
            {showUserForm && (
                <div className="popup-overlay user-form-overlay"
                    onClick={e => e.target.classList.contains('popup-overlay') && setShowUserForm(false)}>
                    <div className="popup-content user-form">
                        <h3>{selectedUser ? 'Editar Usuario' : 'Nuevo Usuario'}</h3>
                        <input
                            name="username"
                            placeholder="Usuario"
                            value={userForm.username}
                            onChange={handleChange}
                        />
                        <input
                            type="password"
                            name="password"
                            placeholder={selectedUser ? 'Deja vacío para no cambiar' : 'Contraseña'}
                            value={userForm.password}
                            onChange={handleChange}
                        />
                        <select name="role" value={userForm.role} onChange={handleChange}>
                            <option value="USER">USER</option>
                            <option value="ADMIN">ADMIN</option>
                        </select>
                        <div className="popup-buttons">
                            <button className="popup-btn popup-btn-save" onClick={submitUser}>
                                Guardar
                            </button>
                            <button className="popup-btn popup-btn-cancel" onClick={() => setShowUserForm(false)}>
                                Cancelar
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ==== Confirm Delete Usuario ==== */}
            {showUserDeletePopup && (
                <div className="popup-overlay confirm-modal-overlay">
                    <div className="popup-content confirm-modal">
                        <p>¿Seguro que quieres eliminar el usuario “{userToDelete.username}”?</p>
                        <button className="popup-btn popup-btn-save" onClick={handleUserDeleteConfirmed}>
                            Sí, eliminar
                        </button>
                        <button className="popup-btn popup-btn-cancel" onClick={() => setShowUserDeletePopup(false)}>
                            Cancelar
                        </button>
                    </div>
                </div>
            )}

            {/* Delete Branch Confirmation Popup */}
            {showBranchDeletePopup && (
                <div className="popup-overlay">
                    <div className="popup-content confirm-modal">
                        <p>¿Seguro que quieres eliminar la sucursal “{branchToDelete.name}”?</p>
                        <button onClick={handleBranchDeleteConfirmed} className="popup-btn popup-btn-save">Sí, eliminar</button>
                        <button onClick={() => setShowBranchDeletePopup(false)} className="popup-btn popup-btn-cancel">Cancelar</button>
                    </div>
                </div>
            )}

            {showBranchForm && (
                <div className="popup-overlay" onClick={e => e.target.classList.contains('popup-overlay') && setShowBranchForm(false)}>
                    <div className="popup-content branch-form">
                        <h3>{selectedBranch ? 'Editar Sucursal' : 'Agregar Sucursal'}</h3>
                        <input name="name" placeholder="Nombre" value={branchForm.name} onChange={handleBranchChange} />
                        <input name="username" placeholder="Usuario" value={branchForm.username} onChange={handleBranchChange} />
                        <input
                            type="password"
                            name="password"
                            placeholder={selectedBranch ? 'Deja vacío para no cambiar' : 'Contraseña'}
                            value={branchForm.password}
                            onChange={handleBranchChange}
                        />
                        <input name="mercadoPagoAccessToken" placeholder="AccessToken MP" value={branchForm.mercadoPagoAccessToken} onChange={handleBranchChange} />
                        <input name="location" placeholder="Ubicación" value={branchForm.location} onChange={handleBranchChange} />
                        <input name="phone" placeholder="Teléfono" value={branchForm.phone} onChange={handleBranchChange} />
                        <label><input type="checkbox" name="enableIngredients" checked={branchForm.enableIngredients} onChange={handleBranchChange} /> Habilitar Ingredientes</label>
                        <label><input type="checkbox" name="enableKitchenCommands" checked={branchForm.enableKitchenCommands} onChange={handleBranchChange} /> Comandas a Cocina</label>
                        <div className="popup-buttons">
                            <button className="popup-btn popup-btn-save" onClick={submitNewBranch}>Guardar</button>
                            <button className="popup-btn popup-btn-cancel" onClick={closeBranchForm}>Cancelar</button>
                        </div>
                    </div>
                </div>
            )}
            {showUsersModal && (
                <div
                    className="popup-overlay"
                    onClick={e => e.target.classList.contains('popup-overlay') && setShowUsersModal(false)}
                >
                    <div className="popup-content user-modal">
                        <h3>Usuarios de {selectedBranch.name}</h3>
                        <table className="branch-table">
                            <thead>
                                <tr><th>Usuario</th><th>Rol</th><th>Acciones</th></tr>
                            </thead>
                            <tbody>
                                {users.map(u => (
                                    <tr key={u.id}>
                                        <td>{u.username}</td>
                                        <td>{u.role}</td>
                                        <td>
                                            <button className="edit-btn" onClick={() => openEditUser(u)}>Editar</button>
                                            <button className="delete-btn" onClick={() => confirmDeleteUser(u)}>Eliminar</button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        <div className="popup-buttons">
                            <button className="popup-btn popup-btn-save" onClick={openAddUser}>Agregar Usuario</button>
                            <button className="popup-btn popup-btn-cancel" onClick={() => setShowUsersModal(false)}>Cerrar</button>
                        </div>
                    </div>
                </div>
            )}
            {errorMessage && (
                <div className="popup-overlay confirm-modal-overlay" onClick={() => setErrorMessage('')}>
                    <div className="popup-content">
                        <p>{errorMessage}</p>
                        <button
                            className="popup-btn popup-btn-cancel"
                            onClick={() => setErrorMessage('')}
                        >
                            Cerrar
                        </button>
                    </div>
                </div>
            )}
            {showForceDeleteBranchPopup && (
                <div className="popup-overlay confirm-modal-overlay">
                    <div className="popup-content confirm-modal">
                        <p>La sucursal tiene usuarios asociados. ¿Deseas eliminarla igualmente (se borrarán también los usuarios)?</p>
                        <button
                            className="popup-btn popup-btn-save"
                            onClick={handleForceDeleteBranch}
                        >
                            Sí, eliminar
                        </button>
                        <button
                            className="popup-btn popup-btn-cancel"
                            onClick={() => setShowForceDeleteBranchPopup(false)}
                        >
                            Cancelar
                        </button>
                    </div>
                </div>
            )}
            {showForceDeleteCompanyPopup && (
                <div className="popup-overlay confirm-modal-overlay">
                    <div className="popup-content confirm-modal">
                        <p>La empresa tiene sucursales asociadas. ¿Deseas eliminarla igualmente (se borrarán también sucursales y usuarios)?</p>
                        <button
                            className="popup-btn popup-btn-save"
                            onClick={handleForceDeleteCompany}
                        >
                            Sí, eliminar
                        </button>
                        <button
                            className="popup-btn popup-btn-cancel"
                            onClick={() => setShowForceDeleteCompanyPopup(false)}
                        >
                            Cancelar
                        </button>
                    </div>
                </div>
            )}
            {showForceDisableCompanyModal && companyToDisable && (
                <div className="popup-overlay">
                    <div className="popup-content confirm-modal">
                        <p>
                            La empresa “{companyToDisable.name}” tiene sucursales habilitadas.
                            Si la deshabilitas, también se deshabilitarán todas sus sucursales.
                            ¿Deseas continuar?
                        </p>
                        <button
                            className="popup-btn popup-btn-save"
                            onClick={async () => {
                                try {
                                    await customFetch(
                                        `/api/companies/${companyToDisable.id}/enabled?enabled=false&force=true`,
                                        { method: 'PUT' }
                                    );
                                    fetchCompanies();
                                } catch {
                                    setErrorMessage('No se pudo deshabilitar la empresa.');
                                } finally {
                                    setShowForceDisableCompanyModal(false);
                                    setCompanyToDisable(null);
                                }
                            }}
                        >
                            Sí, deshabilitar
                        </button>
                        <button
                            className="popup-btn popup-btn-cancel"
                            onClick={() => {
                                setShowForceDisableCompanyModal(false);
                                setCompanyToDisable(null);
                            }}
                        >
                            Cancelar
                        </button>
                    </div>
                </div>
            )}
            {showStatsModal && companyStats && (
                <div className="popup-overlay" onClick={() => setShowStatsModal(false)}>
                    <div className="popup-content">
                        <h3>Estadísticas Generales</h3>
                        <p><strong>Total de Ventas:</strong> {companyStats.totalSalesCount}</p>
                        <p><strong>Ingresos Totales:</strong> ${companyStats.totalRevenue}</p>
                        <p><strong>Productos Vendidos:</strong> {companyStats.totalProductsSold}</p>

                        <h4>Detalle por Sucursal:</h4>
                        {companyStats.branches.map(branch => (
                            <div key={branch.branchId} className="branch-card">
                                <p><strong>{branch.branchName}</strong></p>
                                <p>Ventas: {branch.totalSalesCount}</p>
                                <p>Ingresos: ${branch.totalRevenue}</p>
                                <p>Productos: {branch.totalProductsSold}</p>
                            </div>
                        ))}
                        <button className="popup-btn popup-btn-cancel" onClick={() => setShowStatsModal(false)}>Cerrar</button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default SuperAdminDashboard;
