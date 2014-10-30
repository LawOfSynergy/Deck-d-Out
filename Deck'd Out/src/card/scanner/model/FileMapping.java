package card.scanner.model;

import java.awt.image.BufferedImage;
import java.io.File;

public class FileMapping {
	private File file;
	private Card card;
	private BufferedImage img;
	
	/**
	 * Do not increase the card's count, as this will do it on construction.
	 * @param file
	 * @param card
	 */
	public FileMapping(File file, Card card, BufferedImage img){
		if(card == null)throw new RuntimeException("null card");
		this.file = file;
		this.card = card;
		this.img = img;
	}
	
	public File getFile(){
		return file;
	}
	
	public Card getCard(){
		return card;
	}
	
	public BufferedImage getImage(){
		return img;
	}
}
