package fr.needeka.sbr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import fr.ni240sx.ucgt.shared.Utils;
import fr.ni240sx.ucgt.shared.Hash;

//based on https://github.com/ToadKing/sbs_extractor
public class SBR {
	public int bankKey;
	public List<DataDefinition> defs;
	public List<Entry> entries;
	
	public SBR(int bankKey) { //default constructor
		this.bankKey = bankKey;
		defs = new ArrayList<>(2);
		entries = new ArrayList<>();
		defs.add(new DataDefinition(0x534E5231, (short) 1281)); //SNR1
		defs.add(new DataDefinition(0x534E5331, (short) 1540)); //SNS1
	}
	
	public SBR(ByteBuffer in) { //bytebuffer MUST start at zero in the file
		defs = new ArrayList<>();
		entries = new ArrayList<>();
		
		if (in.getInt() != 0x53424B52) throw new UnsupportedOperationException("Not a SBR file! Expecting SBKR magic");
		in.getInt(); //non zero
		
		in.position(20);
		bankKey = in.getInt();
		in.getInt(); //0
		var count = in.getInt();
		in.getInt(); //non zero
		var entryOffset = in.getInt();
		var metaOffset = in.getInt();
		in.getInt();
		in.getInt(); //non zero
		
		in.position(metaOffset);
		while (in.position() < entryOffset) defs.add(new DataDefinition(in)); //6-byte
		in.position(entryOffset);
		for (int i=0; i<count; i++) entries.add(new Entry(in)); //10-byte
		for (var e : entries) e.readEntryData(in);
	}
	
	public class DataDefinition{
		public int id;
		public short var;
		public DataDefinition(ByteBuffer in) {
			id = in.getInt();
			var = in.getShort();
		}
		public DataDefinition(int i, short j) {
			id = i;
			var = j;
		}
		public void save(ByteBuffer out) {
			out.putInt(id);
			out.putShort(var);
		}
	}
	public class Entry{
		public int id;
		public short count;
		public int entryOffset;
		
		/**
		 * codec: default 4=>XAS
		 */
		public byte codec;
		/*
		 * channels: default 20=>6ch
		 */
		public byte channels; //not sure about channels because i have 20 ???
		/**
		 * frequency: default 44100Hz
		 */
		public int freq;
		/**
		 * the amount of samples in the track
		 */
		public int samples; // num samples & 0x40000000
		/**
		 * the offset in the stream where to start decoding this entry
		 */
		public int streamOffset;
		
		private ArrayList<EntryData> data;
		
		public Entry(ByteBuffer in) {
			id = in.getInt();
			count = in.getShort();
			entryOffset = in.getInt();
		}
		public Entry(int ID, int samples, int streamOffset, int codec, int channels, int rate) {
			id = ID;
			count = (short) SBR.this.defs.size();
			this.codec = (byte) codec;
			this.channels = (byte) channels;
			freq = rate;
			this.samples = samples | 0x40000000;
			this.streamOffset = streamOffset;
			data = new ArrayList<>(count);
			for (int i=0; i<count; i++) data.add(new EntryData((short) i, 0));
			
			System.out.println("entry %08x codec %d channels %d freq %dHz samples %d".formatted(id, codec, channels, freq, samples & ~40000000));
		}
		public void readEntryData(ByteBuffer in) {
			in.position(entryOffset);
			data = new ArrayList<>(count);
			for (int i = 0; i<count; i++) {
				data.add(new EntryData(in));
			}
			for (var d : data) {
				in.position(d.offset);
				if (SBR.this.defs.get(d.type).id == 0x534E5231) { //SNR1 
					codec = in.get();
					channels = in.get();
					freq = Short.toUnsignedInt(in.getShort());
					samples = in.getInt();
				}
				else if (SBR.this.defs.get(d.type).id == 0x534E5331) { //SNS1 
					streamOffset = in.getInt();
				}
			}
			data = null;
			System.out.println("Entry id %08x @ %d in sbs, codec %d, %d channels, %dHz, %d samples".formatted(id, streamOffset, codec, channels, freq, samples & ~0x40000000));
		}
		
		public class EntryData{
			public short type;
			public int offset;
			public EntryData(ByteBuffer in) {
				type = in.getShort();
				offset = in.getInt();
			}
			public EntryData(short type, int offset) {
				this.type = type;
				this.offset = offset;
			}
			public void save(ByteBuffer out) {
				out.putShort(type);
				out.putInt(offset);
			}
		}

		public void save(ByteBuffer out) {
			out.putInt(id);
			out.putShort(count);
			out.putInt(entryOffset);
		}

		public void saveDataPointers(ByteBuffer out) {
			entryOffset = out.position();
			for (var e : data) {
				e.save(out);
			}
		}
		
		public void saveData(ByteBuffer out) {
			for (var d : data) {
				d.offset = out.position();
				if (SBR.this.defs.get(d.type).id == 0x534E5231) { //SNR1 
					out.put(codec);
					out.put(channels);
					out.putShort((short) freq);
					out.putInt(samples);
				}
				else if (SBR.this.defs.get(d.type).id == 0x534E5331) { //SNS1 
					out.putInt(streamOffset);
				}
			}
		}
	}
	
	public static int getNextIntBE(RandomAccessFile fis) throws IOException {
		return fis.read() << 24 | fis.read() << 16 | fis.read() << 8 | fis.read();
	}
	public static int getNextShortBE(RandomAccessFile fis) throws IOException {
		return fis.read() << 8 | fis.read();
	}
	public static int getNextIntBE(FileInputStream fis) throws IOException {
		return fis.read() << 24 | fis.read() << 16 | fis.read() << 8 | fis.read();
	}
	public static int getNextShortBE(FileInputStream fis) throws IOException {
		return fis.read() << 8 | fis.read();
	}

	public static void compile(File dir, String name) throws FileNotFoundException, IOException {
		var SBR = new SBR(Hash.findVLT(dir.getName()));
		var stream = new FileOutputStream(new File(dir.getParent() + File.separator + name + ".sbs"));
		int inStream = 16;
		stream.write(0x53);
		stream.write(0x42);
		stream.write(0x4B);
		stream.write(0x53); //SBKS
		stream.write(new byte[12]);
		
		for (var f : dir.listFiles()) if (f.getName().endsWith(".snr")) {
			var fis = new FileInputStream(f);
			int snrHeader = getNextIntBE(fis); //0x414AC44
			if ((snrHeader >>> 24) != 0x04) {
				System.out.println("WARNING: File %s is not XAS encoded! SBR/SBS compiler behavior unknown".formatted(f.getName()));
				//fis.close(); 
				//continue; 
			}
			int channels   = ((snrHeader >>> 18) & 0x3F) + 1;
			int groupBytes = 76 * channels;          // 128 samples
			int chunkBytes = 4 * groupBytes;         // 512 samples, same as vanilla's common case

			var samples = getNextIntBE(fis);
			var dataLength = getNextIntBE(fis) - 8; //after header
			getNextIntBE(fis);
						
			SBR.entries.add(SBR.new Entry(
					Hash.findVLT(f.getName().replace(".snr", "")), 
					samples, 				//samples 
					inStream, 				//stream offset
					(snrHeader >>> 24) & 0xff, 	//codec = XAS (4)
					(snrHeader >>> 16) & 0xff, 	//channels = 20 (meta for 6)
					((snrHeader >>> 8) & 0xff) << 8 | (snrHeader & 0xff))); //freq = 44100Hz
			//start of sound data
			
			while (dataLength > chunkBytes) { //write all chunks
				var bytes = fis.readNBytes(chunkBytes);
				
				stream.write(0x00);
				stream.write((chunkBytes + 8) >>> 16);
				stream.write((chunkBytes + 8) >>> 8); //total length 1832
				stream.write((chunkBytes + 8) >>> 0);
	
				stream.write(512 >>> 24); //512 samples
				stream.write(512 >>> 16);
				stream.write(512 >>> 8);
				stream.write(512 >>> 0);
				
				stream.write(bytes);

				samples -= 512;
				dataLength -= chunkBytes;
				inStream += chunkBytes + 8;
			}

			//write last chunk and find padding
			var bytes = fis.readNBytes(dataLength);
			var padding = Utils.findAlignment((inStream + dataLength + 8), 0x10);
			
			stream.write(0x80); //flag to say it's the last chunk
			stream.write((dataLength+padding+8) >>> 16);
			stream.write((dataLength+padding+8) >>> 8);
			stream.write((dataLength+padding+8) >>> 0);

			stream.write(samples >>> 24);
			stream.write(samples >>> 16);
			stream.write(samples >>> 8);
			stream.write(samples >>> 0);
			
			stream.write(bytes);
			stream.write(new byte[padding]);

			inStream += dataLength + padding + 8;
			
			//chunk size 1832/512 samples, last one can be longer (eg 2288/640 samples), then one with ending flag (32768/0x8000) length 2352/603samples
			//no padding after it, padding comprised inside already ??
			
			fis.close();
			
		}
		stream.close();
		
		var sbr = new FileOutputStream(new File(dir.getParent() + File.separator + name + ".sbr"));
		sbr.write(SBR.save());
		sbr.close();
	}
	
	public static final int HEADER_LENGTH = 176;
	
	public byte[] save() {
		
		defs.sort((a,b) -> Integer.compareUnsigned(a.id, b.id));
		entries.sort((a,b) -> Integer.compareUnsigned(a.id, b.id));
		
		int length = HEADER_LENGTH + 
				defs.size() * 6 + entries.size()*10 + 
				entries.size() * defs.size() * 6;
		for (var d : defs) {
			if (d.id == 0x534E5231) { //SNR1 
				length += entries.size() * 8;
			} else if (d.id == 0x534E5331) { //SNS1 
				length += entries.size() * 4;
			}
		}
		
		var out = ByteBuffer.allocate(length);
		out.order(ByteOrder.BIG_ENDIAN);
		out.putInt(0x53424B52);
		out.putInt(0x01100000);
		out.position(20);
		out.putInt(bankKey);
		out.putInt(0);
		out.putInt(entries.size());
		out.putInt(0x30);
		out.putInt(HEADER_LENGTH + defs.size() * 6); //data start
		out.putInt(HEADER_LENGTH); //defs start
		out.putInt(0);
		out.putInt(0x007E0000);
		
		out.position(HEADER_LENGTH);
		for (var d : defs) d.save(out); //OK
		var entriesStart = out.position();
		for (var e : entries) e.save(out); //NOT OK
		var entriesDataStart = out.position();
		for (var e : entries) e.saveDataPointers(out); //NOT OK
		for (var e : entries) e.saveData(out); //OK
		
		out.position(entriesDataStart);
		for (var e : entries) e.saveDataPointers(out); //NOW ITS OK		
		out.position(entriesStart);
		for (var e : entries) e.save(out); //NOW ITS OK
		
		return out.array();
	}

	public static SBR readSBR(File f) throws FileNotFoundException, IOException {
		var fis = new FileInputStream(f);
		var in = ByteBuffer.allocate((int) f.length());
		fis.read(in.array());
		fis.close();
		
		in.order(ByteOrder.BIG_ENDIAN);
		var sbr = new SBR(in);
		return sbr;
	}
	
	public void export(File f, String folder)
			throws FileNotFoundException, IOException {
		var stream = new RandomAccessFile(f, "r");
		folder += File.separator + Hash.getVLT(bankKey);
		for (var e : this.entries) {
			try {
				var exptFile = new File(folder + File.separator + Hash.getVLT(e.id) + ".snr");
				exportEntry(stream, e, exptFile);
			} catch (Exception ex) {
				System.out.println("Error reading entry %s at %d".formatted(Hash.getVLT(e.id), stream.getFilePointer()));
				ex.printStackTrace();
			}
		}
		stream.close();
	}
	public static void exportEntry(RandomAccessFile stream, Entry e, File exptFile) throws FileNotFoundException, IOException {
		var expt = new FileOutputStream(exptFile);
		ByteBuffer snrHeader = ByteBuffer.wrap(new byte[16]);
		snrHeader.order(ByteOrder.BIG_ENDIAN);
		expt.write(snrHeader.array());
		
		stream.seek(e.streamOffset);
		
		boolean hasRemaining = true;
		while (hasRemaining) {
			if (stream.getFilePointer() > stream.length()) {
				System.out.println("Trying to read past the end of file!");
				break;
			}
			var v = getNextIntBE(stream);
			var v1 = v & 0xFF000000; //getNextShortBE(stream);
			var length = v & 0x00FFFFFF;//getNextShortBE(stream);
			//var samples = 
					getNextIntBE(stream);
			//if ((float)(length-8)/(float)samples != 3.562500) System.out.println("Length %d Samples %d Ratio %f".formatted(length, samples, (float)(length-8)/(float)samples));
			switch (v1) {
			case 0x80000000: //was 0x8000
			case 0x45000000:
				hasRemaining = false;
			case 0x00000000:
			case 0x44000000:
				var arr = new byte[length - 8];
				stream.read(arr);
				expt.write(arr);
				break;
			}
		}
		
		expt.close();
		
		//snrHeader.putInt(0x0414AC44);
		snrHeader.put(e.codec);
		snrHeader.put(e.channels);
		snrHeader.putShort((short) e.freq);
		
		snrHeader.putInt(e.samples & ~0x40000000);
		snrHeader.putInt((int) (exptFile.length()-8)); //file size -8
		snrHeader.putInt(e.samples & ~0x40000000);

		var raccess = new RandomAccessFile(exptFile, "rw");
		raccess.write(snrHeader.array());
		raccess.close();
	}



	
	
//	static String file = "C:\\Users\\gaupp\\OneDrive\\Documents\\z NFS MODDING\\a UCE\\MISC\\nis sound\\nis.sbr";
//	static String exptFolder = "C:\\Users\\gaupp\\OneDrive\\Documents\\z NFS MODDING\\a UCE\\MISC\\nis sound";
//	
//	public static void main(String[] args) throws IOException {
//		//var sbr = readSBR(new File(file));
//		//sbr.export(new File(file.replace("sbr", "sbs")), exptFolder);
//		
//		compile(new File(exptFolder + File.separator + "0xC03BCF50"), "nis_recomp");
//	}
}