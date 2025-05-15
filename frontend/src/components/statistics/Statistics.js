// src/components/statistics/Statistics.js
import React, { useState, useEffect } from "react";
import { Chart, registerables } from "chart.js";
import { Bar, Pie } from "react-chartjs-2";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, X, Download } from "lucide-react";
import { customFetch } from "../../utils/api";
import "./statisticsStyle.css";
import { API_URL } from '../../config/apiConfig';
import { exportStatisticsToExcel } from "../../utils/exportStatisticsExcel";

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
  const [customStart, setCustomStart] = useState("");
  const [customEnd, setCustomEnd] = useState("");

  useEffect(() => {
    if (!customStart && !customEnd) {
      if (selectedOption === "sales") fetchSalesData();
      if (selectedOption === "top-products") fetchTopProducts();
      if (selectedOption === "cash-register") fetchCashRegisterHistory();
    }
  }, [selectedFilter, selectedOption]);

  useEffect(() => {
    // Ejecuta solo si ambas fechas están completas
    if (customStart && customEnd) {
      if (selectedOption === "sales") fetchSalesData();
      if (selectedOption === "top-products") fetchTopProducts();
      if (selectedOption === "cash-register") fetchCashRegisterHistory();
    }
  
    // Si alguna está vacía, limpiar los datos para no mantener anteriores
    if ((customStart && !customEnd) || (!customStart && customEnd)) {
      setSalesData([]);
      setTopProducts([]);
      setCashRegisterHistory([]);
    }
  }, [customStart, customEnd, selectedOption]);

  // Obtener estadísticas de ventas
  const fetchSalesData = async () => {
    setLoading(true);
    setError(null);
    try {
      let url = `${API_URL}/api/statistics/sales`;

      if (customStart && customEnd) {
        url += `?startDate=${customStart}&endDate=${customEnd}`;
      } else {
        url += `?filter=${selectedFilter}`;
      }

      const response = await customFetch(url);

      // ✔️ Validación para limpiar si viene vacío
      if (!Array.isArray(response) || response.length === 0) {
        setSalesData([]);
        return;
      }

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
      let url = `${API_URL}/api/statistics/top-selling-products`;
      if (customStart && customEnd) {
        url += `?startDate=${customStart}&endDate=${customEnd}`;
      } else {
        url += `?filter=${selectedFilter}`;
      }

      const response = await customFetch(url);
      if (!Array.isArray(response) || response.length === 0) {
        setTopProducts([]);
        return;
      }
      setTopProducts(response); // 👈 acá también tenías setSalesData, estaba mal
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
      let url = `${API_URL}/api/statistics/cash-register-history`;
      if (customStart && customEnd) {
        url += `?startDate=${customStart}&endDate=${customEnd}`;
      } else {
        url += `?filter=${selectedFilter}`;
      }

      const response = await customFetch(url);
      if (!Array.isArray(response) || response.length === 0) {
        setCashRegisterHistory([]);
        return;
      }
      setCashRegisterHistory(response); // ✅ ahora correcto
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
      data: topProducts.map(p => p.quantity),
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
          <li className={selectedOption === "sales" ? "active" : ""} onClick={() => setSelectedOption("sales")}>📊</li>
          <li className={selectedOption === "top-products" ? "active" : ""} onClick={() => setSelectedOption("top-products")}>🍸</li>
          <li className={selectedOption === "cash-register" ? "active" : ""} onClick={() => setSelectedOption("cash-register")}>💰</li>
        </ul>
      </nav>
  
      <div className="statistics-content">
        <div className="filter-container">
          <div className="custom-date-filters">
            <input
              type="date"
              value={customStart}
              onChange={(e) => {
                setCustomStart(e.target.value);
                setSelectedFilter(null);
              }}
            />
            <input
              type="date"
              value={customEnd}
              onChange={(e) => {
                setCustomEnd(e.target.value);
                setSelectedFilter(null);
              }}
            />
          </div>
          {["day", "week", "month", "year"].map(f => (
            <button
              key={f}
              className={selectedFilter === f ? "active" : ""}
              onClick={() => {
                setSelectedFilter(f);
                setCustomStart("");
                setCustomEnd("");
              }}
            >
              {f === "day" ? "📅 Día" :
                f === "week" ? "📆 Semana" :
                  f === "month" ? "📅 Mes" : "📆 Año"}
            </button>
          ))}
        </div>
  
        {error && <div className="error-message">{error}</div>}
  
        <button
          className="floating-export-btn"
          onClick={() => exportStatisticsToExcel({ sales: salesData, topProducts, history: cashRegisterHistory })}
        >
          <Download size={20} /> Exportar
        </button>
  
        {selectedOption === "sales" && (
          <>
            {salesData.length === 0 && !loading && (
              <div className="empty-message">No hay ventas en este período.</div>
            )}
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
                {[...salesData].reverse().map(sale => (
                  <tr key={sale.id} className={sale.estado === "CANCELADA" ? "cancelada" : ""}>
                    <td>{formatDate(sale.date)}</td>
                    <td>${sale.total}</td>
                    <td>{sale.estado === "CANCELADA" ? "❌ Cancelada" : "✅ Activa"}</td>
                    <td>
                      {sale.estado !== "CANCELADA" && (
                        <button className="cancel-button" onClick={() => handleCancelSale(sale)}>❌ Cancelar</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
  
        {selectedOption === "top-products" && (
          <>
            {topProducts.length === 0 && !loading && (
              <div className="empty-message">No hay productos vendidos en este período.</div>
            )}
            <div className="chart-container">
              <Pie data={topProductsChartData} />
            </div>
          </>
        )}
  
        {selectedOption === "cash-register" && (
          <>
            {cashRegisterHistory.length === 0 && !loading && (
              <div className="empty-message">No hay historial de caja en este período.</div>
            )}
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
                    <td>{reg.closeDate ? formatDate(reg.closeDate) : "Abierta"}</td>
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
              <X className="popup-close" size={32} onClick={() => setShowPopup(false)} />
              <h2>¿Seguro que quieres cancelar esta venta?</h2>
              <p>Monto: ${saleToCancel?.total}</p>
              <p>Fecha: {formatDate(saleToCancel?.date)}</p>
              <div className="popup-buttons">
                <button className="popup-btn popup-btn-cash" onClick={confirmCancelSale}>✅ Confirmar</button>
                <button className="popup-btn popup-btn-qr" onClick={() => setShowPopup(false)}>❌ Cancelar</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
  
};

export default Statistics;
