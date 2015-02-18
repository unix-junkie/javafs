/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.BlockSize.guessBlockSize;
import static com.github.unix_junkie.javafs.FileUtilities.getBlockCount;
import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.Files.exists;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.SPARSE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongFunction;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class FileSystem implements AutoCloseable {
	@Nonnull
	@SuppressWarnings("null")
	private static final Logger LOGGER = Logger.getLogger(FileSystem.class.getName());

	private static final short SECTOR_SIZE = 512;

	@Nonnull
	private final FileChannel channel;

	/**
	 * File system length w/o the boot sector and the inode table.
	 */
	private final long dataAreaLength;

	@Nonnull
	private final BlockSize blockSize;

	private FileSystem(final FileChannel channel, final long dataAreaLength,
			final BlockSize blockSize) throws IOException {
		this.channel = channel;
		this.dataAreaLength = dataAreaLength;
		this.blockSize = blockSize;

		System.out.println(format("%d %s-block(s)",
				Long.valueOf(this.getTotalBlockCount()),
				blockSize.getDescription()));
		System.out.println(format("%d file(s)",
				Long.valueOf(this.getFileCount())));
		System.out.println(format("%d block(s) free",
				Long.valueOf(this.getFreeBlockCount())));
		System.out.println(format("Inode table addressing: %d-bit",
				Byte.valueOf((byte) (8 * this.getBlockAddressSize()))));
		System.out.println(format("Inode table size: %d byte(s) (%d 512-byte sector(s))",
				Long.valueOf(this.getInodeTableSize()),
				Long.valueOf(this.getInodeTableSizeRounded() / 512)));
	}

	public static FileSystem create(final Path path, final long length) throws IOException {
		return create(path, length, guessBlockSize(length));
	}

	public static FileSystem create(final Path path, final long length, final BlockSize blockSize) throws IOException {
		final int blockLength = blockSize.getLength();
		final long dataAreaLength = length % blockLength == 0
				? length
				: length / blockLength * blockLength;
		if (length != dataAreaLength) {
			System.out.println(format("File system length: %d rounded down to %d", Long.valueOf(length), Long.valueOf(dataAreaLength)));
		}
		final OpenOption options[] = exists(path)
				? new OpenOption[] {READ, WRITE, TRUNCATE_EXISTING, CREATE}
				: new OpenOption[] {READ, WRITE, CREATE_NEW, SPARSE};
		@Nonnull
		@SuppressWarnings("null")
		final FileChannel channel = FileChannel.open(path, options);
		final FileSystem fileSystem = new FileSystem(channel, dataAreaLength, blockSize);
		final long fullFileLength = fileSystem.getLength();
		/*
		 * Set file size.
		 */
		channel.position(fullFileLength - 1).write(ByteBuffer.wrap(new byte[] {0x0}));

		fileSystem.writeBootSector();

		/*
		 * Write root directory.
		 */
		final FileSystemEntry root = FileSystemEntry.newDirectory("");
		final long rootDirectorySize = root.getSize();
		assert rootDirectorySize == 0 : rootDirectorySize;

		root.setFileSystem(fileSystem);
		final long blockCount = root.getBlockCount();
		assert blockCount == 1 : blockCount;

		final long rootBlockId = fileSystem.allocateBlocks(blockCount);
		assert rootBlockId == 0 : rootBlockId;
		root.setFirstBlockId(rootBlockId);

		final int bootSectorSize = fileSystem.getBootSectorSize();
		final MappedByteBuffer bootSector = channel.map(READ_WRITE, 0, bootSectorSize);
		bootSector.position(bootSectorSize / 2);
		root.writeMetadataTo(bootSector);

		return fileSystem;
	}

	public BlockSize getBlockSize() {
		return this.blockSize;
	}

	/**
	 * <p>Returns block address size.</p>
	 *
	 * <p>For a 1.44M file system (4k blocks), 16-bit addressing is
	 * sufficient; inode table would be just 720 bytes.</p>
	 *
	 * <p>For a 32G file system (4k blocks again), 24-bit addressing is
	 * sufficient; inode table size is 24M.</p>
	 *
	 * <p>For a file system which is 7.99E large however (the maximum value
	 * which can be represented using {@link Long#MAX_VALUE}) and uses 64k
	 * blocks, 48-bit addressing will be required, and inode table size will
	 * be 768T.</p>
	 *
	 * @return the number of bytes needed to identify a block within the
	 *         inode table. The values returned will range from 1 (8-bit) to
	 *         8 (64-bit).
	 */
	public byte getBlockAddressSize() {
		return getBlockAddressSize(this.getTotalBlockCount());
	}

	static byte getBlockAddressSize(final long blockCount) {
		if (blockCount <= 0) {
			throw new IllegalArgumentException(String.valueOf(blockCount));
		}

		/*
		 * "FAT12" is not used at all: we jump from "FAT8" (up to 1020k)
		 * directly to "FAT16" (1M and up).
		 */
		for (long limit = 0x100; limit <= Long.MAX_VALUE && limit != 0 /* integer overflow */; limit *= 0x100) {
			/*
			 * Here, "strictly less" rather than "less or equal",
			 * because values like 0xFF, 0xFFFF etc will mark EOF
			 * and can't be used for addressing.
			 *
			 * Values like 0x00, 0x0000 etc. (denoting the root
			 * directory) are *needed* for addressing, as root
			 * directory's sub-directories will have a ".." entry
			 * pointing exactly at this address.
			 *
			 * 0x00, 0x0000 etc. within the inode table itself
			 * denotes free space rather than the next file extent
			 * at address zero.
			 */
			if (blockCount < limit) {
				return (byte) (Long.numberOfTrailingZeros(limit) / 8);
			}
		}

		return 8; // "FAT64"
	}

	@SuppressWarnings("static-method")
	public int getBootSectorSize() {
		return SECTOR_SIZE;
	}

	public long getInodeTableSize() {
		return this.getTotalBlockCount() * this.getBlockAddressSize();
	}

	public long getInodeTableSizeRounded() {
		final long inodeTableSize = this.getInodeTableSize();
		return getBlockCount(inodeTableSize, SECTOR_SIZE) * SECTOR_SIZE;
	}

	public long getDataAreaLength() {
		return this.dataAreaLength;
	}

	/**
	 * <p>Returns data area length plus overhead (boot sector and inode table).</p>
	 *
	 * @return data area length plus overhead (boot sector and inode table).
	 */
	public long getLength() {
		return this.getBootSectorSize() + this.getInodeTableSizeRounded() + this.getDataAreaLength();
	}

	/**
	 * @return the total block count.
	 * @see #getFreeBlockCount()
	 */
	public long getTotalBlockCount() {
		return this.dataAreaLength / this.blockSize.getLength();
	}

	@SuppressWarnings("static-method")
	public byte getVersionMajor() {
		return 1;
	}

	@SuppressWarnings("static-method")
	public byte getVersionMinor() {
		return 0;
	}

	public String getVersion() {
		@Nonnull
		@SuppressWarnings("null")
		final String version = format("%d.%d", Byte.valueOf(this.getVersionMajor()), Byte.valueOf(this.getVersionMinor()));
		return version;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see AutoCloseable#close()
	 */
	@Override
	public void close() throws IOException {
		this.channel.close();
	}

	private void writeBootSector() throws IOException {
		/*
		 * Unlike FAT, this filesystem implementation is big-endian,
		 * so we're not changing any byte order here and forth.
		 */
		final ByteBuffer bootSector = ByteBuffer.allocate(this.getBootSectorSize());
		final CharsetEncoder encoder = US_ASCII.newEncoder();
		final String header = "Java File System";
		encoder.encode(CharBuffer.wrap(header), bootSector, true);
		/*
		 * Write [^ to be compatble with a DOS/Windows "type" command
		 */
		bootSector.put((byte) 0x1B);
		/*
		 * File system version.
		 */
		bootSector.put(this.getVersionMajor());
		bootSector.put(this.getVersionMinor());

		/*
		 * File system length and block size.
		 */
		bootSector.putLong(this.dataAreaLength);
		bootSector.putInt(this.blockSize.getLength());

		/*
		 * The standard x86 boot sector magic.
		 */
		bootSector.position(this.getBootSectorSize() - 2);
		bootSector.putShort((short) 0x55AA);

		bootSector.flip();
		this.channel.position(0).write(bootSector);
	}

	public long getFileCount() throws IOException {
		final long t0 = nanoTime();
		try {
			final LongAdder fileCount = new LongAdder();

			this.<Void>scanInodeTable((final long inode) -> {
				if (inode == this.getEofMarker()) {
					fileCount.increment();
				}
				return null;
			});

			return fileCount.sum();
		} finally {
			final long t1 = nanoTime();
			LOGGER.finest(format("Calculated files in %.3f ms", Double.valueOf((t1 - t0) / 1e6)));
		}
	}

	/**
	 * <p>Returns the number of free blocks.</p>
	 *
	 * <p>This operation is as inefficient as a similar operation on FAT
	 * family file systems: since there're no free space bitmaps,
	 * calculating free space results in full inode table scanning.</p>
	 *
	 * @return the number of free blocks.
	 * @throws IOException if an I/O error occurs.
	 * @see #getTotalBlockCount()
	 */
	public long getFreeBlockCount() throws IOException {
		final long t0 = nanoTime();
		try {
			final LongAdder freeBlockCount = new LongAdder();

			this.<Void>scanInodeTable((final long inode) -> {
				/*
				 * If inode table entry is zeroed, the
				 * corresponding block is free.
				 */
				if (inode == 0) {
					freeBlockCount.increment();
				}
				return null;
			});

			return freeBlockCount.sum();
		} finally {
			final long t1 = nanoTime();
			LOGGER.finest(format("Calculated free blocks in %.3f ms", Double.valueOf((t1 - t0) / 1e6)));
		}
	}

	public FileSystemEntry getRoot() throws IOException {
		final int bootSectorSize = this.getBootSectorSize();
		final MappedByteBuffer bootSector = this.channel.map(READ_WRITE, 0, bootSectorSize);
		bootSector.position(bootSectorSize / 2);
		final FileSystemEntry root = FileSystemEntry.readMetadataFrom(bootSector);
		root.setFileSystem(this);
		root.setFirstBlockId(0L);
		return root;
	}

	void setRootDirectorySize(final long rootDirectorySize) throws IOException {
		final int bootSectorSize = this.getBootSectorSize();
		final MappedByteBuffer bootSector = this.channel.map(READ_WRITE, 0, bootSectorSize);
		bootSector.position(bootSectorSize / 2 + FileSystemEntry.SIZE_OFFSET);
		bootSector.putLong(rootDirectorySize);
	}

	/**
	 * @param requestedBlockCount the number of blocks a file will occupy.
	 * @return the id of the first block occupied by the file. The {@code id}
	 *         is guaranteed to be within <code>[0..{@link #getTotalBlockCount() totalBlockCount})</code>
	 *         range.
	 * @throws IOException there's not enough free blocks left to accommodate a new file.
	 * @see #freeBlocks(long)
	 * @see #growInode(long, long)
	 */
	long allocateBlocks(final long requestedBlockCount) throws IOException {
		if (requestedBlockCount <= 0) {
			throw new IllegalArgumentException(format("Requested block count negative or zero: %d",
					Long.valueOf(requestedBlockCount)));
		}
		final long totalBlockCount = this.getTotalBlockCount();
		if (requestedBlockCount > totalBlockCount) {
			throw new IOException(format("%d blocks requested while the filesystem has a maximum of %d",
					Long.valueOf(requestedBlockCount), Long.valueOf(totalBlockCount)));
		}
		final long freeBlockCount = this.getFreeBlockCount();
		if (requestedBlockCount > freeBlockCount) {
			throw new IOException(format("%d blocks requested while only %d available",
					Long.valueOf(requestedBlockCount), Long.valueOf(freeBlockCount)));
		}

		long firstBlockId = 0;

		long allocatedBlockCount = 0;
		final int bootSectorSize = this.getBootSectorSize();
		if (this.getInodeTableSize() <= Integer.MAX_VALUE) {
			final MappedByteBuffer inodeTable = this.channel.map(READ_ONLY, bootSectorSize, this.getInodeTableSize());

			long previousBlockId = -1;
			while (allocatedBlockCount < requestedBlockCount) {
				final long currentBlockId = inodeTable.position() / this.getBlockAddressSize();
				final long inode = this.readInode(inodeTable);
				if (inode == 0) {
					if (previousBlockId == -1) {
						LOGGER.finest(format("Initial block available is %d", Long.valueOf(currentBlockId)));
						firstBlockId = currentBlockId;
					} else {
						LOGGER.finest(format("Referencing block %d from block %d...",
								Long.valueOf(currentBlockId),
								Long.valueOf(previousBlockId)));
						this.writeInode(previousBlockId, currentBlockId);
					}
					previousBlockId = currentBlockId;
					allocatedBlockCount++;
				}
			}
			LOGGER.finest(format("Writing EOF marker to block %d...", Long.valueOf(previousBlockId)));
			this.writeInode(previousBlockId, this.getEofMarker());
		} else {
			/*
			 * TODO: implement for 2G+ inode tables
			 */
			throw new IOException("Inode tables larger than 2G are currently not supported");
		}

		assert firstBlockId >= 0 : firstBlockId;
		assert firstBlockId < totalBlockCount : format("%d >= %d", Long.valueOf(firstBlockId), Long.valueOf(totalBlockCount));

		return firstBlockId;
	}

	void growInode(final long firstBlockId, final long requestedIncrement) throws IOException {
		final long extentStart = this.allocateBlocks(requestedIncrement);
		final long lastBlockId = this.getLastBlockId(firstBlockId);
		this.writeInode(lastBlockId, extentStart);
	}

	long getLastBlockId(final long firstBlockId) throws IOException {
		final long nextBlockId = this.readInode(firstBlockId);
		return nextBlockId == this.getEofMarker() ? firstBlockId : this.getLastBlockId(nextBlockId);
	}

	/**
	 * @param firstBlockId the id of the first block of the file being deleted.
	 * @throws IOException if {@code firstBlockId} points to the free space.
	 * @see #allocateBlocks(long)
	 */
	void freeBlocks(final long firstBlockId) throws IOException {
		if (firstBlockId == 0) {
			throw new IOException("Unable to delete the root directory");
		}

		final long nextBlockId = this.readInode(firstBlockId);
		LOGGER.finest(format("Freeing block %d...", Long.valueOf(firstBlockId)));
		this.writeInode(firstBlockId, 0);

		if (nextBlockId != this.getEofMarker()) {
			this.freeBlocks(nextBlockId);
		} else {
			LOGGER.finest("Space freed.");
		}
	}

	/*
	 * The value returned varies depending on address size.
	 */
	private long getEofMarker() {
		long eofMarker = 0L;
		for (byte b = 0; b < this.getBlockAddressSize(); b++) {
			eofMarker |= 0xff << 8 * b;
		}
		return eofMarker;
	}

	long readInode(final ByteBuffer source) {
		/*
		 * The buffer of blockAddressSize length (1..8 bytes).
		 */
		final byte blockAddressSize = this.getBlockAddressSize();

		/*
		 * Read blockAddressSize bytes and promote the
		 * value to long.
		 */
		long inode = 0L;
		for (byte b = (byte) (blockAddressSize - 1); b >= 0; b--) {
			inode |= (source.get() & 0xff) << 8 * b;
		}

		return inode;
	}

	/**
	 * <p>Reads the inode pointer stored at {@code blockId}. The inode
	 * pointer read can either point to the next inode in a singly-linked
	 * list, or be an {@linkplain #getEofMarker() EOF marker} designating
	 * that {@code blockId} is the last one in the list.</p>
	 *
	 * @param blockId the block id in the inode table
	 * @return the next block id, or an EOF marker.
	 * @throws IOException if an I/O error occurs.
	 */
	private long readInode(final long blockId) throws IOException {
		final byte blockAddressSize = this.getBlockAddressSize();

		final ByteBuffer inodeBuffer = ByteBuffer.allocate(blockAddressSize);

		this.channel.position(this.getBootSectorSize() + blockId * blockAddressSize);
		this.channel.read(inodeBuffer);
		inodeBuffer.flip();
		return this.readInode(inodeBuffer);
	}

	void writeInode(final long inode, final ByteBuffer destination) {
		final byte blockAddressSize = this.getBlockAddressSize();

		for (byte b = (byte) (blockAddressSize - 1); b >= 0; b--) {
			destination.put((byte) (inode >>> 8 * b & 0xff));
		}
	}

	private void writeInode(final long blockId, final long inode) throws IOException {
		final byte blockAddressSize = this.getBlockAddressSize();

		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer inodeBuffer = ByteBuffer.allocate(blockAddressSize);

		this.writeInode(inode, inodeBuffer);
		assert inodeBuffer.position() == inodeBuffer.limit();

		inodeBuffer.flip();
		this.channel.position(this.getBootSectorSize() + blockId * blockAddressSize);
		this.channel.write(inodeBuffer);
	}

	MappedByteBuffer mapBlock(final long blockId) throws IOException {
		final long dataAreaStart = this.getBootSectorSize() + this.getInodeTableSizeRounded();
		final int blockLength = this.getBlockSize().getLength();
		final long dataAreaOffset = blockId * blockLength;
		@Nonnull
		@SuppressWarnings("null")
		final MappedByteBuffer block = this.channel.map(READ_WRITE, dataAreaStart + dataAreaOffset, blockLength);
		return block;
	}

	/**
	 * <p>Returns the list of blocks allocated for (or occupied by) the file
	 * pointed to by {@code firstBlockId}. Since a file occupies at least
	 * one block, the list returned is guaranteed to have a {@linkplain
	 * List#size() size}} of 1 or more.</p>
	 *
	 * <p>The blocks returned can be read from or written to.</p>
	 *
	 * @param firstBlockId the id of the first block allocated for the file.
	 * @return the list of blocks allocated for (or occupied by) the file
	 *         pointed to by {@code firstBlockId}.
	 * @throws IOException if an I/O error occurs.
	 */
	List<MappedByteBuffer> mapBlocks(final long firstBlockId) throws IOException {
		final List<MappedByteBuffer> buffers = new ArrayList<>();

		final long dataAreaStart = this.getBootSectorSize() + this.getInodeTableSizeRounded();
		final int blockLength = this.getBlockSize().getLength();

		long blockId = firstBlockId;
		while (blockId != this.getEofMarker()) {
			final long dataAreaOffset = blockId * blockLength;
			buffers.add(this.channel.map(READ_WRITE, dataAreaStart + dataAreaOffset, blockLength));
			blockId = this.readInode(blockId);
		}

		return buffers;
	}

	/**
	 * <p>Writes file's contents to the previously allocated blocks.</p>
	 *
	 * @param firstBlockId the id of the first block allocated for the file.
	 * @param source the buffer which contains file's contents.
	 * @throws IOException if an I/O error occurs.
	 * @see #writeTo(long, FileChannel)
	 */
	void writeTo(final long firstBlockId, final ByteBuffer source) throws IOException {
		this.writeTo(firstBlockId, source, 0L);
	}

	/**
	 * <p>Appends file's contents to the previously allocated blocks,
	 * starting at {@code destinationOffset}.</p>
	 *
	 * @param firstBlockId the id of the first block allocated for the file.
	 * @param source the buffer which contains file's contents.
	 * @param destinationOffset the offset to start writing at, usually
	 *        previous value of file size.
	 * @throws IOException if an I/O error occurs.
	 * @see #writeTo(long, FileChannel)
	 */
	void writeTo(final long firstBlockId, final ByteBuffer source, final long destinationOffset) throws IOException {
		final List<MappedByteBuffer> blocks = this.mapBlocks(firstBlockId);
		FileUtilities.writeTo(source, blocks, destinationOffset);
	}

	/**
	 * <p>Writes file's contents to the previously allocated blocks.</p>
	 *
	 * @param firstBlockId the id of the first block allocated for the file.
	 * @param source the channel for reading a file on the "real" file system.
	 * @throws IOException if an I/O error occurs.
	 * @see #writeTo(long, ByteBuffer)
	 */
	void writeTo(final long firstBlockId, final FileChannel source) throws IOException {
		final List<MappedByteBuffer> blocks = this.mapBlocks(firstBlockId);
		source.read(blocks.toArray(new MappedByteBuffer[0]));
	}

	private <T> void scanInodeTable(final LongFunction<T> f) throws IOException {
		final int bootSectorSize = this.getBootSectorSize();
		if (this.getInodeTableSize() <= Integer.MAX_VALUE) {
			@Nonnull
			@SuppressWarnings("null")
			final MappedByteBuffer inodeTable = this.channel.map(READ_ONLY, bootSectorSize, this.getInodeTableSize());

			for (long l = 0; l < this.getTotalBlockCount(); l++) {
				final long inode = this.readInode(inodeTable);

				f.apply(inode);
			}

			assert inodeTable.position() == this.getInodeTableSize() : inodeTable.position();
		} else {
			final ByteBuffer inodeBuffer = ByteBuffer.allocate(this.getBlockAddressSize());
			this.channel.position(bootSectorSize);

			/*
			 * Very inefficient: for a 32G file system, scanning a
			 * 24M inode table takes up to 25 seconds.
			 *
			 * TODO: rewrite for 2G+ inode tables
			 */
			for (long l = 0; l < this.getTotalBlockCount(); l++) {
				inodeBuffer.clear();
				this.channel.read(inodeBuffer);
				inodeBuffer.flip();

				final long inode = this.readInode(inodeBuffer);

				f.apply(inode);
			}

			assert this.channel.position() == bootSectorSize + this.getInodeTableSize();
		}
	}
}
