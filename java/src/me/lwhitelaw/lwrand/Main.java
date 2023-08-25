package me.lwhitelaw.lwrand;

public class Main {
	private static final int BUF_SIZE = 1048576;
	
	public static void main(String[] args) {
		if (args.length > 0 && args[0].equals("lwrand32")) {
			LWRand32 r = new LWRand32();
			byte[] b = new byte[BUF_SIZE];
			for (;;) {
				r.nextBytes(b);
				System.out.writeBytes(b);
			}
		}
		if (args.length > 0 && args[0].equals("lwrand64")) {
			LWRand64 r = new LWRand64();
			byte[] b = new byte[BUF_SIZE];
			for (;;) {
				r.nextBytes(b);
				System.out.writeBytes(b);
			}
		}
		System.out.println("Usage: (lwrand32 | lwrand64)");
	}
}
