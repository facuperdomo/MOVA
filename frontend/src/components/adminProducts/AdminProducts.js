import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, X } from "lucide-react"; // Iconos para volver y cerrar popup
import "./adminProductsStyle.css";
import { customFetch } from "../../utils/api";

const AdminProducts = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [image, setImage] = useState(null);
  const [imageName, setImageName] = useState("Seleccionar Imagen");
  const [editingId, setEditingId] = useState(null);
  const [loading, setLoading] = useState(true);
  
  // Estado para manejar el popup de eliminación
  const [showDeletePopup, setShowDeletePopup] = useState(false);
  const [productToDelete, setProductToDelete] = useState(null);

  useEffect(() => {
    fetchProducts();
  }, []);

  const fetchProducts = async () => {
    try {
      const response = await customFetch("http://localhost:8080/api/products");
      if (!Array.isArray(response)) throw new Error("La respuesta no es un array");

      const productsWithImages = response.map((product) => ({
        ...product,
        imageUrl: product.image ? `data:image/png;base64,${product.image}` : null,
      }));

      setProducts(productsWithImages);
    } catch (error) {
      console.error("❌ Error al obtener productos:", error);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!name.trim() || !price) {
      alert("Por favor, completa todos los campos.");
      return;
    }

    const formData = new FormData();
    formData.append("name", name);
    formData.append("price", price);

    if (image) {
      formData.append("image", image);
    } else if (editingId) {
      const existingProduct = products.find((p) => p.id === editingId);
      if (existingProduct && existingProduct.imageUrl) {
        const response = await fetch(existingProduct.imageUrl);
        const blob = await response.blob();
        formData.append("image", blob, "currentImage.png");
      }
    }

    const method = editingId ? "PUT" : "POST";
    const url = editingId
      ? `http://localhost:8080/api/products/${editingId}`
      : "http://localhost:8080/api/products";

    const token = localStorage.getItem("token");

    try {
      await fetch(url, {
        method,
        body: formData,
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      fetchProducts();
      resetForm();
    } catch (error) {
      console.error("❌ Error al guardar producto:", error);
    }
  };

  const handleEdit = (product) => {
    setName(product.name);
    setPrice(product.price);
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
    if (!productToDelete) return;

    const token = localStorage.getItem("token");
    try {
      await fetch(`http://localhost:8080/api/products/${productToDelete.id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
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
    setImage(null);
    setImageName("Seleccionar Imagen");
    setEditingId(null);
  };

  return (
    <div className="admin-page">
      {/* 🔹 Botón de Volver */}
      <div className="dashboard-sidebar" onClick={() => navigate("/admin-options")}>
        <ArrowLeft size={40} className="back-icon" />
      </div>

      {/* Contenedor de Formulario */}
      <div className="form-container">
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
                setImageName(e.target.files[0] ? e.target.files[0].name : "Seleccionar Imagen");
              }}
              required={!editingId}
            />
          </label>
          <button type="submit">{editingId ? "Guardar Cambios" : "Agregar"}</button>
          {editingId && <button type="button" onClick={resetForm}>Cancelar</button>}
        </form>
      </div>

      {/* Contenedor de Productos */}
      <div className="products-container">
        <h2 className="static-title">Lista de Productos</h2>
        <div className="products-grid-admin">
          {loading ? (
            <p>Cargando productos...</p>
          ) : (
            products.map((product) => (
              <div key={product.id} className="product-item">
                {product.imageUrl && (
                  <img src={product.imageUrl} alt={product.name} className="product-image" />
                )}
                <h3>{product.name}</h3>
                <p>${product.price}</p>
                <div className="product-actions">
                  <button className="edit-btn" onClick={() => handleEdit(product)}>✏️ Editar</button>
                  <button className="delete-btn" onClick={() => confirmDelete(product)}>🗑️ Eliminar</button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* 🔹 Popup de Confirmación de Eliminación */}
      {showDeletePopup && (
        <div className="popup-overlay">
          <div className="popup-content">
            <X className="popup-close" size={32} onClick={() => setShowDeletePopup(false)} />
            <h2>¿Eliminar producto?</h2>
            <p>¿Estás seguro de que deseas eliminar <strong>{productToDelete?.name}</strong>?</p>
            <div className="popup-buttons">
              <button className="popup-btn popup-btn-cash" onClick={handleDelete}>
                ✅ Confirmar
              </button>
              <button className="popup-btn popup-btn-qr" onClick={() => setShowDeletePopup(false)}>
                ❌ Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminProducts;
