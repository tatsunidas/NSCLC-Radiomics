import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vis.radiomics.main.RadiomicsJ;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.plugin.FolderOpener;
import ij.util.DicomTools;

public class ExtractFeatures {

	/*
	 * 画像の順序がSEGとは異なるのでUIDで紐付け
	 * 003
	 * 095
	 * 
	 * 画像枚数とSEG枚数とが異なる
	 * 095
	 * 
	 * 
	 * 246 termination//copy error
	 */
	public static void main(String[] args) throws Exception {
		/*
		 * see also Run Configuration (VM arguments) up to heap size.
		 * -Xms512M -Xmx8048M
		 */
		extract("LUNG1-245","LUNG1-245");
	}
	
	static void check(String name) {
		File ds_i = new File("/home/tatsunidas/デスクトップ/NSCLC-DS/SORTED_IMAGE/"+name+"/");
		File ds_m = new File("/home/tatsunidas/デスクトップ/NSCLC-DS/MASK/"+name+"/");
		ImagePlus imgAndMsk[] = loadImageSetByReferenceUIDs(ds_i, ds_m);
		imgAndMsk[0].show();
		imgAndMsk[1].show();
	}
	
	/**
	 * See also Run Configuration (VM arguments) to increase heap size(-Xms512M -Xmx8048M).
	 * @param startFrom e.g., LUNG1-246
	 */
	static void extract(String startFrom/*extract all if set null*/, String endWith) {
		/*
		 * see also Run Configuration (VM arguments) up to heap size.
		 * -Xms512M -Xmx8048M
		 */
		File ds_i = new File("/home/tatsunidas/デスクトップ/NSCLC-DS/SORTED_IMAGE/");
		File ds_m = new File("/home/tatsunidas/デスクトップ/NSCLC-DS/MASK/");
		String prop = "nsclc_prop.properties";
		
		RadiomicsJ radiomics = new RadiomicsJ();
		radiomics.setDebug(true);
		radiomics.loadSettings(prop);
		
		File[] images = ds_i.listFiles();
		List<File> imagesList = Arrays.asList(images);
		File[] masks = ds_m.listFiles();
		List<File> masksList = Arrays.asList(masks);
		Collections.sort(imagesList);
		Collections.sort(masksList);
		ResultsTable res = null;
		boolean go = false;
		for(int i=0;i<imagesList.size();i++) {
			
			String id_ = imagesList.get(i).getName();
			
			if(startFrom == null) {
				go = true;
			} else {
				if (id_.equals(startFrom)) {
					go = true;
				}
			}
			
			if(!go) {
				continue;
			}
			
			if(id_.equals("LUNG1-128")) {
				//segmentation file is not included, I checked 2 times downloads.
				continue;
			}
			
			System.out.println("==================================");
			System.out.println("Now processing : "+id_);
			Date start = new Date();
			
			try {
				if(!imagesList.get(i).getName().equals(masksList.get(i).getName())) {
					System.out.println(imagesList.get(i).getName());
					System.out.println(masksList.get(i).getName());
					throw new Exception("image and mask are not paired.");
				}
				
				ImagePlus imgAndMsk[] = loadImageSetByReferenceUIDs(imagesList.get(i), masksList.get(i));
				
				ResultsTable res_ = radiomics.execute(imgAndMsk[0], imgAndMsk[1], RadiomicsJ.targetLabel);
				if(res == null) {
					res = res_;
					res.addValue("ID", id_);
					Date end = new Date();
					long diff = end.getTime() - start.getTime();
					String TimeTaken = String.format("[%s] hours : [%s] mins : [%s] secs",
		                    Long.toString(TimeUnit.MILLISECONDS.toHours(diff)),
		                    TimeUnit.MILLISECONDS.toMinutes(diff),
		                    TimeUnit.MILLISECONDS.toSeconds(diff));
					System.out.println(String.format("Time taken %s", TimeTaken));
					if(endWith != null) {
						if(id_.equals(endWith)) {
							break;
						}
					}
					continue;
				}
				String colsString = res_.getColumnHeadings();
				String[] cols = colsString.replace(" ","").split("\t");
				res.addRow();
				res.addValue("ID", id_);
				for(String col : cols) {
					res.addValue(col, res_.getStringValue(col, 0));
				}
			} catch (Exception e) {
				e.printStackTrace();
				IJ.log(e.getMessage());
				if(res != null) {
					res.save("results"+start.getTime()+".csv");//escape
				}
				System.out.println("Termination at :"+images[i].getName());
				return;
			}
			Date end = new Date();
			long diff = end.getTime() - start.getTime();
			String TimeTaken = String.format("[%s] hours : [%s] mins : [%s] secs",
                    Long.toString(TimeUnit.MILLISECONDS.toHours(diff)),
                    TimeUnit.MILLISECONDS.toMinutes(diff),
                    TimeUnit.MILLISECONDS.toSeconds(diff));
			System.out.println(String.format("Time taken %s", TimeTaken));
			if(endWith != null) {
				if(id_.equals(endWith)) {
					break;
				}
			}
		}
		if (res != null) {
			Date t = new Date();
			res.show(RadiomicsJ.resultWindowTitle);
			res.save("results_"+t.getTime()+".csv");
		}else {
			IJ.log("RadiomicsJ can not perform feature extraction. Please check image, mask and settings.");
		}
	}
	
	static void testLoadMaskByUID() {
		File ds_i = new File("/home/tatsunidas/デスクトップ/NSCLC-DS/TEST/IMAGE/LUNG1-003/");
		File ds_m = new File("/home/tatsunidas/デスクトップ/NSCLC-DS/TEST/MASK/LUNG1-003/");
		
		FolderOpener opener = new FolderOpener();
		opener.sortByMetaData(false);//set false sorting by image number, to activate z axis sorted images by file names.
		ImagePlus im = opener.openFolder(ds_i.getAbsolutePath());
		im.show();
		ImagePlus msk = loadMaskByUID(ds_m, im);
		IJ.saveAs(msk, "tif", "maskTest");
	}
	
	/**
	 * 
	 * @param imageDir
	 * @param maskDir
	 * @return imageStack and maskStack (are sorted)
	 */
	static ImagePlus[] loadImageSetByReferenceUIDs(File imageDir, File maskDir) {
		File masks[] = maskDir.listFiles();
		File images[] = imageDir.listFiles();
		
		if(images.length <= masks.length) {
			FolderOpener opener = new FolderOpener();
			opener.sortByMetaData(false);//set false sorting by image number, to activate z axis sorted images by file names.
			ImagePlus img = opener.openFolder(imageDir.getAbsolutePath());
			ImagePlus msk = loadMaskByUID(maskDir, img);
			if(img.getNSlices() != msk.getNSlices()) {
				System.out.println("Number of slices is not matched. Please check this dataset:"+imageDir.getName());
				return null;
			}
			return new ImagePlus[] {img, msk};
		}
		
		if(masks.length < images.length) {
			FolderOpener opener = new FolderOpener();
			opener.sortByMetaData(false);//set false sorting by image number, to activate z axis sorted images by file names.
			ImagePlus img = opener.openFolder(imageDir.getAbsolutePath());
			ImagePlus msk = loadMaskByUID(maskDir, img);
			ArrayList<Integer> drop = new ArrayList<>();
			for(int i =1;i<=img.getNSlices();i++) {
				img.setPosition(i);
				String uid = DicomTools.getTag(img, "0008,0018").trim();
				boolean found = false;
				for(File m:maskDir.listFiles()) {
					if (m.getName().contains(uid)) {
						found = true;
						break;
					}
				}
				if(!found) {
					drop.add(i);
				}
			}
			for(int pos : drop) {
				img.getStack().deleteSlice(pos);
			}
			return new ImagePlus[]{img, msk};
		}
		return null;
	}
	
	
	static ImagePlus loadMaskByUID(File maskDir, ImagePlus img) {
		File[] masks = maskDir.listFiles();//tif images
		int z = img.getNSlices(); 
		ImageStack stack = new ImageStack(img.getWidth(), img.getHeight());
		for(int i=1;i<=z;i++) {
			img.setPosition(i);
			String uid = DicomTools.getTag(img, "0008,0018").trim();
			for(File m : masks) {
				if(m.getName().contains(uid)) {
					ImagePlus slice = new ImagePlus(m.getAbsolutePath());
					stack.addSlice(i+"",slice.getProcessor());
					break;
				}
			}
		}
		return new ImagePlus("mask", stack);
	}
}
