package uk.gov.crowncommercial.dts.scale.cat.csvwriter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.SneakyThrows;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;

public abstract class AbstractCsvGenerator<T>{

    private final Path baseFolder;
    private static final CsvMapper mapper = new CsvMapper();
    private final CsvSchema schema;
    private final Class<T> persistentClass;


    public AbstractCsvGenerator(String baseFolder){
        schema = getSchema();
        mapper.enable(JsonGenerator.Feature.IGNORE_UNKNOWN);
        mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        this.baseFolder = Paths.get(baseFolder);

        this.persistentClass = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
    }

    protected Path getBaseFolder(){
        return this.baseFolder;
    }

    @SneakyThrows
    protected void writeModel(T model, Writer writer) {
        try {
            mapper.writerFor(persistentClass).with(schema).writeValue(writer, model);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    public CsvSchema getSchema(){
        CsvSchema.Builder builder =  CsvSchema.builder();
        for(String column : getColumns()){
            builder.addColumn(column);
        }
        return builder.build();
    }

    public abstract List<String> getColumns();

    public String getHeader() {
        return String.join(",", getColumns());
    }

    protected void writeHeader(BufferedWriter writer) throws IOException {
        writer.write(getHeader());
        writer.newLine();
    }


    protected BufferedWriter getWriter(Path file) throws IOException{
        Path parent = file.getParent();
        parent.toFile().mkdirs();
        File output = file.toFile();
        if(!output.exists()){
            output.createNewFile();
        }
        return new BufferedWriter( new FileWriter(output));
    }
}
