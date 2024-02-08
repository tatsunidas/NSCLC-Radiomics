import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.util.DicomTools;


public class PrepareFolders {
	
	public static void main(String[] args) {
		//copyCTImages();
//		sortImages();
		//copyMasks();
		
	}
	
	/*
	 * sort file name by image position and validate dcm file not collapsed.
	 */
	static void sortImages() {
		String imgDir =  "/home/tatsunidas/デスクトップ/NSCLC-DS/IMAGE/";
		String saveTo =  "/home/tatsunidas/デスクトップ/NSCLC-DS/SORTED_IMAGE/";//see, LUNG1-003
		File parent = new File(imgDir);
		File[] subjs = parent.listFiles();
		for(File sub : subjs) {
			
			HashMap<Double, File> map = new HashMap<>();
			File[] images = sub.listFiles();
			
			String name = sub.getName();
			
			new File(saveTo+name).mkdirs();
			
			for(File im : images) {
				ImagePlus imp = new ImagePlus(im.getAbsolutePath());//if dcm file collapsed, IJ cannot read file.
				String iop = DicomTools.getTag(imp, "0020,0032");
				String z_ = iop.trim().split("\\\\")[2];
				map.put(Double.valueOf(z_), im);
			}
			
			Object[] keys = map.keySet().toArray();
//			Arrays.sort(keys);//accending
			Arrays.sort(keys, Collections.reverseOrder());//keys should be Double or Integer. Arrays cannot handle primitive values.
			int itr = 1;
			for(Object k : keys) {
				try {
					Files.copy(map.get(k).toPath(), new File(saveTo+name+File.separator+(itr++)+".dcm").toPath(), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	static void copyCTImages() {
		String parent = "/home/tatsunidas/デスクトップ/manifest-1603198545583/NSCLC-Radiomics/";
		File[] subjs = new File(parent).listFiles();
		
		String saveTo = "/home/tatsunidas/デスクトップ/NSCLC-DS/IMAGE/";
		
		for(File sub:subjs) {
			if(sub.isFile()) {
				continue;
			}
			String name = sub.getName();
			if(!new File(saveTo + name).exists()) {
				new File(saveTo + name).mkdirs();
			}
			
			File study = sub.listFiles()[0];
			File[] series = study.listFiles();
			for(File se:series) {
				if(se.listFiles().length > 3) {
					//CT images
					for(File ct:se.listFiles()) {
						try {
							Files.copy(ct.toPath(), new File(saveTo + name+File.separator+ct.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
	
	static void copyMasks() {
		String parent = "/home/tatsunidas/デスクトップ/NSCLC_SEG_ALL/";
		File[] subjs = new File(parent).listFiles();
		
		String saveTo = "/home/tatsunidas/デスクトップ/NSCLC-DS/MASK/";
		
		for(File sub:subjs) {
			if(sub.isFile()) {
				continue;
			}
			String name = sub.getName();
			if(!new File(saveTo + name).exists()) {
				new File(saveTo + name).mkdirs();
			}
			File[] segDirs = sub.listFiles();
			for(File dir:segDirs) {
				if(dir.getName().contains("Neoplasm")) {
					File[] segs = dir.listFiles();
					for(File s : segs) {
						//SEG image
						try {
							Files.copy(s.toPath(), new File(saveTo + name+File.separator+s.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}

}
