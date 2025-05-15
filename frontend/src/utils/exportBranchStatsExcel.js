import * as XLSX from "xlsx";
import { saveAs } from "file-saver";

export function exportBranchStatisticsToExcel(stats, topProducts, branchName = "Sucursal", startDate = null, endDate = null) {
    const wb = XLSX.utils.book_new();

    // ──────────────── Estilo base para bordes ────────────────
    const borderStyle = {
        top: { style: "thin", color: { rgb: "CCCCCC" } },
        bottom: { style: "thin", color: { rgb: "CCCCCC" } },
        left: { style: "thin", color: { rgb: "CCCCCC" } },
        right: { style: "thin", color: { rgb: "CCCCCC" } }
    };

    // ──────────────── Hoja 1: Resumen ────────────────
    const metricsData = [
        ["Rango de fechas", startDate && endDate ? `${startDate} a ${endDate}` : "Filtro aplicado: Último período"],
        [],
        ["Métrica", "Valor"],
        ["Total de Ventas", stats.totalSalesCount],
        ["Ingresos Totales", `$${stats.totalRevenue}`],
        ["Productos Vendidos", stats.totalProductsSold],
        ["Ticket Promedio", `$${stats.averageTicket}`],
        ["Ventas Canceladas", stats.cancelledSalesCount],
        ["Día con más ventas", stats.topDayOfWeek],
    ];
    const metricsSheet = XLSX.utils.aoa_to_sheet(metricsData);

    // Aplicar estilos a la hoja de métricas
    const range = XLSX.utils.decode_range(metricsSheet['!ref']);
    for (let R = range.s.r; R <= range.e.r; ++R) {
        for (let C = range.s.c; C <= range.e.c; ++C) {
            const cellRef = XLSX.utils.encode_cell({ r: R, c: C });
            const cell = metricsSheet[cellRef];
            if (!cell) continue;

            // Encabezados
            if (R === 2) {
                cell.s = {
                    font: { bold: true },
                    fill: { fgColor: { rgb: "D9D9D9" } },
                    border: borderStyle
                };
            } else {
                cell.s = { border: borderStyle };
            }
        }
    }

    XLSX.utils.book_append_sheet(wb, metricsSheet, "Resumen");

    // ──────────────── Hoja 2: Top Productos ────────────────
    const topData = topProducts.map(p => ({
        Producto: p.name,
        Cantidad: p.quantity
    }));
    const topSheet = XLSX.utils.json_to_sheet(topData);

    // Estilos para hoja de productos
    const topRange = XLSX.utils.decode_range(topSheet['!ref']);
    for (let R = topRange.s.r; R <= topRange.e.r; ++R) {
        for (let C = topRange.s.c; C <= topRange.e.c; ++C) {
            const cellRef = XLSX.utils.encode_cell({ r: R, c: C });
            const cell = topSheet[cellRef];
            if (!cell) continue;

            // Primera fila = encabezado
            if (R === 0) {
                cell.s = {
                    font: { bold: true },
                    fill: { fgColor: { rgb: "D9D9D9" } },
                    border: borderStyle
                };
            } else {
                cell.s = { border: borderStyle };
            }
        }
    }

    XLSX.utils.book_append_sheet(wb, topSheet, "Top Productos");

    // ──────────────── Exportar archivo ────────────────
    const filename = `Estadisticas_${branchName.replace(/\s+/g, "_")}.xlsx`;
    const buffer = XLSX.write(wb, {
        bookType: "xlsx",
        type: "array",
        cellStyles: true,
    });

    const blob = new Blob([buffer], { type: "application/octet-stream" });
    saveAs(blob, filename);
}
