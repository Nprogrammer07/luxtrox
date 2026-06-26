package com.luxtrox.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.luxtrox.backend.entity.Invoice;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.integration.storage.SupabaseStorageClient;
import com.luxtrox.backend.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Genera el PDF de factura de una compra confirmada y lo sube a
 * Supabase Storage (S3-compatible -- ver docs/domain-model.md adenda
 * de Fase 7). El envio por correo (con este PDF adjunto) lo hace
 * NotificationEmailService, no esta clase.
 */
@Service
public class InvoiceService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InvoiceRepository invoiceRepository;
    private final SupabaseStorageClient storageClient;

    public InvoiceService(InvoiceRepository invoiceRepository, SupabaseStorageClient storageClient) {
        this.invoiceRepository = invoiceRepository;
        this.storageClient = storageClient;
    }

    /**
     * A PROPOSITO sin @Transactional: esto se llama siempre desde
     * dentro de PurchaseService.confirmPurchase() (que SI es
     * transaccional) envuelto en su propio try/catch -- si este
     * metodo FUERA @Transactional y storageClient.uploadFile()
     * fallara (ej. Storage caido), Spring marcaria la transaccion
     * COMPARTIDA con el llamador como rollback-only ANTES de que el
     * try/catch del llamador llegue a atraparla. El resultado: la
     * excepcion queda "atrapada" pero la transaccion ya esta
     * envenenada, y el commit final de confirmPurchase() explota con
     * UnexpectedRollbackException (un 500 que no deberia pasar, ya
     * que la compra en si se confirmo bien). Sin @Transactional aqui,
     * un fallo de storage es un RuntimeException comun que el
     * try/catch del llamador atrapa limpio, sin tocar ninguna
     * transaccion.
     */
    public Invoice generateAndStore(Purchase purchase) {
        return generateStoreAndReturnBytes(purchase).invoice();
    }

    /**
     * Devuelve tambien los bytes del PDF recien generado -- evita que
     * quien llama tenga que volver a descargarlo de storage solo para
     * adjuntarlo en el correo de confirmacion.
     */
    public record InvoiceWithBytes(Invoice invoice, byte[] pdfBytes) {
    }

    public InvoiceWithBytes generateStoreAndReturnBytes(Purchase purchase) {
        String invoiceNumber = generateInvoiceNumber(purchase);
        byte[] pdfBytes = renderPdf(purchase, invoiceNumber);

        String storageKey = "invoices/" + invoiceNumber + ".pdf";
        storageClient.uploadFile(storageKey, pdfBytes, "application/pdf");

        Invoice invoice = invoiceRepository.save(new Invoice(purchase, invoiceNumber, storageKey));
        return new InvoiceWithBytes(invoice, pdfBytes);
    }

    public byte[] downloadPdf(Invoice invoice) {
        return storageClient.downloadFile(invoice.getPdfStorageKey());
    }

    /**
     * Secuencial dentro de este proceso + timestamp -- suficiente para
     * que no colisione. No pretende ser un numero de factura legal
     * correlativo estricto (eso requeriria un contador centralizado en
     * la base de datos, fuera del alcance de esta fase).
     */
    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis() % 100000);

    private String generateInvoiceNumber(Purchase purchase) {
        return "LUX-" + purchase.getCreatedAt().getYear() + "-" + COUNTER.incrementAndGet();
    }

    private byte[] renderPdf(Purchase purchase, String invoiceNumber) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph("LUXTROX ALGORITMO", titleFont));
            document.add(new Paragraph("Factura " + invoiceNumber, labelFont));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);

            addRow(table, "Cliente", purchase.getUser().getFullName(), labelFont, valueFont);
            addRow(table, "Email", purchase.getUser().getEmail(), labelFont, valueFont);
            addRow(table, "Plan", purchase.getPlanType().name(), labelFont, valueFont);
            addRow(table, "Cantidad", String.valueOf(purchase.getPackageQuantity()), labelFont, valueFont);
            addRow(table, "Metodo de pago", purchase.getPaymentMethod().name(), labelFont, valueFont);
            addRow(table, "Fecha de confirmacion",
                    purchase.getConfirmedAt() != null ? purchase.getConfirmedAt().format(DATE_FORMAT) : "-",
                    labelFont, valueFont);
            addRow(table, "Total", "$" + purchase.getTotalAmount() + " USD", labelFont, valueFont);

            document.add(table);
            document.close();

            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la factura", e);
        }
    }

    private void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}