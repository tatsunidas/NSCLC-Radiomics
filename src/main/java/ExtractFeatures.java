import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.poi.util.SystemOutLogger;

import com.vis.radiomics.main.RadiomicsJ;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.plugin.FolderOpener;
import ij.util.DicomTools;

public class ExtractFeatures {

	public static void main(String[] args) throws Exception {
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
		for(int i=0;i<imagesList.size();i++) {
			if(!imagesList.get(i).getName().equals(masksList.get(i).getName())) {
				System.out.println(imagesList.get(i).getName());
				System.out.println(masksList.get(i).getName());
				throw new Exception("image and mask are not paired.");
			}
			String id_ = imagesList.get(i).getName();
			Date start = new Date();
			FolderOpener opener = new FolderOpener();
			opener.sortByMetaData(false);//set false sorting by image number, to activate z axis sorted images by file names.
			ImagePlus im = opener.openFolder(images[i].getAbsolutePath());
			ImagePlus msk = loadMaskByUID(masks[i], im);
			System.out.println("==================================");
			System.out.println("Now processing : "+images[i].getName());
			try {
				ResultsTable res_ = radiomics.execute(im, msk, RadiomicsJ.targetLabel);
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
					continue;
				}
				String colsString = res_.getColumnHeadings();
				String[] cols = colsString.replace(" ","").split("\t");
				res.addRow();
				for(String col : cols) {
					if(col.equals("ID")) {
						res.addValue(col, id_);
						continue;
					}
					res.addValue(col, res_.getStringValue(col, 0));
				}
			} catch (Exception e) {
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
