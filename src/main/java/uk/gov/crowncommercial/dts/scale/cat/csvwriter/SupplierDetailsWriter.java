package uk.gov.crowncommercial.dts.scale.cat.csvwriter;

import lombok.SneakyThrows;
import uk.gov.crowncommercial.dts.scale.agreement.model.dto.SupplierDetails;

import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class SupplierDetailsWriter extends AbstractCsvGenerator<SupplierDetails> implements Consumer<SupplierDetails> {
    private static final List<String> columns = Arrays.asList("entityId","legalName", "tradingName",
            "emailAddress", "address");
    private final String filename;

    private BufferedWriter writer;

    public SupplierDetailsWriter(String baseFolder, String filename) {
        super(baseFolder);
        this.filename = filename;
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
    public void accept(SupplierDetails model) {
        writeModel(model, writer);
        writer.flush();
    }

    @SneakyThrows
    public void complete() {
        writer.flush();
        writer.close();
    }
}
