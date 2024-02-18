
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

public class SegmentationObject {
	
	/*
	 * LUNG1-128 Segmentation File not included.
	 * only one : 050,067,115,149,210
	 * only 2 : 301
	 */
	
	public static void main(String[] args) throws IOException {
		SegmentationObject so = new SegmentationObject();
		so.extractAll();
//		so.copyLesionSeg();
	}
	
	public void copyLesionSeg() throws IOException {
		String from = "/home/tatsunidas/デスクトップ/NSCLC_SEG_ALL/";
		String copyTo = "/home/tatsunidas/デスクトップ/NSCLC_SEG_Lesion/";
		if(!new File(copyTo).exists()) {
			new File(copyTo).mkdirs();
		}
		File[] subj = new File(from).listFiles();
		for(File sub:subj) {
			File[] segs = sub.listFiles();
			for(File seg:segs) {
				if(seg.getName().contains("Neoplasm")) {
					Files.copy(seg.toPath(), new File(copyTo+seg.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
					break;
				}
			}
		}
	}

	public void extractAll() throws IOException {
		String dsPath = "/home/tatsunidas/デスクトップ/manifest-1603198545583/NSCLC-Radiomics/";
		File parent = new File(dsPath);
		File[] subjects = parent.listFiles();
		String saveTo = "/home/tatsunidas/デスクトップ/NSCLC_SEG_ALL/";
		for(File sub:subjects) {
			if(sub.isDirectory()) {
				String sub_name = sub.getName();
				String saveLoc = saveTo+sub_name+File.separator;
				if(!new File(saveLoc).exists()) {
					new File(saveLoc).mkdirs();
				}
				File[] study = sub.listFiles();
				for(File s:study) {
					if(s.isDirectory()) {
						File[] dataset = s.listFiles();
						for(File ds: dataset) {
							File[] dcms = ds.listFiles();
							if(dcms.length == 1) {
								try {
									convertToImagePlus(dcms[0].getAbsolutePath(), saveLoc);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
			}
		}
	}
	
//	public void saveAsTif(String segDcmPath, String saveTo, String id) {
//		ImagePlus[] stacks=null;
//		try {
//			stacks = convertToImagePlus(segDcmPath, saveTo, id);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		if(stacks != null && stacks.length>0) {
//			for(ImagePlus imp : stacks) {
//				String segnum_labelname = imp.getTitle();
//				IJ.save(imp, saveTo+id+"_"+segnum_labelname+".tif");
//			}
//		}
//	}
	
	public void convertToImagePlus(String segDcmPath, String saveTo) throws Exception {
		DicomInputStream dis = null;
		Attributes dcmObj;
		try {
			dis = new DicomInputStream(new File(segDcmPath));
			dcmObj = dis.readDataset();
			Attributes fmi = dis.readFileMetaInformation();
			if(!fmi.getString(Tag.MediaStorageSOPClassUID).equals("1.2.840.10008.5.1.4.1.1.66.4")) {
				System.out.println("This is not Segmentation Object.");
				return;
			}
			String compress = dcmObj.getString(Tag.LossyImageCompression);
			String seg_type = dcmObj.getString(Tag.SegmentationType);
			if(!compress.equals("00")) {
				System.out.println("This segmetation was compressed, you need decompression.");
				return;
			}
			
			if(!seg_type.equals("BINARY")) {
				System.out.println("This segmentation type is not BINARY, cannot read in this code.");
				return;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}finally {
			if(dis !=null) {
				try {
					dis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		Object bulk = dcmObj.getValue(Tag.PixelData);
		byte[] binary = null;
		if(bulk instanceof byte[]) {
			binary = extractBinary((byte[])bulk);
		}else {
			System.out.println("Can not read pixel array...");
			return;
		}
		
		int row = dcmObj.getInt(Tag.Rows, -1);
		int col = dcmObj.getInt(Tag.Columns, -1);
		int matrix = row*col;
		int total = dcmObj.getInt(Tag.NumberOfFrames, -1);
		String[] refUIDs = getReferencedUID(dcmObj);
		int num_seg = getNumberOfSegmentation(dcmObj);
		HashMap<Integer,String> labels = getSegmentLabels(dcmObj);
		
		if(num_seg != labels.size()) {
			throw new Exception("Labels size does not match with SegmentNumber!");
		}
		
		int slice = total/num_seg;
		int stack_len = matrix*slice;
		
		int sample = dcmObj.getInt(Tag.SamplesPerPixel, -1);
		int bit = dcmObj.getInt(Tag.BitsAllocated, -1);
		int bitStore = dcmObj.getInt(Tag.BitsStored, -1);
		//int highBit = dcmObj.getInt(Tag.HighBit, -1);//0
		if(sample != 1) {
			System.out.println("This segmentation is not binary...");
			return;
		}
		if(bit !=1 || bitStore != 1) {
			System.out.println("This segmentation is not binary...");
			return;
		}
		
		for(Integer segnum : labels.keySet()) {
			String label = labels.get(segnum);
			new File(saveTo+segnum+"_"+label).mkdirs();
		}
		
		int itr = 0;
		int f = 0;
		for(Integer segnum : labels.keySet()) {
			String label = labels.get(segnum);
			for(int z=0;z<slice;z++) {
				byte[] pixels = new byte[matrix];
				int pos_ = 0;
				for(int y=0;y<row;y++) {
					for(int x=0;x<col;x++) {
						byte v = binary[x+y*row+z*matrix+stack_len*f];
						pixels[pos_++]=v;
					}
				}
				ImageProcessor ip = new ByteProcessor(col, row, pixels);
				ImagePlus imp = new ImagePlus(refUIDs[itr], ip);
				/*
				 * total - itr means reverse image position to adjust endianness order big to little.
				 */
				IJ.save(imp, saveTo+segnum+"_"+label+File.separator+(total-itr)+"_"+refUIDs[itr++]+".tif");
			}
			f++;
		}
		
		/*
		 * get as stacks.
		 */
//		ImageStack[] stacks = new ImageStack[num_seg];
//		for(int f=0;f<num_seg;f++) {
//			ImageStack stack = new ImageStack();
//			for(int z=0;z<slice;z++) {
//				byte[] pixels = new byte[matrix];
//				int pos_ = 0;
//				for(int y=0;y<row;y++) {
//					for(int x=0;x<col;x++) {
//						byte v = binary[x+y*row+z*matrix+stack_len*f];
//						pixels[pos_++]=v;
//					}
//				}
//				ImageProcessor ip = new ByteProcessor(col, row, pixels);
//				stack.addSlice(""+z,ip);
//				
//			}
//			/*
//			 * to adjust endianness order big to little
//			 */
//			ImageStack reverse = new ImageStack();
//			for(int r=0;r<slice;r++) {
//				reverse.addSlice(""+r, stack.getProcessor(slice-r));
//			}
//			stacks[f] = reverse;
//		}
//		ImagePlus[] set = new ImagePlus[num_seg];
//		int n=0;
//		for(Integer segnum : labels.keySet()) {
//			ImagePlus imp = new ImagePlus(labels.get(segnum),stacks[n]);
//			imp.setTitle(segnum+"_"+labels.get(segnum));
//			set[n] = imp;
//			n++;
//		}
	}
	
	String[] getReferencedUID(Attributes seg) {
		Sequence seq = seg.getSequence(Tag.PerFrameFunctionalGroupsSequence);
		int frames = seq.size();//same as the number of frames
		String[] uids = new String[frames];
		int itr = 0;
		for(Attributes atr: seq) {
			Sequence dis = atr.getSequence(Tag.DerivationImageSequence);
			for(Attributes chi: dis) {
				Sequence sis = chi.getSequence(Tag.SourceImageSequence);
				Iterator<Attributes> con = sis.iterator();
				boolean brk = false;
				while(con.hasNext()) {
					Attributes in = con.next();
					int[] tags = in.tags();
					for(int t:tags) {
						if(t == Tag.ReferencedSOPInstanceUID) {
							uids[itr++] = in.getString(Tag.ReferencedSOPInstanceUID);
							brk = true;
							break;
						}
					}
					if(brk) {
						break;//while break
					}
				}
			}
		}
		return uids;
	}
	
	int getNumberOfSegmentation(Attributes seg) {
		Sequence seq = seg.getSequence(Tag.PerFrameFunctionalGroupsSequence);
		//System.out.println(seq.size());//same as the number of frames
		HashSet<String> segNumber = new HashSet<String>();
		for(Attributes atr: seq) {
			//SegmentIdentificationSequence
			Sequence segcon = atr.getSequence(Tag.SegmentIdentificationSequence);
			Iterator<Attributes> con = segcon.iterator();
			while(con.hasNext()) {
				Attributes in = con.next();
				int[] tags = in.tags();
				for(int t:tags) {
					if(t == Tag.ReferencedSegmentNumber) {
						segNumber.add(in.getString(Tag.ReferencedSegmentNumber));
						break;
					}
				}
			}
		}
		return segNumber.size();
	}
	
	HashMap<Integer, String> getSegmentLabels(Attributes seg) throws Exception{
		HashMap<Integer, String> labels = new HashMap<Integer, String>();
		Sequence seg_seq = seg.getSequence(Tag.SegmentSequence);
		Iterator<Attributes> con = seg_seq.iterator();
		while(con.hasNext()) {
			Attributes in = con.next();
			int[] tags = in.tags();
			Integer num = null;
			String label = null;
			for(int t:tags) {
				if(num != null && label != null) {
					labels.put(num, label);
					num = null;
					label = null;
				}
				if(t == Tag.SegmentNumber) {
					num = in.getInt(t, -1);
					if(num == -1) {
						throw new Exception();
					}
				}
				if(t == Tag.SegmentLabel) {
					label = in.getString(t);
				}
			}
		}
		return labels;
	}
	
	byte[] extractBinary(byte[] bulk) {
		int len = bulk.length;
		int pos = 0;
		int byte_len =8;
		byte[] binary = new byte[len*byte_len];
		for(int i=0;i<len;i++) {
			String byte8 = String.format("%8s", Integer.toBinaryString(bulk[i] & 0xFF)).replace(' ', '0');
			//big-endian to little
			StringBuilder reversedString = new StringBuilder(byte8);
	        reversedString.reverse();
	        byte8 = reversedString.toString();
//			if (byte8.contains("1")) System.out.println(byte8); // e.g., 10000001
			byte binary0 = Byte.parseByte(byte8.substring(0, 1));
			byte binary1 = Byte.parseByte(byte8.substring(1, 2));
			byte binary2 = Byte.parseByte(byte8.substring(2, 3));
			byte binary3 = Byte.parseByte(byte8.substring(3, 4));
			byte binary4 = Byte.parseByte(byte8.substring(4, 5));
			byte binary5 = Byte.parseByte(byte8.substring(5, 6));
			byte binary6 = Byte.parseByte(byte8.substring(6, 7));
			byte binary7 = Byte.parseByte(byte8.substring(7));
			
			binary[pos++] = (byte) (binary0);
			binary[pos++] = (byte) (binary1);
			binary[pos++] = (byte) (binary2);
			binary[pos++] = (byte) (binary3);
			binary[pos++] = (byte) (binary4);
			binary[pos++] = (byte) (binary5);
			binary[pos++] = (byte) (binary6);
			binary[pos++] = (byte) (binary7);
		}
		return binary;
	}

}
