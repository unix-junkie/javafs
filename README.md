JavaFS
======
[![Build Status](https://api.travis-ci.org/unix-junkie/javafs.png?branch=master)](https://travis-ci.org/unix-junkie/javafs)
[![codecov.io](http://codecov.io/github/unix-junkie/javafs/coverage.svg?branch=master)](http://codecov.io/github/unix-junkie/javafs?branch=master)

If filesystem is 1M large or even larger, 2 bytes per inode will be used to
store the inode address. For testing purposes, it is conveninent to have
only 1 address byte per inode (255 inodes maximum). Specify the size of
1020k in this case:

    java com.github.unix_junkie.javafs.Main mkfs -l 1020k <FILE>.javafs
