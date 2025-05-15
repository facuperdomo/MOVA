import * as XLSX from "xlsx";
import { saveAs } from "file-saver";

/**
 * Exporta estadísticas completas de empresa a un archivo Excel
 * @param {Object} stats - Objeto de estadísticas devuelto por el backend
 * @param {string} companyName - Nombre de la empresa
 * @param {string|null} startDate - Fecha de inicio (YYYY-MM-DD) o null
 * @param {string|null} endDate - Fecha de fin (YYYY-MM-DD) o null
 */
export function exportCompanyStatisticsToExcel(stats, companyName = "Empresa", startDate = null, endDate = null) {
    const wb = XLSX.utils.book_new();

    // ───── Info de fechas
    const fechaTitulo = startDate && endDate
        ? `Del ${startDate} al ${endDate}`
        : `Filtro aplicado: ${stats.filterLabel || 'N/A'}`;

    // ───── 1) Métricas Generales
    const resumenData = [
        ["Estadísticas Generales", ""],
        ["Rango Analizado", fechaTitulo],
        [],
        ["Métrica", "Valor"],
        ["Total de Ventas", stats.totalSalesCount],
        ["Ingresos Totales", `$${stats.totalRevenue}`],
        ["Productos Vendidos", stats.totalProductsSold],
        ["Ticket Promedio", `$${stats.averageTicket}`],
        ["Sucursales Activas", stats.activeBranchesCount],
        ["Promedio por Sucursal", `$${stats.averageSalesPerBranch}`],
        ["Ventas Canceladas", stats.cancelledSalesCount],
        ["Día con más ventas", stats.topDayOfWeek]
    ];
    const resumenSheet = XLSX.utils.aoa_to_sheet(resumenData);
    XLSX.utils.book_append_sheet(wb, resumenSheet, "Resumen");

    // ───── 2) Detalle por Sucursal
    const branchesData = [
        ["Sucursal", "Ventas", "Ingresos", "Productos Vendidos"]
    ];
    for (const branch of stats.branches || []) {
        branchesData.push([
            branch.branchName,
            branch.totalSalesCount,
            `$${branch.totalRevenue}`,
            branch.totalProductsSold
        ]);
    }
    const branchSheet = XLSX.utils.aoa_to_sheet(branchesData);
    XLSX.utils.book_append_sheet(wb, branchSheet, "Por Sucursal");

    // ───── 3) Ingresos por Categoría
    const categoryData = [
        ["Categoría", "Ingresos"]
    ];
    for (const [cat, val] of Object.entries(stats.revenueByCategory || {})) {
        categoryData.push([cat, `$${val}`]);
    }
    const catSheet = XLSX.utils.aoa_to_sheet(categoryData);
    XLSX.utils.book_append_sheet(wb, catSheet, "Por Categoría");

    // ───── Exportar archivo
    const filename = `Estadisticas_${companyName.replace(/\s+/g, "_")}.xlsx`;
    const buffer = XLSX.write(wb, { bookType: "xlsx", type: "array" });
    const blob = new Blob([buffer], { type: "application/octet-stream" });
    saveAs(blob, filename);
}
