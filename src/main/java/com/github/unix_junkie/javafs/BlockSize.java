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
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public enum BlockSize {
	/*
	 * Adhere to NTFS block to file system size relationship.
	 */
	B512(toBytes(512, B),	0,			toBytes(512, M),	"512B"),
	K1(toBytes(1, K),	toBytes(512, M),	toBytes(1, G),		"1k"),
	K2(toBytes(2, K),	toBytes(1, G),		toBytes(2, G),		"2k"),
	K4(toBytes(4, K),	0,			toBytes(16, T),		"4k"),
	K8(toBytes(8, K),	toBytes(16, T),		toBytes(32, T),		"8k"),
	K16(toBytes(16, K),	toBytes(32, T),		toBytes(64, T),		"16k"),
	K32(toBytes(32, K),	toBytes(64, T),		toBytes(128, T),	"32k"),
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

	public int getLength() {
		return this.length;
	}

	public long getMinFsLength() {
		return this.minFsLength;
	}

	public long getMaxFsLength() {
		return this.maxFsLength;
	}

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
}
