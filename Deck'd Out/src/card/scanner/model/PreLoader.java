package card.scanner.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class PreLoader {
	private static final int NEAR_BLACK = 60;
	
	public static Map<String, Card> preload(String path){
		File f = new File(path);
		Map<String, String> sets = new HashMap<String, String>();
		Map<String, Integer> prices = new HashMap<String, Integer>();
		Map<String, Card> cards = new HashMap<String, Card>();
		
		File props = new File(path + "/info.txt");
		
		try(Scanner scan = new Scanner(props)){
			while(scan.hasNext()){
				String[] tokens = scan.nextLine().split("\t");
				sets.put(tokens[0], tokens[1]);
				prices.put(tokens[0], Integer.parseInt(tokens[2]));
				
				cards.put(tokens[0], new Card(tokens[0], tokens[1], Integer.parseInt(tokens[2])));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		
		return cards;
	}
}
