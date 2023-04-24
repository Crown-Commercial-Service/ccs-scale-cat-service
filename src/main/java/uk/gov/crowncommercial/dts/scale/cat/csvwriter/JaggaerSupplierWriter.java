package uk.gov.crowncommercial.dts.scale.cat.csvwriter;

import lombok.SneakyThrows;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.JaggaerSupplierModel;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.SupplierModel;

import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class JaggaerSupplierWriter extends AbstractCsvGenerator<JaggaerSupplierModel> implements Consumer<JaggaerSupplierModel> {

    private static final List<String> columns = Arrays.asList("bravoId","extCode","extUniqueCode", "fiscalCode","supplierName", "email", "postalAddress");
    private final String filename;

    private BufferedWriter writer;

    public JaggaerSupplierWriter(String baseFolder, String filename) {
        super(baseFolder);
        this.filename = filename;
        initHeader();
    }

    @Override
    public List<String> getColumns() {
        return columns;
    }

    @SneakyThrows
    public void initHeader() {
        Path file = getBaseFolder().resolve(filename);
        writer = getWriter(file);
        writer.write(getHeader());
        writer.newLine();
    }

    @Override
    @SneakyThrows
    public void accept(JaggaerSupplierModel model) {
        writeModel(model, writer);
        writer.flush();
    }

    @SneakyThrows
    public void complete() {
        writer.flush();
        writer.close();
    }
}
