package card.scanner.model;

public interface Filter<T>{
	public boolean accept(T t);
}
