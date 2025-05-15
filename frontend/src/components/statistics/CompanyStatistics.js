import React, { useEffect, useState } from "react";
import {
    BarChart,
    Bar,
    PieChart,
    Pie,
    Cell,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    Legend,
} from "recharts";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { customFetch } from "../../utils/api";
import { API_URL } from "../../config/apiConfig";
import "./CompanyDashboard.css";
import { exportCompanyStatisticsToExcel } from "../../utils/exportCompanyStatisticsToExcel";

const COLORS = ["#0088FE", "#00C49F", "#FFBB28", "#FF8042", "#8884D8", "#82ca9d"];
const RADIAN = Math.PI / 180;
const renderCustomizedLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent }) => {
    const radius = innerRadius + (outerRadius - innerRadius) * 0.5;
    const x = cx + radius * Math.cos(-midAngle * RADIAN);
    const y = cy + radius * Math.sin(-midAngle * RADIAN);

    return (
        <text x={x} y={y} fill="white" textAnchor={x > cx ? "start" : "end"} dominantBaseline="central">
            {`${(percent * 100).toFixed(0)}%`}
        </text>
    );
};

const CompanyStatistics = () => {
    const { companyId } = useParams();
    const navigate = useNavigate();
    const [stats, setStats] = useState(null);
    const [selectedFilter, setSelectedFilter] = useState("day");
    const [error, setError] = useState(null);
    const [customStart, setCustomStart] = useState(null);
    const [customEnd, setCustomEnd] = useState(null);

    useEffect(() => {
        fetchStats();
    }, [selectedFilter, customStart, customEnd]);

    const fetchStats = async () => {
        try {
            let url = `${API_URL}/api/statistics/by-company/${companyId}`;

            if (customStart && customEnd) {
                url += `?startDate=${customStart}&endDate=${customEnd}`;
            } else {
                url += `?filter=${selectedFilter}`;
            }

            const res = await customFetch(url);
            setStats(res);
        } catch (err) {
            setError("No se pudieron cargar las estadísticas.");
        }
    };

    if (error) return <div className="dashboard-container">{error}</div>;
    if (!stats) return <div className="dashboard-container">Cargando estadísticas...</div>;

    const branches = stats.branches || [];
    const revenueByCategory = Object.entries(stats.revenueByCategory || {}).map(([name, value]) => ({ name, value }));

    return (
        <div className="dashboard-container">
            <div className="dashboard-header">
                <h1 className="dashboard-title">Estadísticas Empresa #{companyId}</h1>
                <p className="dashboard-subtitle">Métricas generales y rendimiento por sucursal</p>
            </div>

            <div className="filter-container">
                {/* filtro rápido */}
                {["day", "week", "month", "year"].map(f => (
                    <button
                        key={f}
                        className={`filter-btn ${selectedFilter === f ? "active" : ""}`}
                        onClick={() => {
                            setSelectedFilter(f);
                            setCustomStart(null); // Limpia fechas personalizadas
                            setCustomEnd(null);
                        }}
                    >
                        {f === "day" ? "📅 Día" :
                            f === "week" ? "📆 Semana" :
                                f === "month" ? "📅 Mes" : "📆 Año"}
                    </button>
                ))}

                {/* filtro manual */}
                <input type="date" value={customStart || ""} onChange={(e) => { setCustomStart(e.target.value); setSelectedFilter(null); }} />
                <input type="date" value={customEnd || ""} onChange={(e) => {
                    setCustomEnd(e.target.value);
                    setSelectedFilter(null); // Desmarca el filtro rápido
                }} />
            </div>

            {/* Métricas */}
            <div className="metrics-grid">
                <MetricCard title="Total Ventas" value={stats.totalSalesCount} desc="Número total de ventas" />
                <MetricCard title="Ingresos Totales" value={`$${stats.totalRevenue}`} desc="Suma total recaudada" />
                <MetricCard title="Productos Vendidos" value={stats.totalProductsSold} desc="Cantidad total vendida" />
                <MetricCard title="Ticket Promedio" value={`$${stats.averageTicket}`} desc="Ingreso promedio por venta" />
                <MetricCard title="Sucursales Activas" value={stats.activeBranchesCount} desc="Con al menos una venta" />
                <MetricCard title="Prom. por Sucursal" value={`$${stats.averageSalesPerBranch}`} desc="Ingresos / sucursal activa" />
                <MetricCard title="Ventas Canceladas" value={stats.cancelledSalesCount} desc="Ventas anuladas" />
                <MetricCard title="Día con más ventas" value={stats.topDayOfWeek} desc="Mayor volumen de ventas" />
            </div>

            {/* Gráficos */}
            <div className="charts-grid">
                <ChartCard title="Ventas por Sucursal" desc="Cantidad de ventas por sucursal">
                    <BarChartComponent data={branches} dataKey="totalSalesCount" />
                </ChartCard>
                <ChartCard title="Ingresos por Sucursal" desc="Total de ingresos por sucursal">
                    <BarChartComponent data={branches} dataKey="totalRevenue" prefix="$" />
                </ChartCard>
            </div>

            <div className="charts-grid">
                <ChartCard title="Productos Vendidos por Sucursal" desc="Proporción de productos vendidos">
                    <PieChartComponent data={branches.map(b => ({ name: b.branchName, value: b.totalProductsSold }))} />
                </ChartCard>
                <ChartCard title="Ingresos por Categoría" desc="Distribución por tipo de producto">
                    <PieChartComponent data={revenueByCategory} prefix="$" />
                </ChartCard>
            </div>
            <button
                className="popup-btn popup-btn-save"
                onClick={() =>
                    exportCompanyStatisticsToExcel(
                        stats,
                        stats.companyName || `Empresa_${companyId}`,
                        customStart,
                        customEnd
                    )
                }
            >
                📥 Exportar a Excel
            </button>
            <div style={{ marginTop: 24 }}>
                <button onClick={() => navigate("/superadmin-dashboard")} className="popup-btn popup-btn-cancel">
                    ⬅ Volver al Dashboard
                </button>
            </div>
        </div>
    );
};

const MetricCard = ({ title, value, desc }) => (
    <div className="metric-card">
        <div className="card-header">
            <h3 className="card-title">{title}</h3>
        </div>
        <div className="card-content">
            <div className="metric-value">{value}</div>
            <p className="metric-description">{desc}</p>
        </div>
    </div>
);

const ChartCard = ({ title, desc, children }) => (
    <div className="chart-card">
        <div className="card-header">
            <h3 className="card-title">{title}</h3>
            <p className="card-description">{desc}</p>
        </div>
        <div className="card-content chart-container">{children}</div>
    </div>
);

const BarChartComponent = ({ data, dataKey, prefix = "" }) => (
    <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="branchName" />
            <YAxis />
            <Tooltip formatter={(v) => [`${prefix}${v}`, ""]} />
            <Bar dataKey={dataKey} fill="#0088FE" />
        </BarChart>
    </ResponsiveContainer>
);

const PieChartComponent = ({ data, prefix = "" }) => (
    <ResponsiveContainer width="100%" height="100%">
        <PieChart>
            <Pie
                data={data}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={renderCustomizedLabel}
                outerRadius={150}
                fill="#8884d8"
                dataKey="value"
            >
                {data.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
            </Pie>
            <Tooltip formatter={(v) => [`${prefix}${v}`, ""]} />
            <Legend />
        </PieChart>
    </ResponsiveContainer>
);

export default CompanyStatistics;