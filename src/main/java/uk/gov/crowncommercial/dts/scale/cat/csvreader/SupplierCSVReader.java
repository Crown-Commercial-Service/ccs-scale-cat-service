package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.util.Arrays;
import java.util.List;

public class SupplierCSVReader extends AbstractCSVReader<SupplierModel>{

    private static final List<String> columns = Arrays.asList("supplierName", "entityId");
//
//    protected CsvSchema getSchema(){
//        CsvSchema.Builder builder =  CsvSchema.builder();
//        for(String column : columns){
//            builder.addColumn(column);
//        }
//        return builder.build().withHeader();
//    }
}
