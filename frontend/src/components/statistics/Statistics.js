// src/components/statistics/Statistics.js
import React, { useState, useEffect } from "react";
import { Chart, registerables } from "chart.js";
import { Bar, Pie } from "react-chartjs-2";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, X } from "lucide-react";
import { customFetch } from "../../utils/api";
import "./statisticsStyle.css";
import { API_URL } from '../../config/apiConfig';

Chart.register(...registerables);

const Statistics = () => {
  const navigate = useNavigate();

  // Estados
  const [salesData, setSalesData] = useState([]);
  const [topProducts, setTopProducts] = useState([]);
  const [cashRegisterHistory, setCashRegisterHistory] = useState([]);
  const [selectedFilter, setSelectedFilter] = useState("day");
  const [selectedOption, setSelectedOption] = useState("sales");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showPopup, setShowPopup] = useState(false);
  const [saleToCancel, setSaleToCancel] = useState(null);

  useEffect(() => {
    if (selectedOption === "sales") fetchSalesData();
    if (selectedOption === "top-products") fetchTopProducts();
    if (selectedOption === "cash-register") fetchCashRegisterHistory();
  }, [selectedFilter, selectedOption]);

  // Obtener estadísticas de ventas
  const fetchSalesData = async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `${API_URL}/api/statistics/sales?filter=${selectedFilter}`;
      const response = await customFetch(url);
      if (!Array.isArray(response)) throw new Error("La respuesta del servidor no es un array");
      setSalesData(response);
    } catch (err) {
      setError("No se pudieron cargar las estadísticas.");
      setSalesData([]);
    } finally {
      setLoading(false);
    }
  };

  // Obtener los tragos más vendidos
  const fetchTopProducts = async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `${API_URL}/api/statistics/top-selling-products?filter=${selectedFilter}`;
      const response = await customFetch(url);
      if (!Array.isArray(response)) throw new Error("La respuesta del servidor no es un array");
      setTopProducts(response);
    } catch (err) {
      setError("Hubo un error al obtener los productos más vendidos.");
      setTopProducts([]);
    } finally {
      setLoading(false);
    }
  };

  // Obtener el historial de caja
  const fetchCashRegisterHistory = async () => {
    setLoading(true);
    setError(null);
    try {
      const url = `${API_URL}/api/statistics/cash-register-history?filter=${selectedFilter}`;
      const response = await customFetch(url);
      if (!Array.isArray(response)) throw new Error("La respuesta del servidor no es un array");
      setCashRegisterHistory(response);
    } catch (err) {
      setError("No se pudo cargar el historial de caja.");
      setCashRegisterHistory([]);
    } finally {
      setLoading(false);
    }
  };

  // Formatear fecha correctamente
  const formatDate = (dateString) => {
    if (!dateString) return "Fecha no disponible";
    return dateString.replace("T", " ").replace(/-/g, "/");
  };

  // Datos para gráfico de ventas activas
  const salesChartData = {
    labels: salesData
      .filter(s => s.estado === "ACTIVA")
      .map(s => formatDate(s.date)),
    datasets: [{
      label: "Ventas Activas",
      data: salesData
        .filter(s => s.estado === "ACTIVA")
        .map(s => s.total),
      backgroundColor: "rgba(54, 162, 235, 0.5)",
      borderColor: "rgba(54, 162, 235, 1)",
      borderWidth: 1,
    }],
  };

  // Colores aleatorios para top-products
  const generateColors = (n) => Array.from({ length: n }, () =>
    `hsl(${Math.floor(Math.random() * 360)}, 70%, 60%)`
  );
  const colors = generateColors(topProducts.length);
  const topProductsChartData = {
    labels: topProducts.map(p => p.name),
    datasets: [{
      label: "Cantidad Vendida",
      data: topProducts.map(p => p.totalSold),
      backgroundColor: colors,
      borderColor: colors,
      borderWidth: 1,
    }],
  };

  // Abrir popup para cancelar venta
  const handleCancelSale = (sale) => {
    setSaleToCancel(sale);
    setShowPopup(true);
  };
  // Confirmar cancelación
  const confirmCancelSale = async () => {
    if (!saleToCancel) return;
    try {
      const response = await fetch(
        `${API_URL}/api/statistics/cancel-sale/${saleToCancel.id}`,
        {
          method: "PUT",
          headers: {
            Authorization: `Bearer ${localStorage.getItem("token")}`,
            "Content-Type": "application/json",
          },
        }
      );
      if (response.ok) fetchSalesData();
    } catch (err) {
      console.error("❌ Error cancelando venta:", err);
    } finally {
      setShowPopup(false);
      setSaleToCancel(null);
    }
  };

  return (
    <div className="statistics-page">
      <nav className="sidebar">
        <ul>
          <li onClick={() => navigate("/admin-options")}>
            <ArrowLeft size={24} />
          </li>
          <li
            className={selectedOption === "sales" ? "active" : ""}
            onClick={() => setSelectedOption("sales")}
          >
            📊
          </li>
          <li
            className={selectedOption === "top-products" ? "active" : ""}
            onClick={() => setSelectedOption("top-products")}
          >
            🍸
          </li>
          <li
            className={selectedOption === "cash-register" ? "active" : ""}
            onClick={() => setSelectedOption("cash-register")}
          >
            💰
          </li>
        </ul>
      </nav>

      <div className="statistics-content">
        <div className="filter-container">
          {["day", "week", "month", "year"].map(f => (
            <button
              key={f}
              className={selectedFilter === f ? "active" : ""}
              onClick={() => setSelectedFilter(f)}
            >
              {f === "day" ? "📅 Día" :
               f === "week" ? "📆 Semana" :
               f === "month" ? "📅 Mes" : "📆 Año"}
            </button>
          ))}
        </div>

        {selectedOption === "sales" && (
          <>
            <div className="chart-container">
              <Bar data={salesChartData} />
            </div>

            <table className="sales-table">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Total</th>
                  <th>Estado</th>
                  <th>Modificar</th>
                </tr>
              </thead>
              <tbody>
                {/** Aquí invertimos el array para ver primero las ventas más recientes */}
                {[...salesData]
                  .reverse()
                  .map(sale => (
                    <tr
                      key={sale.id}
                      className={sale.estado === "CANCELADA" ? "cancelada" : ""}
                    >
                      <td>{formatDate(sale.date)}</td>
                      <td>${sale.total}</td>
                      <td>
                        {sale.estado === "CANCELADA"
                          ? "❌ Cancelada"
                          : "✅ Activa"}
                      </td>
                      <td>
                        {sale.estado !== "CANCELADA" && (
                          <button
                            className="cancel-button"
                            onClick={() => handleCancelSale(sale)}
                          >
                            ❌ Cancelar
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </>
        )}

        {selectedOption === "top-products" && (
          <div className="chart-container">
            <Pie data={topProductsChartData} />
          </div>
        )}

        {selectedOption === "cash-register" && (
          <>
            <div className="chart-container">
              <Bar
                data={{
                  labels: cashRegisterHistory.map(r => formatDate(r.openDate)),
                  datasets: [{
                    label: "Ventas Totales",
                    data: cashRegisterHistory.map(r => r.totalSales || 0),
                    backgroundColor: "rgba(255, 99, 132, 0.5)",
                    borderColor: "rgba(255, 99, 132, 1)",
                    borderWidth: 1,
                  }],
                }}
              />
            </div>

            <table className="sales-table">
              <thead>
                <tr>
                  <th>Apertura</th>
                  <th>Cierre</th>
                  <th>Total Ventas</th>
                </tr>
              </thead>
              <tbody>
                {cashRegisterHistory.map(reg => (
                  <tr key={reg.id}>
                    <td>{formatDate(reg.openDate)}</td>
                    <td>
                      {reg.closeDate
                        ? formatDate(reg.closeDate)
                        : "Abierta"}
                    </td>
                    <td>${reg.totalSales}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}

        {showPopup && (
          <div className="popup-overlay">
            <div className="popup-content">
              <X
                className="popup-close"
                size={32}
                onClick={() => setShowPopup(false)}
              />
              <h2>¿Seguro que quieres cancelar esta venta?</h2>
              <p>Monto: ${saleToCancel?.total}</p>
              <p>Fecha: {formatDate(saleToCancel?.date)}</p>
              <div className="popup-buttons">
                <button
                  className="popup-btn popup-btn-cash"
                  onClick={confirmCancelSale}
                >
                  ✅ Confirmar
                </button>
                <button
                  className="popup-btn popup-btn-qr"
                  onClick={() => setShowPopup(false)}
                >
                  ❌ Cancelar
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Statistics;
