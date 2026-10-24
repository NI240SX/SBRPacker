package fr.needeka.sbr;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("unused")
public class SBREditorCLI {

	public static File commandLocation = null;
	
	public static void main(String[] args) {
		
		if (args.length >0) {
			//launching with arguments runs a special mode without user input
			String concatenatedArgs = "";
			for (var s : args) {
				if (s.contains(" ")) concatenatedArgs += "\"" +s + "\" ";
				else concatenatedArgs += s + " ";
			}
//			System.out.println("Arguments provided : "+concatenatedArgs);
			parseCommand(concatenatedArgs);
			
		} else {
			//launching without arguments runs the command-line utility
			
			showHelp();
			commandLocation = new File("").getAbsoluteFile();
			
			System.out.print("\n%s> ".formatted(commandLocation == null ? "" : commandLocation.getAbsolutePath()));
	
	        var in = new BufferedReader(new InputStreamReader(System.in));
	        String l;
	        
	        try {
				while (!(l = in.readLine()).equals("exit")) {
					parseCommand(l);
			    	System.gc();
					System.out.print("\n%s> ".formatted(commandLocation == null ? "" : commandLocation.getAbsolutePath()));
				}
			} catch (Exception e) {
				System.out.println("An unexpected error happened while parsing user input, the program has to close.");
				e.printStackTrace();
			}
	        
		}

	}

	public static void parseCommand(String l) {
		try{
			switch (l.split(" ")[0]) {
	
			case "help":
			case "?":
				showHelp();
				break;
				
			case "compile":
				compile(l);
				break;
				
			case "decompile":
				decompile(l);
				break;
				
			case "trol":
				System.out.println("java.lang.err.trolling.grand.syndicate.existence");
				break;
								
			case "cd":
				String src;
				try {
					l = l.substring(3);					
					//read first path
					if (l.startsWith("\"")) {
						l = l.substring(1);
						src = l.split("\"")[0];
						l = l.substring(src.length()+1);
					} else {
						src = l.split(" ")[0];
						l = l.substring(src.length());
					}
					src = resolveRelativePath(src);

					if (new File(src).isDirectory()) {
						commandLocation = new File(src).getCanonicalFile();
					}
				} catch ( Exception e) {
					System.out.println("Invalid command syntax : cd <new directory>");
				}
				break;
				
			default:
				System.out.println("Unknown command : \""+l+"\". Type \"help\" for a list of commands.");
			}
			
			
		} catch (Exception e) {
			System.out.println("An unexpected error happened while parsing the command : "+e.getMessage());
			e.printStackTrace();
		}
	}

	private static void decompile(String l) {
		String src;
		String dst;
		try {
			l = l.substring(10);
			
			//read first path
			if (l.startsWith("\"")) {
				l = l.substring(1);
				src = l.split("\"")[0];
				l = l.substring(src.length()+1);
			} else {
				src = l.split(" ")[0];
				l = l.substring(src.length());
			}
			
			if (l.length()>1) {
				l = l.substring(1);
				
				//read second path
				if (l.startsWith("\"")) {
					l = l.substring(1);
					dst = l.split("\"")[0];
	//					l = l.substring(dst.length()+2);
				} else {
					dst = l.split(" ")[0];
	//					l = l.substring(dst.length()+1);
				}
			} else {
				dst = src.replace(src.split(File.separator)[src.split(File.separator).length-1], "");
			}
			
			src = resolveRelativePath(src);
			dst = resolveRelativePath(dst);
			sanitizeFolderEnd(dst);

			new File(dst).getParentFile().mkdirs();
			
			try {
				long t = System.currentTimeMillis();
				System.out.println("Loading SBR...");

				var sbr = SBR.readSBR(new File(src));
				System.out.println("SBR read in "+(System.currentTimeMillis()-t)+" ms.");
				
				System.out.println("Extracting SNRs...");
				t = System.currentTimeMillis();

				sbr.export(new File(src.replace("sbr", "sbs")), dst);

				System.out.println("SNRs exported in "+(System.currentTimeMillis()-t)+" ms.");
				System.out.println("Operation successful !");
				
			} catch (Exception e) {
				System.out.printf("An error occured: %s\n", e.getMessage());
				e.printStackTrace();
			}
			
		}catch (ArrayIndexOutOfBoundsException | StringIndexOutOfBoundsException e) {System.out.println("Invalid command syntax : decompile <BIN source> [glTF/OBJ/Z3D output]");}
	}

	private static void compile(String l) {
		String src;
		String dst;
		try {
			l = l.substring(8);
			
			//read first path
			if (l.startsWith("\"")) {
				l = l.substring(1);
				src = l.split("\"")[0];
				l = l.substring(src.length()+1);
			} else {
				src = l.split(" ")[0];
				l = l.substring(src.length());
			}
			
			if (l.length()>1) {
				l = l.substring(1);
				
				//read second path
				if (l.startsWith("\"")) {
					l = l.substring(1);
					dst = l.split("\"")[0];
				} else {
					dst = l.split(" ")[0];
				}
			} else {
				dst = src + "";
			}
			
			dst = dst.replace(dst.split("\\.")[dst.split("\\.").length-1], ""); //remove extension
			src = resolveRelativePath(src);
			dst = resolveRelativePath(dst);
			sanitizeFolderEnd(src);

			backUpFile(new File(dst));
			new File(dst).getParentFile().mkdirs();
			
			try {
				
				long t = System.currentTimeMillis();
				System.out.println("Creating SBR and SBS...");
				SBR.compile(new File(src), dst);
				System.out.println("Files created in "+(System.currentTimeMillis()-t)+" ms.");
				System.out.println("Operation successful !");
			} catch (Exception e) {
				System.out.printf("An error occured: %s\n", e.getMessage());
				e.printStackTrace();
			}
			
		} catch (ArrayIndexOutOfBoundsException | StringIndexOutOfBoundsException e) {System.out.println("Invalid command syntax : compile <OBJ/Z3D source> <BIN output>");}
	}

	public static void showHelp() {
		System.out.printf("""
				 ===   SBR Editor v1.0 by needeka   ===
				
				Supported files:
					EA SBR+SBS sound streams
					EA SNR sound tracks
				
				Command syntax:
					<something> means MANDATORY
					[something] means OPTIONAL
					write commands without brackets
					if a file path contains spaces, wrap the path in quotes ""
				
				Commands:
					compile <input folder> [SBR output]
					    - compiles an SBR + SBS from a folder with SNRs inside
					      uses the folder's name as a bank key, and each file's name as an entry key 
					      (straight hex key or Attrib/Vlt string hashing)

					decompile <SBR input> [output folder]
					    - extract the SNRs referenced by an SBR
					      creates a folder using the SBR's bank key, containing SNRs each named by their key
					    
					help
					    - show this info
					    
					exit
					    - exits the program
				""");
	}

	private static void sanitizeFolderEnd(String s) {
		if (s.endsWith("\\") || s.endsWith("/")) s = s.substring(0, s.length()-1);
	}
	private static String resolveRelativePath(String src) {
		if (src == null) return null;
		if (new File(src).isAbsolute()) return src;
		return commandLocation.getAbsolutePath() + File.separator + src;
	}
	public static void backUpFile(File f) {
		if (f.isFile()) {
			File f_old = new File(f.getAbsolutePath());
			f_old.renameTo(new File(f_old.getAbsolutePath() + ".bak_" + new SimpleDateFormat("yyMMdd-HHmmss").format(new Date(f_old.lastModified()))));
		}
	}
}
