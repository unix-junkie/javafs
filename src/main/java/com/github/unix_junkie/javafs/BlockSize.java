/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.SizeUnit.B;
import static com.github.unix_junkie.javafs.SizeUnit.G;
import static com.github.unix_junkie.javafs.SizeUnit.K;
import static com.github.unix_junkie.javafs.SizeUnit.M;
import static com.github.unix_junkie.javafs.SizeUnit.T;
import static com.github.unix_junkie.javafs.SizeUnit.toBytes;
import static java.lang.Math.max;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toSet;

import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;

/**
 * <p>{@code BlockSize} contains possible block sized this file system driver
 * supports.</p>
 *
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public enum BlockSize {
	/*
	 * Adhere to NTFS block to file system size relationship.
	 */
	/**
	 * 512 B block; file systems up to 512 MB (legacy).
	 */
	B512(toBytes(512, B),	0,			toBytes(512, M),	"512B"),
	/**
	 * 1 kB block; file systems from 512 MB up to 1 GB (legacy).
	 */
	K1(toBytes(1, K),	toBytes(512, M),	toBytes(1, G),		"1k"),
	/**
	 * 2 kB block; file systems from 1GB up to 2 GB (legacy).
	 */
	K2(toBytes(2, K),	toBytes(1, G),		toBytes(2, G),		"2k"),
	/**
	 * 4 kB block; file systems up to 16 TB.
	 */
	K4(toBytes(4, K),	0,			toBytes(16, T),		"4k"),
	/**
	 * 8 kB block; file systems from 16 TB up to 32 TB.
	 */
	K8(toBytes(8, K),	toBytes(16, T),		toBytes(32, T),		"8k"),
	/**
	 * 16 kB block; file systems from 32 TB up to 64 TB.
	 */
	K16(toBytes(16, K),	toBytes(32, T),		toBytes(64, T),		"16k"),
	/**
	 * 32 kB block; file systems from 64 TB to 128 TB.
	 */
	K32(toBytes(32, K),	toBytes(64, T),		toBytes(128, T),	"32k"),
	/**
	 * 64 kB block; file systems from 128 TB to 8 EB.
	 */
	K64(toBytes(64, K),	toBytes(128, T),	Long.MAX_VALUE,		"64k"),
	;

	private final int length;

	private final long minFsLength;

	private final long maxFsLength;

	@Nonnull
	private final String description;

	/**
	 * @param length the length of the block, in bytes.
	 * @param minFsLength minimum file system length in bytes, inclusive.
	 * @param maxFsLength maximum file system length in bytes (exclusive, if
	 *        the value is a power of 2; inclusive otherwise).
	 * @param description the human-readable description of this block size
	 *        (similar to that produced by "du" or "df" commands with "-h"
	 *        switch).
	 */
	private BlockSize(final long length, final long minFsLength, final long maxFsLength, final String description) {
		this.length = (int) length;
		this.minFsLength = max(minFsLength, length);
		this.maxFsLength = (maxFsLength & maxFsLength - 1) == 0 ? maxFsLength - 1 : maxFsLength;
		this.description = description;
	}

	/**
	 * @return this block size, in bytes.
	 */
	public int getLength() {
		return this.length;
	}

	/**
	 * @return the minimum file system length which supports this block size,
	 *         in bytes.
	 */
	public long getMinFsLength() {
		return this.minFsLength;
	}

	/**
	 * @return the maximum file system length which supports this block size,
	 *         in bytes.
	 */
	public long getMaxFsLength() {
		return this.maxFsLength;
	}

	/**
	 * @return the human-readable form of this block size.
	 */
	public String getDescription() {
		return this.description;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see Enum#toString()
	 */
	@Override
	public String toString() {
		return this.getDescription();
	}

	public static BlockSize getMinimum() {
		final TreeSet<BlockSize> values = new TreeSet<>(comparingInt(BlockSize::getLength));
		values.addAll(asList(values()));
		final BlockSize minimum = values.first();
		assert minimum != null;
		return minimum;
	}

	public static BlockSize getMaximum() {
		final TreeSet<BlockSize> values = new TreeSet<>(comparingInt(BlockSize::getLength));
		values.addAll(asList(values()));
		final BlockSize maximum = values.last();
		assert maximum != null;
		return maximum;
	}

	public static SortedSet<BlockSize> getSupportedBlockSizes(final long length) {
		if (length <= 0) {
			throw new IllegalArgumentException(format("File system size negative or zero: %d", Long.valueOf(length)));
		}

		final int minimumLength = BlockSize.getMinimum().getLength();
		if (length < minimumLength) {
			throw new IllegalArgumentException(format("File system size: %d is less than the minimum block size: %d", Long.valueOf(length), Integer.valueOf(minimumLength)));
		}

		return new TreeSet<>(asList(BlockSize.values()).stream().filter(bs -> bs.getMinFsLength() <= length && length <= bs.getMaxFsLength()).collect(toSet()));
	}

	public static BlockSize guessBlockSize(final long length) {
		final SortedSet<BlockSize> supportedBlockSizes = getSupportedBlockSizes(length);
		if (supportedBlockSizes.isEmpty()) {
			throw new IllegalArgumentException(format("No block size matching file system length found: %d", Long.valueOf(length)));
		}

		final BlockSize maximum = supportedBlockSizes.last();
		assert maximum != null;
		return maximum;
	}

	public static BlockSize valueOf(final int length) {
		for (final BlockSize blockSize : values()) {
			if (blockSize.getLength() == length) {
				return blockSize;
			}
		}
		throw new IllegalArgumentException(format("Unsupported block size: %d", Integer.valueOf(length)));
	}
}
