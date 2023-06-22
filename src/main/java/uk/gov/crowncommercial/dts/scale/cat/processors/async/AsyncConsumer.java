package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public interface AsyncConsumer<T> extends TaskConsumer<T>{

    String getIdentifier(T data);

}
