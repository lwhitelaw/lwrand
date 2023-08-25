package me.lwhitelaw.lwrand;

import java.util.random.RandomGenerator;

/**
 * A counter-based PRNG with a period of 2^128 - 2^64, emitting 64-bit values and supporting 2^63 independent streams. 32-bit values
 * are emitted as alternating high and low halves of the 64-bit result, therefore this generator has 2-equidistribution for 32-bit
 * outputs. Additionally, 32-bit outputs are faster for this same reason.
 * <br>
 * The internals are two 64-bit "cores" combined with XOR that advance together, potentially at different strides.
 * Core A has period 2^64 and core B has period 2^64-1, producing a sequence with the above period. Core A and Core B both
 * pass PractRand for 2^40 bytes individually before testing was stopped. The actual fail points may be higher.
 * The PractRand failure point for LWRand64 is conservatively estimated to be 2^80 bytes of output.
 * LWRand64 also passes BigCrush.
 * <br>
 * LWRand64 can also have a stream configured to one of 2^63 possible independent stream values using the {@linkplain #setStream(long)} method.
 * The default stream is stream 0. Generators with different stream values will produce entirely different sequences of values.
 * @author lwhitelaw
 *
 */
public class LWRand64 implements RandomGenerator.JumpableGenerator {
	private static final ThreadLocal<LWRand64> TLR = ThreadLocal.withInitial(() -> {
		int sysHashCode = System.identityHashCode(Thread.currentThread());
		return new LWRand64().setStream(sysHashCode);
	});

	private long c; // counter (traverses all 2^64)
	private long d; // counter (traverses 2^64-1 states, 0x0000000000000000-0xFFFFFFFFFFFFFFFE)
	
	private long stream; // increment of c (any odd number, forms Weyl sequence)
	
	long value; // buffered bits from last RNG call
	int haveBits; // number of valid bits in value
	
	/**
	 * Construct a generator with a seed derived from the system clock.
	 */
	public LWRand64() {
		c = System.currentTimeMillis();
		d = System.nanoTime(); if (d == 0xFFFFFFFF_FFFFFFFFL) d = 0; // prevent d being outside of range
		stream = 1;
	}
	
	/**
	 * Construct a generator with an arbitrary seed given as two 64-bit halves. 2^64 of the 2^128 possible
	 * seeds are aliased to another seed value.
	 * @param seedh the high 64 bits of the seed
	 * @param seedl the low 64 bits of the seed
	 */
	public LWRand64(long seedh, long seedl) {
		c = seedl;
		d = seedh; if (d == 0xFFFFFFFF_FFFFFFFFL) d = 0; // prevent d being outside of range
		stream = 1;
	}
	
	/**
	 * Return a thread-local generator. The generator is preseeded with the system clock and uses a stream based on the thread's
	 * identity hash code.
	 * @return the thread-local generator
	 */
	public static LWRand64 threadLocal() {
		return TLR.get();
	}
	
	/**
	 * Set the stream to the given stream ID. Only the lower 63 bits are used.
	 * @param streamId the stream ID
	 * @return this generator
	 */
	public LWRand64 setStream(long streamId) {
		stream = (streamId << 1) | 1L;
		return this;
	}
	
	/**
	 * Get this generator's stream ID.
	 * @return the stream ID
	 */
	public long getStream() {
		return stream >>> 1;
	}
	
	/**
	 * Advance the generator one step.
	 */
	public void advance() {
		c += stream;
		d++; if (d == 0xFFFFFFFF_FFFFFFFFL) d = 0;
	}
	
	/**
	 * Return true if the state of both internal counters are zero.
	 * @return true if both counters are zero
	 */
	public boolean isStateZero() {
		return c == 0 && d == 0;
	}
	
	/**
	 * Advance the generator, produce a 32-bit value, and truncate to the requested number of bits.
	 * @param bits the number of bits to produce from 0-32
	 * @return a random n-bit value
	 */
	public int next(int bits) {
		// refill if needed
		if (haveBits == 0) {
			advance();
			value = mix(c) ^ mix2(d);
			haveBits = 64;
		}
		// extract
		int value32 = (int)(value & 0xFFFFFFFFL);
		// remove from 64bit value for accounting
		value = (value >>> 32);
		haveBits -= 32;
		// return
		return value32 >>> (32 - bits);
	}

	/**
	 * Mix function core A.
	 * @param c counter input
	 * @return mixed value
	 */
	private long mix(long c) {
		long v = c;
		// Optimised 4 - avalanche image looks good - used for testing
		// PractRand failed this at 2^40
		// av = 0.03169826840338819
		// sav = 0.0018870179137751777
//			v += 0xE134F70DB9447CA2L; v ^= v >>> 34;
//			v += 0x60DA52CA490014FBL; v ^= v << 15;
//			v += 0x98680816D4A0F62CL; v ^= v << 14;
//			v += 0x0139F24880D222E1L; v ^= v >>> 12;
//			
//			v += 0x3C9F910C32549830L; v ^= v << 19;
//			v += 0x1BA2531C2F6B68CCL; v ^= v << 5;
//			v += 0xB41AAE6E6D26888DL; v ^= v << 19;
//			v += 0x3626882462E0872CL; v ^= v << 8;
//			
//			v += 0xE74F229FA609C504L; v ^= v >>> 31;
//			v += 0x9B7967F228580975L; v ^= v >>> 12;
		
		// Need to try this set sometime - used tightened parametres
		// Avalanche image is *near* full grey with some contrast spots
		// Re-exam is similar. Avalanche testing may not be able to expose 64-bit function flaws properly
		// PractRand failed this at 2^41; good enough for me
		v += 0xFF7B3242A5346FABL; v ^= v << 19;
		v += 0xE29E698A09D89099L; v ^= v << 6;
		v += 0x764E4B4DD29E68E7L; v ^= v >>> 28;
		v += 0xBD45085DD1D02E75L; v ^= v << 8;
		
		v += 0x08632527BC62F9F4L; v ^= v >>> 22;
		v += 0xF6F843C0DC630B04L; v ^= v >>> 2;
		v += 0x0273CC983D9F1994L; v ^= v >>> 10;
		v += 0xA4CB2895080EF775L; v ^= v << 18;
		
		v += 0x9AC877D396CCD88CL; v ^= v >>> 22;
		v += 0xF61ECC72C148D762L; v ^= v << 28;
		
		// Optimised 3 - flaws
//			v += 0xFD7008BA6F5D4B23L; v ^= v << 41;
//			v += 0x85489C9F3F4A8B62L; v ^= v << 7;
//			v += 0xF5B1027389679609L; v ^= v >>> 24;
//			v += 0xA8D81DA084234BF4L; v ^= v >>> 15;
//			
//			v += 0x05A173054CDC8EC3L; v ^= v >>> 35;
//			v += 0x53BEE0BEC9B44239L; v ^= v << 27;
//			v += 0xC386276CBC0B87BAL; v ^= v << 19;
//			v += 0x3088492813F7E614L; v ^= v >>> 36;
//			
//			v += 0xB388903EEA825AC3L; v ^= v << 20;
//			v += 0x5A3A42A559F1C505L; v ^= v >>> 42;
		
		// Optimised 2 - flaws
//			v += 0x470791830C125562L; v ^= v << 3;
//			v += 0x1841F39BA1DDDA6EL; v ^= v << 30;
//			v += 0x59CC86F3D3A3F393L; v ^= v >>> 11;
//			v += 0xABB8674CF56962E4L; v ^= v << 7;
//			
//			v += 0x5240E2C61367D976L; v ^= v >>> 25;
//			v += 0x958EE44601BC8723L; v ^= v << 4;
//			v += 0x16A9C6F70FE351E5L; v ^= v >>> 35;
//			v += 0x1F6059C16CB64191L; v ^= v << 38;
//			
//			v += 0x20A805BBD32B399AL; v ^= v >>> 5;
//			v += 0x6AF19B0D74C82368L; v ^= v << 6;
		
		// Optimised 10op
		// PractRand failed this at 2^39
		
//			v += 0xF55E5AE6C700FCB6L; v ^= v << 10;
//			v += 0x0FBA145E5420D2C9L; v ^= v >>> 6;
//			v += 0x5EFFD64B65A63AD4L; v ^= v << 29;
//			v += 0x9E1153E7CF025028L; v ^= v >>> 18;
//			
//			v += 0x918470C2B4F0F977L; v ^= v >>> 5;
//			v += 0x953C337A9D1AFFDBL; v ^= v >>> 41;
//			v += 0xF136E93E3E379E48L; v ^= v >>> 30;
//			v += 0x09C5A5D9C78AE813L; v ^= v << 27;
//			
//			v += 0x6B08A61EAD542568L; v ^= v << 14;
//			v += 0x287D38C68ED705B8L; v ^= v >>> 43;
		
		// Optimised
//			v += 0x18CC8571BE7C78F7L; v ^= v >>> 22;
//			v += 0xB4D0D3067BC16102L; v ^= v >>> 58;
//			v += 0xF86CCEAA51E2153FL; v ^= v << 25;
//			v += 0x98680816D4A0F62CL; v ^= v << 14;
//			
//			v += 0xFDE579A0E743B3C2L; v ^= v >>> 16;
//			v += 0x6B2B654828EC442EL; v ^= v >>> 21;
//			v += 0x223518F9868C16E2L; v ^= v >>> 46;
//			v += 0x2275FCAA57C5019FL; v ^= v << 39;
//			
//			v += 0xF7987A3F964AC1FAL; v ^= v << 56;
//			v += 0x3626882462E0872CL; v ^= v << 8;
//			v += 0xB5BA85DE452E6D83L; v ^= v << 3;
//			v += 0x0A951F64174039F2L; v ^= v >>> 25;
		
		// Tried-and-true to at least 2^40
//			v += 0xC22545B27DA2BB23L; v ^= v >>> 19;
//			v += 0x598BDC3A121EBEF9L; v ^= v <<  19;
//			v += 0xB05FC85E077E169BL; v ^= v >>> 21;
//			v += 0x15978A22F45ABE85L; v ^= v <<  12;
//			
//			v += 0x16B18296671E922FL; v ^= v >>> 3;
//			v += 0x64E8BBDF93782523L; v ^= v <<  19;
//			v += 0xC9EF33CD01F36FFBL; v ^= v >>> 1;
//			v += 0x5590DBD147384177L; v ^= v <<  12;
//			
//			v += 0x4457E49208E5552BL; v ^= v >>> 22;
//			v += 0x5C88CA32C0C68E29L; v ^= v <<  8;
//			v += 0xE65647934FC65E07L; v ^= v >>> 26;
//			v += 0x524A2C61FEC6743FL; v ^= v <<  9;
		return v;
	}
	
	/**
	 * Mix function core B.
	 * @param d counter input
	 * @return mixed value
	 */
	private long mix2(long d) {
		long v = d;
//			return 0;
		// Optimised
//			v += 0x6F0208508F3431D9L; v ^= v >>> 22;
//			v += 0x5B0EC6B841DCFD79L; v ^= v << 34;
//			v += 0xE20E25BA8D5A63D3L; v ^= v << 26;
//			v += 0x3F8700D21736262CL; v ^= v << 18;
//			
//			v += 0x43F7FA0AB262EBB7L; v ^= v >>> 27;
//			v += 0x5B58E3642761676CL; v ^= v << 3;
//			v += 0x0E9D78DAFB5B79A0L; v ^= v << 11;
//			v += 0xBD557F8C155C6303L; v ^= v >>> 17;
//			
//			v += 0x19E815DECFA7F440L; v ^= v >>> 48;
//			v += 0xBF2B6C8ED533A087L; v ^= v << 20;
		
		// Optimised 2 - looks better on avalanche image - used for testing
		// PractRand has passed 2^41 on this parameter set. Testing stopped here.
		// This is the "golden set"
		// av = 0.031305172728805564
		// sav = 0.0010976563945813986
		v += 0x38D506988BA5CF97L; v ^= v << 33;
		v += 0x1CFF974774D783BBL; v ^= v >>> 10;
		v += 0xD26F6DAD13252AF1L; v ^= v << 1;
		v += 0x6057C672DC20E52AL; v ^= v >>> 24;
		
		v += 0x1B154FB993729895L; v ^= v << 38;
		v += 0xDA5A1A05BFA175F0L; v ^= v >>> 18;
		v += 0x80E81C053D0D9A0DL; v ^= v >>> 3;
		v += 0x3DB7D7C167BAE229L; v ^= v << 5;
		
		v += 0x19C1405A41403449L; v ^= v >>> 33;
		v += 0xCAC96BEA9B351A4AL; v ^= v << 23;
		
		// Randomly generated
//			v += 0x2F385BBCF3065831L; v ^= v >>> 8;
//			v += 0x33A47C1BF7EE67D1L; v ^= v <<  6;
//			v += 0xDD3E83F4BEABC94BL; v ^= v >>> 14;
//			v += 0x9D479B43DD9174F5L; v ^= v <<  29;
//			
//			v += 0x89566EE03F2ED94FL; v ^= v >>> 28;
//			v += 0x544775D29AB9C63BL; v ^= v <<  30;
//			v += 0x3F2374374761A991L; v ^= v >>> 30;
//			v += 0xF536603E2B72490BL; v ^= v <<  28;
//			
//			v += 0xE8B200A7D2BE06D7L; v ^= v >>> 28;
//			v += 0x5EC1B6DBD86E42DFL; v ^= v <<  13;
//			v += 0xE2A251D06DFC628DL; v ^= v >>> 21;
//			v += 0xB9DC821CC202A22BL; v ^= v <<  11;
		return v;
	}
	
	@Override
	public int nextInt() {
		return next(32);
	}
	
	@Override
	public long nextLong() {
		// The generator prefers to emit the high and low 32-bits of the 64-bit result normally
		// This has to bypass that
		haveBits = 0; // force 32-bit resync next call
		advance();
		return mix(c) ^ mix2(d);
	}

	/**
	 * Copy this generator.
	 */
	@Override
	public LWRand64 copy() {
		LWRand64 copy = new LWRand64();
		copy.c = this.c;
		copy.d = this.d;
		copy.stream = this.stream;
		copy.value = this.value;
		copy.haveBits = this.haveBits;
		return copy;
	}

	/**
	 * Jump the state by 2^64 states.
	 */
	@Override
	public void jump() {
		// Only increment d.
		d++; if (d == 0xFFFFFFFF_FFFFFFFFL) d = 0;
		
		/*
		 * Why?
		 * c has 2^64 period, so jumping 2^64 means c does not change.
		 * This doesn't change regardless of stream value.
		 * d has period 2^64-1, so 2^64 - (2^64-1) = 1.
		 * Therefore increment d once.
		 */
	}

	@Override
	public double jumpDistance() {
		return 0x1P+64; // 2^64
	}
}
