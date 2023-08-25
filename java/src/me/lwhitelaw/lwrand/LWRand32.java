package me.lwhitelaw.lwrand;

import java.util.random.RandomGenerator;

/**
 * A counter-based PRNG with a period of 2^64 - 2^32, emitting 32-bit values and supporting 2^31 independent streams.
 * <br>
 * The internals are two 32-bit "cores" combined with XOR that advance together, potentially at different strides.
 * Core A has period 2^32 and core B has period 2^32-1, producing a sequence with the above period. Core A and Core B both
 * pass PractRand for 2^29 bytes individually. The PractRand failure point for LWRand32 is conservatively estimated to
 * be 2^56 bytes of output. LWRand32 also passes BigCrush.
 * <br>
 * LWRand32 can also have a stream configured to one of 2^31 possible independent stream values using the {@linkplain #setStream(int)} method.
 * The default stream is stream 0. Generators with different stream values will produce entirely different sequences of values.
 * <br>
 * 64-bit systems should prefer LWRand64 over this generator due to a larger period, better statistical quality, and faster speed on those systems.
 * @author lwhitelaw
 *
 */
public class LWRand32 implements RandomGenerator {
	private static final ThreadLocal<LWRand32> TLR = ThreadLocal.withInitial(() -> {
		int sysHashCode = System.identityHashCode(Thread.currentThread());
		return new LWRand32().setStream(sysHashCode);
	});
	
	private int c; // counter (traverses all 2^32)
	private int d; // counter (traverses 2^32-1 states, 0x00000000-0xFFFFFFFE)
	
	private int stream; // increment of c (any odd number, forms Weyl sequence)
	
	/**
	 * Construct a generator with a seed derived from the system clock.
	 */
	public LWRand32() {
		c = (int) (System.currentTimeMillis());
		d = (int) (System.nanoTime());
		if (d == 0xFFFFFFFF) d = 0; // prevent d being outside of range
		stream = 1;
	}
	
	/**
	 * Construct a generator with an arbitrary seed. 2^32 of the 2^64 possible seeds are aliased to another seed value.
	 * @param seed the seed to use
	 */
	public LWRand32(long seed) {
		c = (int) ((seed >>> 32) & 0x00000000FFFFFFFFL);
		d = (int) (seed & 0x00000000FFFFFFFFL);
		if (d == 0xFFFFFFFF) d = 0; // prevent d being outside of range
		stream = 1;
	}
	
	/**
	 * Return a thread-local generator. The generator is preseeded with the system clock and uses a stream based on the thread's
	 * identity hash code.
	 * @return the thread-local generator
	 */
	public static LWRand32 threadLocal() {
		return TLR.get();
	}
	
	/**
	 * Set the stream to the given stream ID. Only the lower 31 bits are used.
	 * @param streamId the stream ID
	 * @return this generator
	 */
	public LWRand32 setStream(int streamId) {
		stream = (streamId << 1) | 1;
		return this;
	}
	
	/**
	 * Get this generator's stream ID.
	 * @return the stream ID
	 */
	public int getStream() {
		return stream >>> 1;
	}
	
	/**
	 * Advance the generator one step.
	 */
	public void advance() {
		c += stream;
		d++; if (d == 0xFFFFFFFF) d = 0; // wrap d as needed
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
		advance();
		return (mix(c) ^ mix2(d)) >>> (32 - bits);
	}

	/**
	 * Mix function core A.
	 * @param c counter input
	 * @return mixed value
	 */
	private int mix(int c) {
		int v = c;
		
		// Optimised set
		v += 0x09FE1424; v ^= v << 25;
		v += 0x69D61F34; v ^= v >>> 12;
		v += 0xBA1A7EE1; v ^= v << 19;
		
		v += 0xBE637486; v ^= v >>> 3;
		v += 0x2350E6C7; v ^= v >>> 6;
		v += 0x8A16198E; v ^= v << 4;
		
		v += 0x6336D6EC; v ^= v >>> 14;
		v += 0x696B1357; v ^= v << 16;
		v += 0x632A4DD3; v ^= v >>> 8;
		
		// Unicorn set
//			v += 0x46050EDE; v ^= v << 14;
//			v += 0x3E5E6574; v ^= v >>> 9;
//			v += 0x0EB658CE; v ^= v << 3;
//			v += 0x2723B194; v ^= v << 15;
//			
//			v += 0x3278CC42; v ^= v >>> 17;
//			v += 0x7EF79D81; v ^= v >>> 4;
//			v += 0xA4DB621A; v ^= v << 9;
//			v += 0x1B67A201; v ^= v >>> 14;
		
		// Tried-and-true set
//			v += 0x40BEB317; v ^= v >>> 18;
//			v += 0xD4EB3139; v ^= v <<  26;
//			v += 0x94D16407; v ^= v >>> 7;
//			v += 0x593DA2B5; v ^= v <<  21;
//			
//			v += 0x99432295; v ^= v >>> 20;
//			v += 0x404B11EF; v ^= v <<  17;
//			v += 0x3CCAB73D; v ^= v >>> 29;
//			v += 0x93E84FC9; v ^= v <<  13;
//			
//			v += 0xDDE57393; v ^= v >>> 18;
//			v += 0x381CA447; v ^= v <<  7;
//			v += 0x77E9F6B5; v ^= v >>> 27;
//			v += 0x372A7285; v ^= v <<  26;
		return v;
	}
	
	/**
	 * Mix function core B.
	 * @param d counter input
	 * @return mixed value
	 */
	private int mix2(int d) {
		int v = d;
		// Optimised set
		v += 0x38A341AF; v ^= v << 13;
		v += 0x682F2DF8; v ^= v >>> 10;
		v += 0x882611AA; v ^= v << 17;
		
		v += 0x2787052E; v ^= v >>> 3;
		v += 0x562885C4; v ^= v << 8;
		v += 0x1B6E2B3D; v ^= v >>> 20;
		
		v += 0x7EF79D81; v ^= v >>> 4;
		v += 0xA4DB621A; v ^= v << 9;
		v += 0xCC2C66ED; v ^= v >>> 15;
		// Tried-and-true set
//			v += 0xF395F3D7; v ^= v >>> 1;
//			v += 0x993EE12D; v ^= v <<  4;
//			v += 0x9AA4EAA3; v ^= v >>> 4;
//			v += 0x77B21A0B; v ^= v <<  16;
//			
//			v += 0x69E36339; v ^= v >>> 3;
//			v += 0x912BF7EF; v ^= v <<  4;
//			v += 0xC899B3C5; v ^= v >>> 14;
//			v += 0x380A02B3; v ^= v <<  14;
//			
//			v += 0x013386A3; v ^= v >>> 10;
//			v += 0x77D7B81F; v ^= v <<  15;
//			v += 0xE8052793; v ^= v >>> 16;
//			v += 0xCB4724EB; v ^= v <<  3;
		return v;
	}
	
	@Override
	public int nextInt() {
		return next(32);
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * The value is formed from two 32-bit outputs. Therefore, there is no guarantee all possible <code>long</code> values
	 * will be produced.
	 */
	@Override
	public long nextLong() {
		return ((next(32) & 0xFFFFFFFFL) << 32) | (next(32) & 0xFFFFFFFFL);
	}
	
	/**
	 * Copy this generator.
	 * @return a copy
	 */
	public LWRand32 copy() {
		LWRand32 copy = new LWRand32();
		copy.c = this.c;
		copy.d = this.d;
		copy.stream = this.stream;
		return copy;
	}
}
