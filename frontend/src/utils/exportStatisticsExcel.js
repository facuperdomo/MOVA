import * as XLSX from "xlsx";
import { saveAs } from "file-saver";

export function exportStatisticsToExcel({ sales, topProducts, history }) {
  const wb = XLSX.utils.book_new();

  // Hoja 1: Ventas
  const salesData = [
    ["Fecha", "Total", "Estado"],
    ...sales.map(s => [formatDate(s.date), s.total, s.estado]),
  ];
  const salesSheet = XLSX.utils.aoa_to_sheet(salesData);
  XLSX.utils.book_append_sheet(wb, salesSheet, "Ventas");

  // Hoja 2: Top Productos
  const productData = [
    ["Producto", "Cantidad Vendida"],
    ...topProducts.map(p => [p.name, p.totalSold]),
  ];
  const productSheet = XLSX.utils.aoa_to_sheet(productData);
  XLSX.utils.book_append_sheet(wb, productSheet, "Top Productos");

  // Hoja 3: Historial Caja
  const cashData = [
    ["Fecha Apertura", "Fecha Cierre", "Total Vendido"],
    ...history.map(r => [
      formatDate(r.openDate),
      r.closeDate ? formatDate(r.closeDate) : "Abierta",
      r.totalSales,
    ]),
  ];
  const cashSheet = XLSX.utils.aoa_to_sheet(cashData);
  XLSX.utils.book_append_sheet(wb, cashSheet, "Historial Caja");

  // Exportar archivo
  const filename = `Estadisticas_Completas_${new Date().toISOString().slice(0, 10)}.xlsx`;
  const buffer = XLSX.write(wb, { bookType: "xlsx", type: "array" });
  const blob = new Blob([buffer], { type: "application/octet-stream" });
  saveAs(blob, filename);
}

function formatDate(dateString) {
  return dateString?.replace("T", " ").replace(/-/g, "/") || "Sin fecha";
}
