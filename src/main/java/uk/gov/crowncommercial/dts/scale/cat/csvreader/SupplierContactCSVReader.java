package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import java.io.File;

public class SupplierContactCSVReader extends AbstractCSVReader<SupplierContactModel>{
    @Override
    protected void onCompletion(File file) {
        file.delete();
    }
}
