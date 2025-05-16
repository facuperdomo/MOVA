package com.example.printbridge;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * NanoHTTPD bridge to relay CPCL jobs via Bluetooth SPP.
 * - GET  /        → HTML página de prueba
 * - OPTIONS /     → CORS preflight
 * - POST /print   → raw CPCL → Bluetooth (X-Printer-Mac header obligatorio)
 */
public class PrintBridge extends NanoHTTPD {
    private static final String TAG      = "PrintBridge";
    private static final UUID   SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public PrintBridge(int port) {
        super(port);
    }

    @SuppressLint("MissingPermission")
    @Override
    public Response serve(IHTTPSession session) {
        String uri    = session.getUri();
        Method method = session.getMethod();

        try {
            // CORS preflight
            if (Method.OPTIONS.equals(method)) {
                Response resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
                addCorsHeaders(resp);
                return resp;
            }

            // GET / → página de prueba
            if (Method.GET.equals(method) && "/".equals(uri)) {
                String html =
                        "<html><body>\n" +
                                "  <h1>Print Bridge activo</h1>\n" +
                                "  <button onclick=\"printTest()\">Imprimir Test</button>\n" +
                                "  <script>\n" +
                                "  async function printTest(){\n" +
                                "    const cmd = `! 0 200 200 210 1\\r\\nTEXT 4 0 30 50 Test dinámico\\r\\nFORM\\r\\nPRINT\\r\\n`;\n" +
                                "    await fetch('/print', {\n" +
                                "      method:'POST',\n" +
                                "      headers:{\n" +
                                "        'Content-Type':'application/octet-stream',\n" +
                                "        'X-Printer-Mac':'DC:0D:30:1F:B7:0E'\n" +
                                "      },\n" +
                                "      body: new TextEncoder().encode(cmd)\n" +
                                "    });\n" +
                                "    alert('Enviado')\n" +
                                "  }\n" +
                                "  </script>\n" +
                                "</body></html>";
                Response resp = newFixedLengthResponse(Response.Status.OK, "text/html", html);
                addCorsHeaders(resp);
                return resp;
            }

            // POST /print → relay via Bluetooth
            if (Method.POST.equals(method) && "/print".equals(uri)) {
                // leer Content-Length
                String lenHeader = session.getHeaders().get("content-length");
                int length = lenHeader != null ? Integer.parseInt(lenHeader) : -1;
                if (length <= 0) {
                    return newFixedLengthResponse(
                            Response.Status.LENGTH_REQUIRED,
                            "text/plain",
                            "Missing or invalid Content-Length"
                    );
                }

                // leer payload
                byte[] data = new byte[length];
                InputStream is = session.getInputStream();
                int read = 0, chunk;
                while (read < length && (chunk = is.read(data, read, length - read)) > 0) {
                    read += chunk;
                }
                Log.d(TAG, "Bytes recibidos=" + read);

                // obtener MAC desde header
                String mac = session.getHeaders().get("x-printer-mac");
                if (mac == null || mac.isEmpty()) {
                    return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "text/plain",
                            "Header X-Printer-Mac is required"
                    );
                }

                // enviar por Bluetooth SPP
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) {
                    return newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            "text/plain",
                            "Bluetooth no soportado"
                    );
                }
                BluetoothDevice device = adapter.getRemoteDevice(mac);
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                adapter.cancelDiscovery();
                socket.connect();
                Log.d(TAG, "Conectado a " + mac);

                socket.getOutputStream().write(data);
                socket.getOutputStream().flush();
                socket.close();
                Log.d(TAG, "Impresión OK");

                Response resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
                addCorsHeaders(resp);
                return resp;
            }

            // Not found
            Response resp = newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain",
                    "No encontrado (" + uri + ")"
            );
            addCorsHeaders(resp);
            return resp;

        } catch (IOException e) {
            Log.e(TAG, "Serve IOException:", e);
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "IO Error: " + e.getMessage()
            );
        } catch (Exception e) {
            Log.e(TAG, "Serve Error:", e);
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage()
            );
        }
    }

    /** Agrega cabeceras CORS */
    private void addCorsHeaders(Response resp) {
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, X-Printer-Mac");
    }
}
