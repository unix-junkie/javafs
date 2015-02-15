/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;
import static com.github.unix_junkie.javafs.FileType.SYMBOLIC_LINK;
import static com.github.unix_junkie.javafs.FileUtilities.getGid;
import static com.github.unix_junkie.javafs.FileUtilities.getNlinks;
import static com.github.unix_junkie.javafs.FileUtilities.getPosixAttributes;
import static com.github.unix_junkie.javafs.FileUtilities.getType;
import static com.github.unix_junkie.javafs.FileUtilities.getUid;
import static com.github.unix_junkie.javafs.FileUtilities.gidToString;
import static com.github.unix_junkie.javafs.FileUtilities.uidToString;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.Files.readSymbolicLink;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;
import static java.util.Collections.emptySet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class FileSystemEntry {
	@Nonnull
	@SuppressWarnings("null")
	private static final Logger LOGGER = Logger.getLogger(FileSystemEntry.class.getName());

	/**
	 * Consists of the following:
	 * <ul>
	 * <li>4 bytes: the complete length of the entry itself, including file name,</li>
	 * <li>4 bits: file type (4 bits),</li>
	 * <li>12 bits: POSIX attributes (12 bits),</li>
	 * <li>1 byte: nlinks,</li>
	 * <li>2 bytes: uid,</li>
	 * <li>2 bytes: gid,</li>
	 * <li>8 bytes: file size,</li>
	 * <li>8 bytes: ctime,</li>
	 * <li>8 bytes: mtime,</li>
	 * <li>8 bytes: atime.</li>
	 * </ul>
	 */
	private static byte NAME_OFFSET = 43;

	static byte SIZE_OFFSET = 11;

	@Nonnull
	private final FileType type;

	@Nonnull
	private final PosixAttributes attributes;

	private final byte numberOfLinks;

	private final short uid;

	private final short gid;

	/**
	 * If a child entry is added to or removed from a directory, its size
	 * gets updated.
	 */
	private long size;

	@Nonnull
	private final Date creationTime;

	@Nonnull
	private final Date modificationTime;

	@Nonnull
	private final Date accessTime;

	@Nonnull
	private final String name;

	@Nullable
	private ByteBuffer encodedName;

	@Nullable
	private Path source;

	/**
	 * @see #firstBlockId
	 */
	@Nullable
	private FileSystem fileSystem;

	/**
	 * <p>The first block id of this entry in the inode table, or -1 for
	 * detached entries.</p>
	 *
	 * @see #fileSystem
	 */
	private long firstBlockId = -1;

	public FileSystemEntry(final Path source) throws IOException {
		final BasicFileAttributes attrs = readAttributes(source, BasicFileAttributes.class, NOFOLLOW_LINKS);
		if (attrs.isOther()) {
			throw new IllegalArgumentException(source + " is either a socket or a device");
		}
		this.type = getType(source);
		this.attributes = getPosixAttributes(source);
		this.numberOfLinks = getNlinks(source);
		this.uid = getUid(source);
		this.gid = getGid(source);
		/*
		 * For new (empty) directories, size is initially zero,
		 * and gets incremented while children are added.
		 */
		this.size = this.type != DIRECTORY ? attrs.size() : 0;
		this.creationTime = new Date(attrs.creationTime().toMillis());
		this.modificationTime = new Date(attrs.lastModifiedTime().toMillis());
		this.accessTime = new Date(attrs.lastAccessTime().toMillis());
		@Nonnull
		@SuppressWarnings("null")
		final String fileName = source.getFileName().toString();
		this.name = validateName(fileName, this.type);

		this.source = source;
	}

	public FileSystemEntry(final FileType type,
			final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long size,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name) {
		this(type, attributes, numberOfLinks, uid, gid, size,
				new Date(creationTime.getTime()),
				new Date(modificationTime.getTime()),
				new Date(accessTime.getTime()), name, null);
	}

	private FileSystemEntry(final FileType type,
			final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long size,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name,
			@Nullable final ByteBuffer encodedName) {
		this.type = type;
		this.attributes = attributes;
		this.numberOfLinks = numberOfLinks;
		this.uid = uid;
		this.gid = gid;
		this.size = size;
		this.creationTime = creationTime;
		this.modificationTime = modificationTime;
		this.accessTime = accessTime;
		this.name = validateName(name, type);
		this.encodedName = encodedName;
	}

	public static FileSystemEntry newDirectory(final String name) {
		final Date creationTime = new Date();
		return new FileSystemEntry(DIRECTORY,
				new PosixAttributes((short) 0755), (byte) 1,
				(short) 0, (short) 0, 0L, creationTime,
				creationTime, creationTime, name);
	}

	static FileSystemEntry readMetadataFrom(final ByteBuffer source) throws IOException {
		final int oldPosition = source.position();

		final int dataLength = source.getInt();
		final short typeAndAttributes = source.getShort();
		@Nonnull
		@SuppressWarnings("null")
		final FileType type = FileType.values()[typeAndAttributes >> 12];
		final PosixAttributes attributes = new PosixAttributes((short) (typeAndAttributes & 0x0FFF));
		final byte numberOfLinks = source.get();
		final short uid = source.getShort();
		final short gid = source.getShort();
		final long size = source.getLong();
		final Date creationTime = new Date(source.getLong());
		final Date modificationTime = new Date(source.getLong());
		final Date accessTime = new Date(source.getLong());

		final int newPosition = source.position();
		assert newPosition - oldPosition == NAME_OFFSET : newPosition - oldPosition;

		final byte encodedName0[] = new byte[dataLength - NAME_OFFSET];
		source.get(encodedName0);
		final ByteBuffer encodedName = ByteBuffer.wrap(encodedName0);
		@Nonnull
		@SuppressWarnings("null")
		final String name = UTF_8.newDecoder().decode(encodedName).toString();

		return new FileSystemEntry(type, attributes, numberOfLinks, uid,
				gid, size, creationTime, modificationTime,
				accessTime, name, encodedName);
	}

	void writeMetadataTo(final ByteBuffer destination) throws IOException {
		/*
		 * This entry address (variable, 1-8 bytes, depending on file system)
		 * is stored/read separately.
		 */
		final int oldPosition = destination.position();

		destination.putInt(this.getDataLength());
		final short typeAndAttributes = (short) ((short) (this.type.ordinal() << 12) | this.attributes.getValue());
		destination.putShort(typeAndAttributes);
		destination.put(this.numberOfLinks);
		destination.putShort(this.uid);
		destination.putShort(this.gid);
		destination.putLong(this.size);
		destination.putLong(this.creationTime.getTime());
		destination.putLong(this.modificationTime.getTime());
		destination.putLong(this.accessTime.getTime());

		final int newPosition = destination.position();
		assert newPosition - oldPosition == NAME_OFFSET : newPosition - oldPosition;

		destination.put(this.getEncodedName());
	}

	/**
	 * <p>Writes file contents to {@code destination}. Only suitable for
	 * files under 2G.</p>
	 *
	 * @param destination the buffer file contents will be writen to.
	 * @throws IOException if an I/O error occurs, particularly if {@code
	 *         destination} is not large enough to accommodate the file
	 *         contents.
	 */
	void writeDataTo(final ByteBuffer destination) throws IOException {
		this.requireNotDetached();

		final List<MappedByteBuffer> blocks = this.fileSystem.mapBlocks(this.firstBlockId);
		final int remainder = (int) (this.size % this.fileSystem.getBlockSize().getLength());
		if (remainder != 0) {
			blocks.get(blocks.size() - 1).limit(remainder);
		}
		for (final MappedByteBuffer block : blocks) {
			destination.put(block);
		}
	}

	/**
	 * @return the size of this entry (the space in the data area required
	 *         to store the entry). For newly created directories w/o
	 *         children is 0.
	 */
	public long getSize() {
		return this.size;
	}

	public String getName() {
		return this.isRootDirectory() ? "/" : this.name;
	}

	/**
	 * @return how many bytes this entry will occupy in its parent directory
	 *         data area (or boot sector for the root directory).
	 * @throws IOException if an I/O error occurs.
	 */
	public int getDataLength() throws IOException {
		return NAME_OFFSET + this.getEncodedName().limit();
	}

	public void addChild(final FileSystemEntry child) throws IOException {
		if (this.type != DIRECTORY) {
			throw new IllegalStateException("Can only add children to directories");
		}
		this.requireNotDetached();
		if (child.isRootDirectory()) {
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
		final int sizeIncrement = this.fileSystem.getBlockAddressSize() + child.getDataLength();
		final long oldBlockCount = this.getBlockCount();
		LOGGER.finest(format("Parent directory (%d block(s)) will grow for %d byte(s)", Long.valueOf(oldBlockCount), Integer.valueOf(sizeIncrement)));
		final int blockSize = this.fileSystem.getBlockSize().getLength();
		final long newBlockCount = (this.size + sizeIncrement) / blockSize + 1;
		final boolean growthRequired = newBlockCount != oldBlockCount;
		if (growthRequired) {
			/*
			 * Grow the parent directory *before* allocating space
			 * for the child. This *may* result in less fragmentation.
			 */
			LOGGER.finest(format("Parent directory will span %d block(s)", Long.valueOf(newBlockCount)));
			this.fileSystem.growInode(this.firstBlockId, newBlockCount - oldBlockCount);
		}
		final long childBlockCount = child.size / blockSize + 1;

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
		 * Allocate inode for the child directory.
		 */
		final long childInode = this.fileSystem.allocateBlocks(childBlockCount);
		switch (child.type) {
		case FILE:
			if (child.source != null) {
				try (final FileChannel channel = FileChannel.open(child.source, READ)) {
					final List<MappedByteBuffer> blocks = this.fileSystem.mapBlocks(childInode);
					channel.read(blocks.toArray(new MappedByteBuffer[0]));
				}
			}
			break;
		case SYMBOLIC_LINK:
			if (child.source != null) {
				/*
				 * TODO: Implement symlink support.
				 */
				LOGGER.info(format("%s%s", child, readSymbolicLink(child.source)));
			}
			break;
		case DIRECTORY:
		default:
			/*
			 * Since the new directory is always empty, we don't need
			 * to store anything into the corresponding block (yet).
			 *
			 * TODO: Once links to parent directories are implemented, we *will* have to store a link to parent here.
			 */
			break;
		}

		/*
		 * Update the parent's data area, growing it if necessary.
		 */
		if (growthRequired) {
			/*
			 * The data area may (and most probably will) be non-contiguous.
			 * At best we can have an array of mapped buffers to write to.
			 *
			 * XXX: Implement for child entries larger than 1 block
			 */
			final List<MappedByteBuffer> blocks = this.fileSystem.mapBlocks(this.firstBlockId);
			final int blockCount = blocks.size();
			assert blockCount > 1 : blockCount;
			LOGGER.severe(format("Unable to deal with %d blocks while growing a directory", Integer.valueOf(blockCount)));
			throw new UnsupportedOperationException();
		} else {
			/*
			 * Get the last (incomplete) block, set its position and write.
			 */
			final long lastBlockId = this.fileSystem.getLastBlockId(this.firstBlockId);
			final MappedByteBuffer lastBlock = this.fileSystem.mapBlock(lastBlockId);
			final int position = (int) this.size % this.fileSystem.getBlockSize().getLength();
			assert this.size == 0 ^ position != 0;
			lastBlock.position(position);
			this.fileSystem.writeInode(childInode, lastBlock);
			child.writeMetadataTo(lastBlock);
		}

		/*
		 * Parent directory size has changed.
		 * Record the change in the parent's parent (or boot sector for the root directory).
		 */
		this.size += sizeIncrement;
		if (this.isRootDirectory()) {
			this.fileSystem.setRootDirectorySize(this.size);
		} else {
			// XXX: Implement for directories other than the root one.
			throw new UnsupportedOperationException("Parent (..) links in directories are not stored yet.");
		}

		child.setFileSystem(this.fileSystem);
		child.setFirstBlockId(childInode);
		child.source = null;
	}

	public void unlink(final String child) throws IOException {
		if (this.type != DIRECTORY) {
			throw new IllegalStateException("Can only add children to directories");
		}
		this.requireNotDetached();

		final Set<FileSystemEntry> children = this.list();
		if (!children.stream().anyMatch(entry -> entry.getName().equals(child))) {
			throw new IOException(format("rm: cannot remove `%s': No such file or directory", child));
		}

		final Set<FileSystemEntry> matchingChildren = children.stream().filter(entry -> entry.getName().equals(child)).collect(Collectors.toSet());
		assert matchingChildren.size() == 1;
		final FileSystemEntry matchingChild = matchingChildren.iterator().next();

		final int sizeDecrement = this.fileSystem.getBlockAddressSize() + matchingChild.getDataLength();

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
		final ByteBuffer metadata = ByteBuffer.allocate((int) this.size - sizeDecrement);
		for (final FileSystemEntry remainingChild : children) {
			this.fileSystem.writeInode(remainingChild.firstBlockId, metadata);
			remainingChild.writeMetadataTo(metadata);
		}
		metadata.flip();
		final int originalLimit = metadata.limit();

		final List<MappedByteBuffer> blocks = this.fileSystem.mapBlocks(this.firstBlockId);
		for (final MappedByteBuffer block : blocks) {
			/*
			 * Write at most "blockSize" bytes to each block.
			 */
			metadata.limit(min(metadata.position() + this.fileSystem.getBlockSize().getLength(), originalLimit));
			block.put(metadata);

		}

		/*
		 * Parent directory size has changed.
		 * Record the change in the parent's parent (or boot sector for the root directory).
		 */
		this.size -= sizeDecrement;
		if (this.isRootDirectory()) {
			this.fileSystem.setRootDirectorySize(this.size);
		} else {
			// XXX: Implement for directories other than the root one.
			throw new UnsupportedOperationException("Parent (..) links in directories are not stored yet.");
		}
	}

	/**
	 * @return how many blocks this entry does (or will) occupy on the
	 *         file system.
	 * @throws IllegalStateException if this entry is detached.
	 */
	public long getBlockCount() {
		this.requireNotDetached();

		final int blockSize = this.fileSystem.getBlockSize().getLength();
		return this.size / blockSize + 1;
	}

	public Set<FileSystemEntry> list() throws IOException {
		if (this.type != DIRECTORY) {
			throw new IllegalStateException("Can only list contents of a directory");
		}
		this.requireNotDetached();

		if (this.size == 0) {
			@Nonnull
			@SuppressWarnings("null")
			final Set<FileSystemEntry> emptySet = emptySet();
			return emptySet;
		}

		final Set<FileSystemEntry> children = new LinkedHashSet<>();

		final List<MappedByteBuffer> blocks = this.fileSystem.mapBlocks(this.firstBlockId);
		final int blockCount = blocks.size();
		if (blockCount == 1) {
			@Nonnull
			@SuppressWarnings("null")
			final MappedByteBuffer block = blocks.iterator().next();

			long bytesRead = 0L;
			while (bytesRead < this.size) {
				final long inode = this.fileSystem.readInode(block);
				bytesRead += this.fileSystem.getBlockAddressSize();

				final FileSystemEntry child = readMetadataFrom(block);
				bytesRead += child.getDataLength();

				child.setFileSystem(this.fileSystem);
				child.setFirstBlockId(inode);
				children.add(child);
			}

			assert bytesRead == this.size : bytesRead;
		} else {
			// XXX: Implement for directories spanning more than one block
			LOGGER.severe(format("Unable to deal with %d blocks while growing a directory", Integer.valueOf(blockCount)));
			throw new UnsupportedOperationException();
		}

		return children;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see Object#toString()
	 */
	@Override
	public String toString() {
		final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		final StringBuilder builder = new StringBuilder();
		builder.append(this.type.getType());
		builder.append(this.attributes).append(' ');
		builder.append(this.numberOfLinks).append(' ');
		builder.append(uidToString(this.uid)).append(' ');
		builder.append(gidToString(this.gid)).append(' ');
		builder.append(this.size).append(' ');
		builder.append(dateFormat.format(this.modificationTime)).append(' ');
		/*
		 * Root directory has an empty name.
		 */
		builder.append(this.getName());
		if (this.type == SYMBOLIC_LINK) {
			builder.append(" -> ");
		}
		@Nonnull
		@SuppressWarnings("null")
		final String s = builder.toString();
		return s;
	}

	void setFileSystem(@Nullable final FileSystem fileSystem) {
		this.fileSystem = fileSystem;
	}

	void setFirstBlockId(final long firstBlockId) {
		this.firstBlockId = firstBlockId;
	}

	@SuppressWarnings("null")
	private ByteBuffer getEncodedName() throws CharacterCodingException {
		return (ByteBuffer) (this.encodedName == null
				? this.encodedName = UTF_8.newEncoder().encode(CharBuffer.wrap(this.name))
				: this.encodedName).position(0);
	}

	private static String validateName(final String name, final FileType type) {
		if (name.indexOf('/') != -1 || name.indexOf('\0') != -1) {
			/*
			 * Certain file systems may allow slashes in file names.
			 * We do not.
			 */
			throw new IllegalArgumentException(name);
		}
		if ((name.equals(".") || name.equals("..")) && type != DIRECTORY) {
			throw new IllegalArgumentException(format("%s can only be a directory", name));
		}

		return name;
	}

	private boolean isRootDirectory() {
		return this.name.length() == 0;
	}

	private boolean isDetached() {
		return this.fileSystem == null;
	}

	private void requireNotDetached() {
		if (this.isDetached()) {
			throw new IllegalStateException("Detached file system entry");
		}
	}
}
