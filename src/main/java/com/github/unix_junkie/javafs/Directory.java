/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;
import static java.lang.String.format;
import static java.util.Collections.emptySet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <p>File system entry which corresponds to a directory.</p>
 *
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class Directory extends FileSystemEntry {
	@Nonnull
	@SuppressWarnings("null")
	private static final Logger LOGGER = Logger.getLogger(Directory.class.getName());

	public Directory(final String name) {
		this(name, new Date());
	}

	public Directory(final Path source) throws IOException {
		super(source);

		/*
		 * For new (empty) directories, size is initially zero,
		 * and gets incremented while children are added.
		 */
		this.dataSize = 0;
	}

	public Directory(final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long size,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name) {
		this(attributes, numberOfLinks, uid, gid, size,
				creationTime,
				modificationTime,
				accessTime, name, null);
	}

	protected Directory(final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long size,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name,
			@Nullable final ByteBuffer encodedName) {
		super(attributes, numberOfLinks, uid, gid, size,
				creationTime,
				modificationTime,
				accessTime, name, encodedName);
	}

	private Directory(final String name, final Date creationTime) {
		this(new PosixAttributes((short) 0755), (byte) 1,
				(short) 0, (short) 0, 0L, creationTime,
				creationTime, creationTime, name);
	}

	/**
	 * <p>Adds a child entry to this directory. The child entry data is
	 * written to the local file system.</p>
	 *
	 * @param child the child entry to add.
	 * @throws IOException if this directory already contains an entry with
	 *         the same name, there's not enough free space on the file system,
	 *         or an I/O error occurs.
	 */
	public void addChild(final FileSystemEntry child) throws IOException {
		this.requireNotDetached();
		if (child instanceof Directory && ((Directory) child).isRootDirectory()) {
			throw new IllegalArgumentException("Can't add / as a child");
		}
		if (!child.isDetached()) {
			throw new IllegalArgumentException("Can only add a detached child");
		}
		final String childName = child.getName();

		final String parentName = this.getName();
		LOGGER.finest(format("Adding %s to %s", childName, parentName));

		/*
		 * List this directory entries and check for duplicates.
		 */
		for (final FileSystemEntry entry : this.list()) {
			if (entry.getName().equals(childName)) {
				throw new IOException(format("File %s already exists in directory %s", childName, parentName));
			}
		}

		/*
		 * Find out whether parent directory needs to grow
		 * (e. g. file names longer than block size)
		 */
		final int sizeIncrement = this.fileSystem.getBlockAddressSize() + child.getMetadataSize();
		final long oldBlockCount = this.getBlockCount();
		LOGGER.finest(format("Parent directory (%d block(s)) will grow for %d byte(s)", Long.valueOf(oldBlockCount), Integer.valueOf(sizeIncrement)));
		final int blockSize = this.fileSystem.getBlockSize().getLength();
		final long newBlockCount = FileUtilities.getBlockCount(this.dataSize + sizeIncrement, blockSize);
		final boolean growthRequired = newBlockCount != oldBlockCount;
		if (growthRequired) {
			/*
			 * Grow the parent directory *before* allocating space
			 * for the child. This *may* result in less fragmentation.
			 */
			LOGGER.finest(format("Parent directory will span %d block(s)", Long.valueOf(newBlockCount)));
			this.fileSystem.growInode(this.firstBlockId, newBlockCount - oldBlockCount);
		}
		final long childBlockCount = FileUtilities.getBlockCount(child.dataSize, blockSize);

		/*
		 * Check for free space.
		 */
		final long requestedBlockCount = childBlockCount + newBlockCount - oldBlockCount;
		final long freeBlockCount = this.fileSystem.getFreeBlockCount();
		if (requestedBlockCount > freeBlockCount) {
			throw new IOException(format("%d blocks requested while only %d available",
					Long.valueOf(requestedBlockCount), Long.valueOf(freeBlockCount)));
		}

		/*
		 * Allocate inode for the child entry.
		 */
		final long childInode = this.fileSystem.allocateBlocks(childBlockCount);
		child.setFileSystem(this.fileSystem);
		child.setFirstBlockId(childInode);
		child.writeData();

		/*
		 * Update the parent's data area, growing it if necessary.
		 */
		if (growthRequired) {
			/*
			 * The data area may (and most probably will) be non-contiguous.
			 * At best we can have an array of mapped buffers to write to.
			 */
			final long blockCount = this.fileSystem.getBlockCount(this.firstBlockId);
			assert blockCount > 1 : blockCount;

			@Nonnull
			@SuppressWarnings("null")
			final ByteBuffer inodeWithMetadata = ByteBuffer.allocate(this.fileSystem.getBlockAddressSize() + child.getMetadataSize());

			this.fileSystem.writeInode(childInode, inodeWithMetadata);
			child.writeMetadataTo(inodeWithMetadata);

			inodeWithMetadata.flip();
			this.fileSystem.writeTo(this.firstBlockId, inodeWithMetadata, this.dataSize);
		} else {
			/*
			 * Get the last (incomplete) block, set its position and write.
			 *
			 * This may be a little bit faster than iterating over
			 * each block occupied by the parent directory.
			 */
			final long lastBlockId = this.fileSystem.getLastBlockId(this.firstBlockId);
			final MappedByteBuffer lastBlock = this.fileSystem.mapBlock(lastBlockId);
			final int position = (int) this.dataSize % this.fileSystem.getBlockSize().getLength();
			assert this.dataSize == 0 ^ position != 0;
			lastBlock.position(position);

			this.fileSystem.writeInode(childInode, lastBlock);
			child.writeMetadataTo(lastBlock);
		}

		/*
		 * Parent directory size has changed.
		 * Record the change in the parent's parent (or boot sector for the root directory).
		 */
		this.dataSize += sizeIncrement;
		if (this.isRootDirectory()) {
			this.fileSystem.setRootDirectorySize(this.dataSize);
		} else {
			// XXX: Implement for directories other than the root one.
			throw new UnsupportedOperationException("Parent (..) links in directories are not stored yet.");
		}

		child.source = null;
	}

	/**
	 * <p>Removes (unlinks) the child entry denoted by {@code child} from
	 * this directory.</p>
	 *
	 * @param child the name of the child entry to remove. 
	 * @throws IOException if this directory doesn't contain an entry named
	 *         {@code child}, or an I/O error occurs.
	 */
	public void unlink(final String child) throws IOException {
		this.requireNotDetached();

		final Set<FileSystemEntry> children = this.list();
		if (!children.stream().anyMatch(entry -> entry.getName().equals(child))) {
			throw new IOException(format("rm: cannot remove `%s': No such file or directory", child));
		}

		final Set<FileSystemEntry> matchingChildren = children.stream().filter(entry -> entry.getName().equals(child)).collect(Collectors.toSet());
		assert matchingChildren.size() == 1;
		final FileSystemEntry matchingChild = matchingChildren.iterator().next();

		final int sizeDecrement = this.fileSystem.getBlockAddressSize() + matchingChild.getMetadataSize();

		this.fileSystem.freeBlocks(matchingChild.firstBlockId);
		matchingChild.setFileSystem(null);
		matchingChild.setFirstBlockId(-1);
		assert matchingChild.isDetached();
		final boolean contained = children.remove(matchingChild);
		assert contained;

		/*
		 * Rewrite parent directory entry.
		 *
		 * XXX: Implement for directory sizes larger than 2G.
		 */
		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer metadata = ByteBuffer.allocate((int) this.dataSize - sizeDecrement);
		for (final FileSystemEntry remainingChild : children) {
			this.fileSystem.writeInode(remainingChild.firstBlockId, metadata);
			remainingChild.writeMetadataTo(metadata);
		}
		metadata.flip();

		this.fileSystem.writeTo(this.firstBlockId, metadata);

		/*
		 * Parent directory size has changed.
		 * Record the change in the parent's parent (or boot sector for the root directory).
		 */
		this.dataSize -= sizeDecrement;
		if (this.isRootDirectory()) {
			this.fileSystem.setRootDirectorySize(this.dataSize);
		} else {
			// XXX: Implement for directories other than the root one.
			throw new UnsupportedOperationException("Parent (..) links in directories are not stored yet.");
		}
	}

	@Override
	public String getName() {
		return this.isRootDirectory() ? "/" : this.name;
	}

	/**
	 * <p>Lists the contents of this directory.</p>
	 *
	 * @return the list of entries this directory contains.
	 * @throws IOException if an I/O error occurs.
	 */
	public Set<FileSystemEntry> list() throws IOException {
		this.requireNotDetached();

		if (this.dataSize == 0) {
			@Nonnull
			@SuppressWarnings("null")
			final Set<FileSystemEntry> emptySet = emptySet();
			return emptySet;
		}

		if (this.dataSize > Integer.MAX_VALUE) {
			// TODO: Implement for directories spanning more than 2G
			LOGGER.severe(format("Directories larger than 2G are not supported: %d", Long.valueOf(this.dataSize)));
			throw new UnsupportedOperationException();
		}

		final List<MappedByteBuffer> blocks = this.fileSystem.mapBlocks(this.firstBlockId);
		final int blockCount = blocks.size();
		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer contents = blockCount == 1 ? blocks.iterator().next() : this.getData();
		return this.list(contents);
	}

	private Set<FileSystemEntry> list(final ByteBuffer contents) throws IOException {
		final Set<FileSystemEntry> children = new LinkedHashSet<>();

		long bytesRead = 0L;
		while (bytesRead < this.dataSize) {
			final long inode = this.fileSystem.readInode(contents);
			bytesRead += this.fileSystem.getBlockAddressSize();

			final FileSystemEntry child = readMetadataFrom(contents);
			bytesRead += child.getMetadataSize();

			child.setFileSystem(this.fileSystem);
			child.setFirstBlockId(inode);
			children.add(child);
		}

		assert bytesRead == this.dataSize : bytesRead;

		return children;
	}

	private boolean isRootDirectory() {
		return this.name.length() == 0;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see FileSystemEntry#writeData()
	 */
	@Override
	protected void writeData() {
		this.requireNotDetached();

		/*
		 * Since the new directory is always empty, we don't need
		 * to store anything into the corresponding block (yet).
		 *
		 * TODO: Once links to parent directories are implemented, we *will* have to store a link to parent here.
		 */
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see FileSystemEntry#getType()
	 */
	@Override
	protected FileType getType() {
		return DIRECTORY;
	}
}
