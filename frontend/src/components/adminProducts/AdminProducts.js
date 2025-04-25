import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, X } from "lucide-react";
import "./adminProductsStyle.css";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import CategoryManager from "./CategoryManager";
import IngredientManager from "./IngredientManager";
import IngredientSelector from "./IngredientSelector";

const AdminProducts = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [image, setImage] = useState(null);
  const [imageName, setImageName] = useState("Seleccionar Imagen");
  const [editingId, setEditingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showDeletePopup, setShowDeletePopup] = useState(false);
  const [productToDelete, setProductToDelete] = useState(null);
  const [showCategoryPopup, setShowCategoryPopup] = useState(false);
  const [categories, setCategories] = useState([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState("");
  const [formError, setFormError] = useState("");
  const [filterCategoryId, setFilterCategoryId] = useState(null);
  const [enableIngredients, setEnableIngredients] = useState(false);
  const [enableKitchenCommands, setEnableKitchenCommands] = useState(false);
  const [showIngredientPopup, setShowIngredientPopup] = useState(false);

  const [selectedIngredients, setSelectedIngredients] = useState([]);
  const [showIngredientSelection, setShowIngredientSelection] = useState(false);

  const [editingIngProductId, setEditingIngProductId] = useState(null);
  const [selectedIngredientsForEditing, setSelectedIngredientsForEditing] = useState([]);

  useEffect(() => {
    fetchProducts();
    fetchCategories();
  }, []);

  useEffect(() => {
    const cat = categories.find(c => c.id === selectedCategoryId);
    if (!editingId && cat?.hasIngredients) {
      setShowIngredientSelection(true);
    }
  }, [selectedCategoryId]);

  useEffect(() => {
    (async () => {
      try {
        const me = await customFetch(`${API_URL}/auth/me`, {
          headers: { Authorization: `Bearer ${localStorage.getItem("token")}` }
        });
        console.log("👤 Me:", me);
        setEnableIngredients(me.enableIngredients);
        setEnableKitchenCommands(me.enableKitchenCommands);
        console.log("🔧 enableIngredients:", me.enableIngredients);
      } catch (err) {
        console.error("No pude leer settings de la empresa:", err);
      }
    })();
  }, []);

  const openIngredientEditor = (product) => {
    setEditingIngProductId(product.id);
    // asumimos que el back te devuelve en cada product un array "ingredients" de DTOs {id,name}
    setSelectedIngredientsForEditing(product.ingredients.map(i => i.id));
  };

  const fetchProducts = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/products`);
      const productsWithImages = response.map((product) => ({
        ...product,
        imageUrl: product.image ? `data:image/png;base64,${product.image}` : null,
        category: {
          id: product.categoryId,
          name: product.categoryName,
        },
      }));
      setProducts(productsWithImages);
    } catch (error) {
      console.error("❌ Error al obtener productos:", error);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchCategories = async () => {
    try {
      const response = await customFetch(`${API_URL}/api/categories`);
      setCategories(Array.isArray(response) ? response : []);
    } catch (error) {
      console.error("❌ Error al obtener categorías:", error);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError("");

    // 1. Trim del nombre y validación básica
    const nameTrimmed = name.trim();
    if (!nameTrimmed || !price) {
      setFormError("Completa todos los campos.");
      return;
    }

    // 2. Validación de nombre duplicado (solo al crear, no al editar)
    if (!editingId && products.some(p => p.name.toLowerCase() === nameTrimmed.toLowerCase())) {
      setFormError("Ya existe un producto con ese nombre.");
      return;
    }

    // 3. Validación de categoría
    if (!selectedCategoryId) {
      setFormError("Selecciona una categoría.");
      return;
    }

    // 4. Validación de imagen
    if (!image && !editingId) {
      setFormError("Selecciona una imagen.");
      return;
    }

    // 5. Construcción del FormData
    const formData = new FormData();
    formData.append("name", nameTrimmed);
    formData.append("price", price);
    formData.append("categoryId", selectedCategoryId.toString());

    if (image) {
      formData.append("image", image);
    } else if (editingId) {
      // si estamos editando, reutilizo la imagen actual
      const product = products.find(p => p.id === editingId);
      if (product?.imageUrl) {
        try {
          const resp = await fetch(product.imageUrl);
          const blob = await resp.blob();
          formData.append("image", blob, "currentImage.png");
        } catch {
          setFormError("Hubo un problema al cargar la imagen.");
          return;
        }
      } else {
        setFormError("No se encontró imagen previa del producto.");
        return;
      }
    }

    if (selectedIngredients.length) {
      selectedIngredients.forEach(id => {
        formData.append("ingredientIds", id);
      });
    }

    // 6. Envío al backend
    const url = `${API_URL}/api/products${editingId ? `/${editingId}` : ""}`;
    const method = editingId ? "PUT" : "POST";

    try {
      await fetch(url, {
        method,
        body: formData,
        headers: {
          Authorization: `Bearer ${localStorage.getItem("token")}`,
        },
      });
      fetchProducts();
      resetForm();
    } catch (error) {
      console.error("❌ Error al guardar producto:", error);
      setFormError("Error al guardar producto.");
    }
  };



  const handleEdit = (product) => {
    setName(product.name);
    setPrice(product.price);
    setSelectedCategoryId(product.category?.id || null);
    setEditingId(product.id);
    setImage(null);
    setImageName(product.imageUrl ? "Imagen actual" : "Seleccionar Imagen");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const confirmDelete = (product) => {
    setProductToDelete(product);
    setShowDeletePopup(true);
  };

  const handleDelete = async () => {
    try {
      await fetch(`${API_URL}/api/products/${productToDelete.id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${localStorage.getItem("token")}` },
      });
      fetchProducts();
      setShowDeletePopup(false);
    } catch (error) {
      console.error("❌ Error al eliminar producto:", error);
    }
  };

  const resetForm = () => {
    setName("");
    setPrice("");
    setSelectedCategoryId("");
    setImage(null);
    setImageName("Seleccionar Imagen");
    setEditingId(null);
  };

  return (
    <div className="admin-products-page">
      {/* Sidebar fijo */}
      <div className="dashboard-sidebar" onClick={() => navigate("/admin-options")}>
        <ArrowLeft size={40} className="back-icon" />
      </div>

      {/* Contenedor principal */}
      <div className="main-content-admin">
        {/* Formulario */}
        <div className="form-panel">
          <h2>{editingId ? "Modificar Producto" : "Agregar Producto"}</h2>
          <form onSubmit={handleSubmit}>
            <input
              type="text"
              placeholder="Nombre del producto"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <input
              type="number"
              placeholder="Precio"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              required
            />
            <label className="file-input-label">
              {image ? image.name : imageName}
              <input
                type="file"
                accept="image/*"
                onChange={(e) => {
                  setImage(e.target.files[0]);
                  setImageName(e.target.files[0]?.name || "Seleccionar Imagen");
                }}

              />
            </label>

            <button type="submit">{editingId ? "Guardar Cambios" : "Agregar"}</button>
            {formError && (
              <div className="form-error">
                {formError}
              </div>
            )}
            {editingId && <button type="button" onClick={resetForm}>Cancelar</button>}
          </form>
        </div>

        {/* Panel de categorías */}
        <div className="category-panel">
          <h3>Categorías:</h3>
          <ul>
            {categories.map((cat) => (
              <li
                key={cat.id}
                className={selectedCategoryId === cat.id ? "selected-category" : ""}
                onClick={() => setSelectedCategoryId(cat.id)}
              >
                {cat.name}
              </li>
            ))}
          </ul>
          <button className="category-manage-btn" onClick={() => setShowCategoryPopup(true)}>
            🗂️ Gestionar Cat.
          </button>
        </div>
      </div>
      {/* Sólo si la empresa tiene habilitados ingredientes */}
      {enableIngredients && (
        <button
          className="manage-ingredients-btn"
          onClick={() => setShowIngredientPopup(true)}
        >
          🥬 Administrar Ingredientes
        </button>
      )}

      {/* Productos */}
      <div className="products-container">
        <h2 className="static-title">Lista de Productos</h2>

        {/* Tabs de categorías */}
        <div className="category-tabs">
          <button
            className={!filterCategoryId ? "active-tab" : ""}
            onClick={() => setFilterCategoryId(null)}
          >
            Todos
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              className={filterCategoryId === cat.id ? "active-tab" : ""}
              onClick={() => setFilterCategoryId(cat.id)}
            >
              {cat.name}
            </button>
          ))}
        </div>

        {/* Lista de productos filtrados */}
        <div className="products-grid-admin">
          {loading ? (
            <p>Cargando productos...</p>
          ) : (
            products
              .filter((product) => !filterCategoryId || product.category?.id === filterCategoryId)
              .map((product) => (
                <div key={product.id} className="product-item">
                  {product.imageUrl && (
                    <img src={product.imageUrl} alt={product.name} className="product-image" />
                  )}
                  <h3>{product.name}</h3>
                  <p>${product.price}</p>
                  <p style={{ fontSize: "0.8rem", color: "#aaa" }}>
                    {product.category?.name || "Sin categoría"}
                  </p>
                  <div className="product-actions">
                    <button className="edit-btn" onClick={() => handleEdit(product)}>
                      ✏️ Editar
                    </button>
                    <button className="delete-btn" onClick={() => confirmDelete(product)}>
                      🗑️ Eliminar
                    </button>
                    {categories.find(c => c.id === product.category.id)?.hasIngredients && (
                      <button
                        className="ing-btn"
                        onClick={() => openIngredientEditor(product)}
                      >
                        🧂 Ingredientes
                      </button>
                    )}
                  </div>
                </div>
              ))
          )}
        </div>
      </div>

      {/* Popups */}
      {showDeletePopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={() => setShowDeletePopup(false)} />
            <h2>¿Eliminar producto?</h2>
            <p>¿Estás seguro de que deseas eliminar <strong>{productToDelete?.name}</strong>?</p>
            <div className="popup-buttons">
              <button className="popup-btn popup-btn-cash" onClick={handleDelete}>✅ Confirmar</button>
              <button className="popup-btn popup-btn-qr" onClick={() => setShowDeletePopup(false)}>❌ Cancelar</button>
            </div>
          </div>
        </div>
      )}

      {showCategoryPopup && (
        <CategoryManager
          onClose={() => {
            setShowCategoryPopup(false);
            fetchCategories(); // 🔄 Actualiza cuando se cierra
          }}
        />
      )}

      {showIngredientPopup && (
        <IngredientManager
          onClose={() => {
            setShowIngredientPopup(false);
            // si quieres recargar productos o algo, hazlo aquí
          }}
        />
      )}

      {showIngredientSelection && (
        <IngredientSelector
          initial={selectedIngredients}
          onSave={ings => {
            setSelectedIngredients(ings);
            setShowIngredientSelection(false);
          }}
          onClose={() => setShowIngredientSelection(false)}
        />
      )}

{ editingIngProductId !== null && (
  <IngredientSelector
    initial={selectedIngredientsForEditing}
    onSave={async ings => {
      // Patch a tu endpoint de ingredientes
      await customFetch(
        `${API_URL}/api/products/${editingIngProductId}/ingredients`,
        {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("token")}`
          },
          body: JSON.stringify(ings)
        }
      );
      setEditingIngProductId(null);
      fetchProducts();
    }}
    onClose={() => setEditingIngProductId(null)}
  />
) }
    </div>
  );
};

export default AdminProducts;
