/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;
import static com.github.unix_junkie.javafs.FileUtilities.getGid;
import static com.github.unix_junkie.javafs.FileUtilities.getNlinks;
import static com.github.unix_junkie.javafs.FileUtilities.getPosixAttributes;
import static com.github.unix_junkie.javafs.FileUtilities.getUid;
import static com.github.unix_junkie.javafs.FileUtilities.gidToString;
import static com.github.unix_junkie.javafs.FileUtilities.uidToString;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <p>File system entry, a common superclass of {@link File}, {@link Directory}
 * and {@link SymbolicLink}.</p>
 *
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 * @see File
 * @see Directory
 * @see SymbolicLink
 */
public abstract class FileSystemEntry {
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
	private final PosixAttributes attributes;

	private final byte numberOfLinks;

	private final short uid;

	private final short gid;

	/**
	 * If a child entry is added to or removed from a directory, its size
	 * gets updated.
	 */
	protected long dataSize;

	@Nonnull
	private final Date creationTime;

	@Nonnull
	private final Date modificationTime;

	@Nonnull
	private final Date accessTime;

	/**
	 * The name of this file system entry w/o the directory part (the
	 * <em>basename</em>).
	 */
	@Nonnull
	protected final String name;

	@Nullable
	private ByteBuffer encodedName;

	/**
	 * For a {@linkplain #isDetached() detached} entry, may contain the
	 * source of this entry at the external file system. For attached entries 
	 * (i.e. those which have been already written to the {@linkplain
	 * FileSystem local file system}) is always {@code null}.
	 */
	@Nullable
	protected Path source;

	/**
	 * @see #firstBlockId
	 */
	@Nullable
	protected FileSystem fileSystem;

	/**
	 * <p>The first block id of this entry in the inode table, or -1 for
	 * detached entries.</p>
	 *
	 * @see #fileSystem
	 */
	protected long firstBlockId = -1;

	/**
	 * <p>Creates a detached file system entry, using an existing {@code
	 * path} at the external file system.</p>
	 *
	 * @param source the source of this entry at the external file system.
	 * @throws IOException if an I/O error occurs.
	 */
	protected FileSystemEntry(final Path source) throws IOException {
		final BasicFileAttributes attrs = readAttributes(source, BasicFileAttributes.class, NOFOLLOW_LINKS);
		if (attrs.isOther()) {
			throw new IllegalArgumentException(source + " is either a socket or a device");
		}

		final FileType type = this.getType();
		final FileType sourceType = FileUtilities.getType(source);
		if (type != sourceType) {
			throw new IOException(format("Attempting to construct a %s from %s", type, sourceType));
		}

		this.attributes = getPosixAttributes(source);
		this.numberOfLinks = getNlinks(source);
		this.uid = getUid(source);
		this.gid = getGid(source);
		this.creationTime = new Date(attrs.creationTime().toMillis());
		this.modificationTime = new Date(attrs.lastModifiedTime().toMillis());
		this.accessTime = new Date(attrs.lastAccessTime().toMillis());
		@Nonnull
		@SuppressWarnings("null")
		final String fileName = source.getFileName().toString();
		this.name = validateName(fileName, type);

		this.source = source;
	}

	/**
	 * <p>Creates a detached file system entry from the complete metadata.
	 * </p>
	 *
	 * @param attributes POSIX attributes.
	 * @param numberOfLinks number of hard links.
	 * @param uid owner Id (UID).
	 * @param gid group Id (GID).
	 * @param dataSize the data length of this entry.
	 * @param creationTime creation time.
	 * @param modificationTime modification time.
	 * @param accessTime access time.
	 * @param name the name of this file or directory (just the last name in
	 *        the pathname's name sequence).
	 * @param encodedName {@code name} encoded into a byte array, or {@code null}.
	 */
	protected FileSystemEntry(final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long dataSize,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name,
			@Nullable final ByteBuffer encodedName) {
		this.attributes = attributes;
		this.numberOfLinks = numberOfLinks;
		this.uid = uid;
		this.gid = gid;
		this.dataSize = dataSize;
		this.creationTime = new Date(creationTime.getTime());
		this.modificationTime = new Date(modificationTime.getTime());
		this.accessTime = new Date(accessTime.getTime());
		this.name = validateName(name, this.getType());
		this.encodedName = encodedName;
	}

	private static FileSystemEntry newInstance(final FileType type,
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
		switch (type) {
		case DIRECTORY:
			return new Directory(attributes, numberOfLinks, uid, gid, size,
					creationTime, modificationTime, accessTime,
					name, encodedName);
		case FILE:
			return new File(attributes, numberOfLinks, uid, gid, size,
					creationTime, modificationTime, accessTime,
					name, encodedName);
		case SYMBOLIC_LINK:
		default:
			return new SymbolicLink(attributes, numberOfLinks, uid, gid, size,
					creationTime, modificationTime, accessTime,
					name, encodedName);
		}
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

		return newInstance(type, attributes, numberOfLinks, uid,
				gid, size, creationTime, modificationTime,
				accessTime, name, encodedName);
	}

	final void writeMetadataTo(final ByteBuffer destination) throws IOException {
		/*
		 * This entry address (variable, 1-8 bytes, depending on file system)
		 * is stored/read separately.
		 */
		final int oldPosition = destination.position();

		destination.putInt(this.getMetadataSize());
		final short typeAndAttributes = (short) ((short) (this.getType().ordinal() << 12) | this.attributes.getValue());
		destination.putShort(typeAndAttributes);
		destination.put(this.numberOfLinks);
		destination.putShort(this.uid);
		destination.putShort(this.gid);
		destination.putLong(this.dataSize);
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
	 * <p>With respect to the file system, this is essentially a <em>read</em>
	 * operation.</p>
	 *
	 * @param destination the buffer file contents will be writen to.
	 * @throws IOException if an I/O error occurs, particularly if {@code
	 *         destination} is not large enough to accommodate the file
	 *         contents.
	 * @see #getData()
	 * @see #writeData()
	 */
	private void writeDataTo(final ByteBuffer destination) throws IOException {
		this.requireNotDetached();

		final List<MappedByteBuffer> blocks = this.fileSystem.mapBlocks(this.firstBlockId);

		if (destination.limit() - destination.position() == 0) {
			/*
			 * Empty file.
			 */
			assert blocks.size() == 1;
			return;
		}

		final int remainder = (int) (this.dataSize % this.fileSystem.getBlockSize().getLength());
		if (remainder != 0) {
			blocks.get(blocks.size() - 1).limit(remainder);
		}
		for (final MappedByteBuffer block : blocks) {
			destination.put(block);
		}
	}

	/**
	 * <p>Writes this entry contents to the underlying file system.</p>
	 *
	 * <p>With respect to the file system, this is essentially a <em>write</em>
	 * operation.</p>
	 *
	 * @throws IOException if an I/O error occurs.
	 * @see #writeDataTo(ByteBuffer)
	 */
	protected abstract void writeData() throws IOException;

	/**
	 * @return the type of this entry.
	 */
	protected abstract FileType getType();

	/**
	 * <p>Returns file contents. Only suitable for files under 2G.</p>
	 *
	 * @return the file contents.
	 * @throws IOException if an I/O error occurs.
	 * @see #writeDataTo(ByteBuffer)
	 */
	final ByteBuffer getData() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer data = ByteBuffer.allocate((int) min(this.dataSize, Integer.MAX_VALUE));
		this.writeDataTo(data);
		data.flip();
		return data;
	}

	/**
	 * @return the size of this entry (the space in the data area required
	 *         to store the entry). For newly created directories w/o
	 *         children is 0.
	 */
	public final long getDataSize() {
		return this.dataSize;
	}

	/**
	 * @return the name of this file system entry w/o the directory part
	 *         (the <em>basename</em>).
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * @return how many bytes this entry will occupy in its parent directory
	 *         data area (or boot sector for the root directory).
	 * @throws IOException if an I/O error occurs.
	 */
	final int getMetadataSize() throws IOException {
		return NAME_OFFSET + this.getEncodedName().limit();
	}

	/**
	 * @return how many blocks this entry does (or will) occupy on the
	 *         file system.
	 * @throws IllegalStateException if this entry is detached.
	 */
	public final long getBlockCount() {
		this.requireNotDetached();

		final int blockSize = this.fileSystem.getBlockSize().getLength();
		return FileUtilities.getBlockCount(this.dataSize, blockSize);
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
		builder.append(this.getType().getType());
		builder.append(this.attributes).append(' ');
		builder.append(this.numberOfLinks).append(' ');
		builder.append(uidToString(this.uid)).append(' ');
		builder.append(gidToString(this.gid)).append(' ');
		builder.append(this.dataSize).append(' ');
		builder.append(dateFormat.format(this.modificationTime)).append(' ');
		/*
		 * Root directory has an empty name.
		 */
		builder.append(this.getName());
		@Nonnull
		@SuppressWarnings("null")
		final String s = builder.toString();
		return s;
	}

	final void setFileSystem(@Nullable final FileSystem fileSystem) {
		this.fileSystem = fileSystem;
	}

	final void setFirstBlockId(final long firstBlockId) {
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

	final boolean isDetached() {
		return this.fileSystem == null;
	}

	/**
	 * @throws IllegalStateException if this entry is detached, i. e. is not
	 *         linked to the underlying local file system.
	 */
	protected final void requireNotDetached() {
		if (this.isDetached()) {
			throw new IllegalStateException("Detached file system entry");
		}
	}
}
