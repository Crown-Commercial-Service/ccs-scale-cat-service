package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public abstract class AbstractCSVReader<T> {

    private static final CsvMapper mapper = new CsvMapper();
    private final Class<T> persistentClass;

    static {
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.enable(CsvParser.Feature.IGNORE_TRAILING_UNMAPPABLE);
        mapper.enable(CsvParser.Feature.EMPTY_STRING_AS_NULL);
        mapper.enable(CsvParser.Feature.TRIM_SPACES);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(CsvParser.Feature.SKIP_EMPTY_LINES);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
    }

    public AbstractCSVReader() {
        this.persistentClass = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
    }

    @SneakyThrows
    public void process(File rootDir, Consumer<T> consumer) {

        DateFormat format;

        Collection<File> files = FileUtils.listFiles(
                rootDir,
                new RegexFileFilter("^(.*?)"),
                DirectoryFileFilter.DIRECTORY
        );

        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "6");

        files.parallelStream().forEach(file -> {
//        files.stream().forEach(file -> {
            boolean success = true;
            try{
                MappingIterator<T> it = readdata(file, getDeclaredClass());
                while (it.hasNext()) {
                    try {
                        consumer.accept(it.next());
                    }catch(Throwable t){
                        success = false;
                        t.printStackTrace();
                    }
                }
                if(success)
                    onCompletion(file);
            }
          //  catch(InterruptedException ie){           }
            catch(Throwable t){
                t.printStackTrace();
                System.out.println(file.toPath());
                success = false;
                //throw t;
            }
        });
    }

    @SneakyThrows
    private MappingIterator<T> readdata(File is, Class<T> type) {
        CsvSchema schema = getSchema();
        return mapper.readerFor(type).with(schema).readValues(is);
    }

    protected  CsvSchema getSchema(){
        return CsvSchema.emptySchema().withHeader();
    }

    private Class<T> getDeclaredClass() {
        return persistentClass;
    }

    public void processFile(File file, Consumer<T> consumer){
        try{
            MappingIterator<T> it = readdata(file, getDeclaredClass());
            while (it.hasNext()) {
                consumer.accept(it.next());
            }

            onCompletion(file);
        }catch(Throwable t){
            System.out.println(file.toPath());
            throw t;
        }
    }

    private boolean isRunning= false;

    public void parallelRecordProcess(File file, Consumer<T> consumer) {
        isRunning = true;
        int noThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(noThreads);
        ArrayBlockingQueue<T> blockQueue = new ArrayBlockingQueue<>(noThreads * 8);

        for(int i=0; i < noThreads; i++){
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    while(isRunning) {
                        try {
                            T data = blockQueue.poll(500, TimeUnit.MILLISECONDS);
                            if(null != data)
                                consumer.accept(data);
                        } catch (InterruptedException e) {

                        }catch(Throwable t){
                            t.printStackTrace();
                        }
                    }
                }
            });
        }


        try{
            MappingIterator<T> it = readdata(file, getDeclaredClass());
            while (it.hasNext()) {
                blockQueue.put(it.next());
            }
        }
        catch(InterruptedException ie){
            ie.printStackTrace();
        }
        catch(Throwable t){
            System.out.println(file.toPath());
            throw t;
        }

        while(true){
            if(0 == blockQueue.size()){
                isRunning = false;
                executorService.shutdown();
                break;
            }else{
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    protected void onCompletion(File file) {
    }
}
