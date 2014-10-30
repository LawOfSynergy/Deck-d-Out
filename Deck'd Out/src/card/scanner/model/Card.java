package card.scanner.model;


public class Card {
	private String name;
	private String set;
	private int price;
	
	public Card(String name, String set, int price){
		this.name = name;
		this.set = set;
		this.price = price;
	}
	
	public String getName(){
		return name;
	}
	
	public String getSet(){
		return set;
	}
	
	public int getPrice(){
		return price;
	}
}
