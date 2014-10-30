package card.scanner.model;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import card.scanner.gui.CardProcessorFrame;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import static java.nio.file.StandardWatchEventKinds.*;

public class CardProcessor {
	private static final int NEAR_BLACK = 60;
	private static final String DEFAULT_WORKSPACE = new File("").getAbsolutePath() + "/images";
	
	private CardProcessorFrame frame;
	
	private String workspace = DEFAULT_WORKSPACE;
	private Map<String, Card> cards;
	private Map<String, FileMapping> mappings = new HashMap<String, FileMapping>();
	private Tesseract parser = Tesseract.getInstance();
	private Updator updator = new Updator();
	private Object cardsLock = new Object();
	private Object mappingsLock = new Object();
	
	private Comparator<FileMapping> sorter;
	private Filter<FileMapping> filter;
	
	public CardProcessor(CardProcessorFrame frame){
		this.frame = frame;
		
		cards = PreLoader.preload("init");
		
		EventQueue.invokeLater(new Runnable() {
            public void run() {
                reset(DEFAULT_WORKSPACE);
            }
        });
		
		//initialize updator thread
		Thread thread = new Thread(updator);
		thread.setDaemon(true);
		thread.start();
	}
	
	private FileMapping createMapping(File src){
		try{
			BufferedImage original = loadImage(src);
			BufferedImage png = toPNG(original);
			BufferedImage blackAndWhite = toBlackAndWhite(png);
			if(blackAndWhite == null){
				System.out.println("unrecognized");
				return new FileMapping(src, cards.get("Unrecognized"), original);
			}
			String name = parser.doOCR(blackAndWhite, new Rectangle(130, 135, 890, 90));
			
			name = name.trim();
			
			synchronized(cardsLock){
				if(cards.containsKey(name))return new FileMapping(src, cards.get(name), original);
				return new FileMapping(src, cards.get("Unrecognized"), original);
			}
		}catch(IOException | TesseractException ex){
			ex.printStackTrace();
			return null;
		}
	}
	
	private BufferedImage loadImage(File src){
		try{
			return ImageIO.read(src);
		}catch(IOException ex){
			ex.printStackTrace();
			System.err.println("Failed to read " + src.getAbsolutePath());
			return null;
		}
	}
	
	private BufferedImage toPNG(BufferedImage image)throws IOException{
		if(image == null)return null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image.getSubimage(0, 0, image.getWidth(), 250), "png", out);
		return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
	}
	
	private BufferedImage toBlackAndWhite(BufferedImage png){
		if(png == null)return null;
		return toBlackAndWhite(png, false);
	}
	
	private BufferedImage toBlackAndWhite(BufferedImage png, boolean invert){
		BufferedImage dest = new BufferedImage(png.getWidth(), png.getHeight(), png.getType());
		
		for(int y = 0; y < png.getHeight(); y++){
			for(int x = 0; x < png.getWidth(); x++){
				Color c = new Color(png.getRGB(x, y));
				if(c.getRed() < NEAR_BLACK && c.getGreen() < NEAR_BLACK && c.getBlue() < NEAR_BLACK){
					dest.setRGB(x, y, ((invert) ? Color.WHITE.getRGB() : Color.BLACK.getRGB()));
				}else{
					dest.setRGB(x, y, ((invert) ? Color.BLACK.getRGB() : Color.WHITE.getRGB()));
				}
			}
		}
		
		return dest;
	}
	
	public void reset(){
		String workspace = null;
		workspace = this.workspace;
		
		for(String s : getImageFiles(workspace)){
			File f = new File(s);
			f.delete();
		}
		
		updateTable();
	}
	
	public void reset(String newWorkspace){
		reset();
		changeWorkspace(newWorkspace);
		
		updateTable();
	}
	
	private void updateTable(){
		List<FileMapping> src = new ArrayList<FileMapping>(mappings.values());
		ArrayList<FileMapping> dest = new ArrayList<FileMapping>();
		dest.ensureCapacity(src.size());
		for(FileMapping fm : src){
//			if() TODO finish
		}
		
		frame.updateTable(mappings.values().toArray(new FileMapping[0]));
	}
	
	public void changeWorkspace(String newWorkspace){
		workspace = newWorkspace;
		updator.changeWorkspace(newWorkspace);
	}
	
	private String[] getImageFiles(String workspace){
		File workDir = new File(workspace);
		return workDir.list(new FilenameFilter(){

			@Override
			public boolean accept(File dir, String name) {
				name = name.toLowerCase();
				return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".bmp");
			}
		});
	}
	
	private class Updator implements Runnable{
		WatchService service = null;
		WatchKey key = null;
		
		{
			try {
				service = FileSystems.getDefault().newWatchService();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void run(){
			while(true){
				try {
					WatchKey key = service.take();
					for(WatchEvent<?> e : key.pollEvents()){
						Kind<?> kind = e.kind();
						if(kind == OVERFLOW){
							continue;
						}else{
							Path path = ((Path)e.context());
							File f = path.toFile();
							File actualFile = new File(workspace + "/" + f.getPath());
							path = actualFile.toPath();
							
							String mime = Files.probeContentType(path);
							if(mime.contains("image")){
								synchronized(mappingsLock){
									if(kind == ENTRY_DELETE){
										mappings.remove(path.toFile().getAbsolutePath());
									}else{
										mappings.put(path.toFile().getAbsolutePath(), createMapping(path.toFile()));
									}
								}
							}
						}
					}
					
					key.reset();
					synchronized(mappingsLock){
						updateTable();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		public void changeWorkspace(String newValue){
			try {
				synchronized(mappingsLock){
					mappings = new HashMap<String, FileMapping>();
					updateTable();
				}
				
				//setup callback
				if(key != null)key.cancel();
				File watchDirFile = new File(newValue);
				Path watchDirPath = watchDirFile.toPath();
				key = watchDirPath.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
				
				//initial load
				File f = new File(workspace);
				String[] paths = f.list(new FilenameFilter(){
					
					@Override
					public boolean accept(File parent, String path) {
						File f = new File(parent, path);
						Path p = f.toPath();
						try {
							String contentType = Files.probeContentType(p);
							return (contentType == null) ? false : contentType.contains("image");
						} catch (IOException e) {
							return false;
						}
					}
				});
				
				for(String path : paths){
					synchronized(mappingsLock){
						File file = new File(path);
						file = new File(workspace + "/" + file.getPath());
						mappings.put(file.getPath(), createMapping(file));
					}
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
}
