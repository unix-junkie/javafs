/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.BlockSize.B512;
import static com.github.unix_junkie.javafs.BlockSize.K1;
import static com.github.unix_junkie.javafs.BlockSize.K16;
import static com.github.unix_junkie.javafs.BlockSize.K2;
import static com.github.unix_junkie.javafs.BlockSize.K32;
import static com.github.unix_junkie.javafs.BlockSize.K4;
import static com.github.unix_junkie.javafs.BlockSize.K64;
import static com.github.unix_junkie.javafs.BlockSize.K8;
import static com.github.unix_junkie.javafs.BlockSize.getSupportedBlockSizes;
import static com.github.unix_junkie.javafs.BlockSize.guessBlockSize;
import static com.github.unix_junkie.javafs.SizeUnit.E;
import static com.github.unix_junkie.javafs.SizeUnit.G;
import static com.github.unix_junkie.javafs.SizeUnit.M;
import static com.github.unix_junkie.javafs.SizeUnit.T;
import static com.github.unix_junkie.javafs.SizeUnit.toBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(JUnit4.class)
public final class BlockSizeTest {
	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testFsLength() {
		assertEquals(BlockSize.getMinimum().getLength(), B512.getMinFsLength());
		assertEquals(toBytes(512, M) - 1, B512.getMaxFsLength());

		assertEquals(toBytes(512, M), K1.getMinFsLength());
		assertEquals(toBytes(1, G) - 1, K1.getMaxFsLength());

		assertEquals(toBytes(1, G), K2.getMinFsLength());
		assertEquals(toBytes(2, G) - 1, K2.getMaxFsLength());

		assertEquals(K4.getLength(), K4.getMinFsLength());
		assertEquals(toBytes(16, T) - 1, K4.getMaxFsLength());

		assertEquals(toBytes(16, T), K8.getMinFsLength());
		assertEquals(toBytes(32, T) - 1, K8.getMaxFsLength());

		assertEquals(toBytes(32, T), K16.getMinFsLength());
		assertEquals(toBytes(64, T) - 1, K16.getMaxFsLength());

		assertEquals(toBytes(64, T), K32.getMinFsLength());
		assertEquals(toBytes(128, T) - 1, K32.getMaxFsLength());

		assertEquals(toBytes(128, T), K64.getMinFsLength());
		assertEquals(toBytes(8, E) - 1, K64.getMaxFsLength());
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testSupportedBlockSizes() {
		assertArrayEquals(new BlockSize[] {B512, K4}, getSupportedBlockSizes(toBytes(512, M) - 1).toArray());
		assertArrayEquals(new BlockSize[] {K1, K4}, getSupportedBlockSizes(toBytes(512, M)).toArray());
		assertArrayEquals(new BlockSize[] {K1, K4}, getSupportedBlockSizes(toBytes(1, G) - 1).toArray());
		assertArrayEquals(new BlockSize[] {K2, K4}, getSupportedBlockSizes(toBytes(1, G)).toArray());
		assertArrayEquals(new BlockSize[] {K2, K4}, getSupportedBlockSizes(toBytes(2, G) - 1).toArray());
		assertArrayEquals(new BlockSize[] {K4}, getSupportedBlockSizes(toBytes(2, G)).toArray());
		assertArrayEquals(new BlockSize[] {K8}, getSupportedBlockSizes(toBytes(16, T)).toArray());
		assertArrayEquals(new BlockSize[] {K16}, getSupportedBlockSizes(toBytes(32, T)).toArray());
		assertArrayEquals(new BlockSize[] {K32}, getSupportedBlockSizes(toBytes(64, T)).toArray());
		assertArrayEquals(new BlockSize[] {K64}, getSupportedBlockSizes(toBytes(128, T)).toArray());
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testGuessBlockSizes() {
		assertEquals(K4, guessBlockSize(toBytes(512, M) - 1));
		assertEquals(K4, guessBlockSize(toBytes(512, M)));
		assertEquals(K4, guessBlockSize(toBytes(1, G) - 1));
		assertEquals(K4, guessBlockSize(toBytes(1, G)));
		assertEquals(K4, guessBlockSize(toBytes(2, G) - 1));
		assertEquals(K4, guessBlockSize(toBytes(2, G)));
		assertEquals(K8, guessBlockSize(toBytes(16, T)));
		assertEquals(K16, guessBlockSize(toBytes(32, T)));
		assertEquals(K32, guessBlockSize(toBytes(64, T)));
		assertEquals(K64, guessBlockSize(toBytes(128, T)));
	}
}
