title: DiskLruCache源码解析
date: 2019-10-15 20:11:24
categories: Android Blog
tags: [Android,开源框架,源码解析,算法]
---
DiskLruCache : https://github.com/JakeWharton/DiskLruCache

DiskLruCache 在 Android 开发中应用的非常广泛，比较常用的就是图片的三级缓存中。比如在 Glide 中，图片在硬盘上的缓存就是采用了 DiskLruCache 。

在 DiskLruCache 中有三种文件，

* journal 文件，里面是记录着我们的操作日志；
* journal.tmp 文件，这个文件是临时文件；
* journal.bkp 文件，这个文件是备份文件。

在我们操作 DiskLruCache 过程中，在修改内存中的缓存的同时，也会在硬盘中的 journal 文件追加我们的操作记录，这样就是下次冷启动，就可以直接从 journal 文件中恢复缓存了。

journal 文件的格式，前几行是文件头，后面是操作记录

	libcore.io.DiskLruCache
	1
	100
	2
	
	CLEAN 3400330d1dfc7f3f7f4b8d4d803dfcf6 832 21054
	DIRTY 335c4c6028171cfddfbaae1a9c313c52
	CLEAN 335c4c6028171cfddfbaae1a9c313c52 3934 2342
	REMOVE 335c4c6028171cfddfbaae1a9c313c52
	DIRTY 1ab96a171faeeee38496d8b330771a7a
	CLEAN 1ab96a171faeeee38496d8b330771a7a 1600 234
	READ 335c4c6028171cfddfbaae1a9c313c52
	
其中1表示diskCache的版本，100表示应用的版本，2表示一个key对应多少个缓存文件。

接下来的每一行都代表着一次操作记录，如

跟上面日志里面看到的一样，DiskLruCache处理文件的过程中会有四种状态：

* DIRTY 创建或者修改一个缓存的时候，会有一条DIRTY记录，后面会跟一个CLEAN或REMOVE的记录。如果没有CLEAN或REMOVE，对应的缓存文件是无效的，会被删掉
* CLEAN 表示对应的缓存操作成功了，后面会带上每个缓存文件的大小，比如上面例子中的 832 21054
* REMOVE 表示对应的缓存被删除了
* READ 表示对应的缓存被访问了

DiskLruCache源码解析
====
现在就来解析一下 DiskLruCache 内部的源码。

成员变量
----
``` java
private final File directory;
private final File journalFile;
private final File journalFileTmp;
private final File journalFileBackup;
private final int appVersion;
private long maxSize;
private final int valueCount;
private long size = 0;
private Writer journalWriter;
private final LinkedHashMap<String, Entry> lruEntries =
    new LinkedHashMap<String, Entry>(0, 0.75f, true);
private int redundantOpCount;
private long nextSequenceNumber = 0;
```

* directory: 缓存对应的目录
* journalFile: 日志文件
* journalFileTmp: journal中间产生的临时文件
* journalFileBackup: journal备份文件
* appVersion: 外部传入的应用版本
* maxSize: DiskLruCache缓存最大的容量
* valueCount: 一个key对应着的文件数量
* size: 当前缓存的总容量
* journalWriter: 负责 journalFile文件的写入
* lruEntries: 内存中对应着 LRU 的缓存实体
* redundantOpCount: 操作次数，当这个值大于2000，会trimToSize，重新构建日志文件

``` java
final ThreadPoolExecutor executorService =
    new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
private final Callable<Void> cleanupCallable = new Callable<Void>() {
  public Void call() throws Exception {
    synchronized (DiskLruCache.this) {
      if (journalWriter == null) {
        return null; // Closed.
      }
      trimToSize();
      if (journalRebuildRequired()) {
        rebuildJournal();
        redundantOpCount = 0;
      }
    }
    return null;
  }
};
```
executorService 是只有一个线程的线程池，专门负责清理工作。会清除过多的缓存以及根据 lruEntries 生成新的 journal 文件。

Entry
===
``` java
private final class Entry {
  private final String key;

  /** Lengths of this entry's files. */
  private final long[] lengths;

  /** True if this entry has ever been published. */
  private boolean readable;

  /** The ongoing edit or null if this entry is not being edited. */
  private Editor currentEditor;

  /** The sequence number of the most recently committed edit to this entry. */
  private long sequenceNumber;

  private Entry(String key) {
    this.key = key;
    this.lengths = new long[valueCount];
  }

  public String getLengths() throws IOException {
    StringBuilder result = new StringBuilder();
    for (long size : lengths) {
      result.append(' ').append(size);
    }
    return result.toString();
  }

  /** Set lengths using decimal numbers like "10123". */
  private void setLengths(String[] strings) throws IOException {
    if (strings.length != valueCount) {
      throw invalidLengths(strings);
    }

    try {
      for (int i = 0; i < strings.length; i++) {
        lengths[i] = Long.parseLong(strings[i]);
      }
    } catch (NumberFormatException e) {
      throw invalidLengths(strings);
    }
  }

  private IOException invalidLengths(String[] strings) throws IOException {
    throw new IOException("unexpected journal line: " + java.util.Arrays.toString(strings));
  }

  public File getCleanFile(int i) {
    return new File(directory, key + "." + i);
  }

  public File getDirtyFile(int i) {
    return new File(directory, key + "." + i + ".tmp");
  }
}
```

Entry 分为 CleanFile 和DirtyFile，当取操作的时候读取的是 CleanFile ，而写操作是先写到DirtyFile ，再重命名为 CleanFile 。这样就算写失败了，至少还有 CleanFile 可以读取，不会污染数据，做到读写分离。

Editor
===
``` java
public final class Editor {
  private final Entry entry;
  private final boolean[] written;
  private boolean hasErrors;
  private boolean committed;

  private Editor(Entry entry) {
    this.entry = entry;
    this.written = (entry.readable) ? null : new boolean[valueCount];
  }

  /**
   * Returns an unbuffered input stream to read the last committed value,
   * or null if no value has been committed.
   */
  public InputStream newInputStream(int index) throws IOException {
    synchronized (DiskLruCache.this) {
      if (entry.currentEditor != this) {
        throw new IllegalStateException();
      }
      if (!entry.readable) {
        return null;
      }
      try {
        return new FileInputStream(entry.getCleanFile(index));
      } catch (FileNotFoundException e) {
        return null;
      }
    }
  }

  /**
   * Returns the last committed value as a string, or null if no value
   * has been committed.
   */
  public String getString(int index) throws IOException {
    InputStream in = newInputStream(index);
    return in != null ? inputStreamToString(in) : null;
  }

  /**
   * Returns a new unbuffered output stream to write the value at
   * {@code index}. If the underlying output stream encounters errors
   * when writing to the filesystem, this edit will be aborted when
   * {@link #commit} is called. The returned output stream does not throw
   * IOExceptions.
   */
  public OutputStream newOutputStream(int index) throws IOException {
    if (index < 0 || index >= valueCount) {
      throw new IllegalArgumentException("Expected index " + index + " to "
              + "be greater than 0 and less than the maximum value count "
              + "of " + valueCount);
    }
    synchronized (DiskLruCache.this) {
      if (entry.currentEditor != this) {
        throw new IllegalStateException();
      }
      if (!entry.readable) {
        written[index] = true;
      }
      File dirtyFile = entry.getDirtyFile(index);
      FileOutputStream outputStream;
      try {
        outputStream = new FileOutputStream(dirtyFile);
      } catch (FileNotFoundException e) {
        // Attempt to recreate the cache directory.
        directory.mkdirs();
        try {
          outputStream = new FileOutputStream(dirtyFile);
        } catch (FileNotFoundException e2) {
          // We are unable to recover. Silently eat the writes.
          return NULL_OUTPUT_STREAM;
        }
      }
      return new FaultHidingOutputStream(outputStream);
    }
  }

  /** Sets the value at {@code index} to {@code value}. */
  public void set(int index, String value) throws IOException {
    Writer writer = null;
    try {
      writer = new OutputStreamWriter(newOutputStream(index), Util.UTF_8);
      writer.write(value);
    } finally {
      Util.closeQuietly(writer);
    }
  }

  /**
   * Commits this edit so it is visible to readers.  This releases the
   * edit lock so another edit may be started on the same key.
   */
  public void commit() throws IOException {
    if (hasErrors) {
      completeEdit(this, false);
      remove(entry.key); // The previous entry is stale.
    } else {
      completeEdit(this, true);
    }
    committed = true;
  }

  /**
   * Aborts this edit. This releases the edit lock so another edit may be
   * started on the same key.
   */
  public void abort() throws IOException {
    completeEdit(this, false);
  }

  public void abortUnlessCommitted() {
    if (!committed) {
      try {
        abort();
      } catch (IOException ignored) {
      }
    }
  }

  private class FaultHidingOutputStream extends FilterOutputStream {
    private FaultHidingOutputStream(OutputStream out) {
      super(out);
    }

    @Override public void write(int oneByte) {
      try {
        out.write(oneByte);
      } catch (IOException e) {
        hasErrors = true;
      }
    }

    @Override public void write(byte[] buffer, int offset, int length) {
      try {
        out.write(buffer, offset, length);
      } catch (IOException e) {
        hasErrors = true;
      }
    }

    @Override public void close() {
      try {
        out.close();
      } catch (IOException e) {
        hasErrors = true;
      }
    }

    @Override public void flush() {
      try {
        out.flush();
      } catch (IOException e) {
        hasErrors = true;
      }
    }
  }
}
```

Editor 是对某个 Entry 编辑时的操作对象。DiskLruCache 想要写入缓存文件，需要获取DiskLruCache.Editor，由 Editor 生成 OutputStream，后续只需要将缓存数据写入 OutputStream 即可。

open(File directory, int appVersion, int valueCount, long maxSize)
====
通过调用 open 方法来获得 DiskLruCache 的实例。open 方法有四个参数：

* directory: 缓存文件的存放目录
* appVersion: 应用程序的版本号
* valueCount: 表示同一个 key 可以对应多少个缓存文件
* maxSize: 表示最大可以缓存多少字节的数据

``` java
public static DiskLruCache open(File directory, int appVersion, int valueCount, long maxSize)
    throws IOException {
  if (maxSize <= 0) {
    throw new IllegalArgumentException("maxSize <= 0");
  }
  if (valueCount <= 0) {
    throw new IllegalArgumentException("valueCount <= 0");
  }

  // If a bkp file exists, use it instead.
  // 流程：优先使用JOURNAL_FILE，删除JOURNAL_FILE_BACKUP备份文件，
  // 否则，JOURNAL_FILE_BACKUP备份文件重命名为JOURNAL_FILE
  File backupFile = new File(directory, JOURNAL_FILE_BACKUP);
  if (backupFile.exists()) {
    File journalFile = new File(directory, JOURNAL_FILE);
    // If journal file also exists just delete backup file.
    if (journalFile.exists()) {
      backupFile.delete();
    } else {
      renameTo(backupFile, journalFile, false);
    }
  }

  // Prefer to pick up where we left off.
  DiskLruCache cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
  if (cache.journalFile.exists()) {
    try {
      // 读取Jonrnal文件,恢复记录到内存 lruEntries 中
      cache.readJournal();
      // 根据 lruEntries 内存中的数据简化 Journal 文件
      cache.processJournal();
      return cache;
    } catch (IOException journalIsCorrupt) {
      System.out
          .println("DiskLruCache "
              + directory
              + " is corrupt: "
              + journalIsCorrupt.getMessage()
              + ", removing");
      cache.delete();
    }
  }

  // Create a new empty cache.
  directory.mkdirs();
  cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
  // 创建一个 journal.tmp 文件，并写入文件头
  cache.rebuildJournal();
  return cache;
}
```

在方法的一开始，会检查 journal.bkp 文件是否存在。如果 journal 文件和 journal.bkp 文件同时存在，会删除 journal.bkp 文件。否则会把 journal.bkp 文件转化成 journal 文件。

接着，如果 journal 文件存在的话，会调用 readJournal() 读取每一行的 journal 文件的记录，把数据恢复到 lruEntries 中。然后 processJournal() 负责将清除掉 journal.tmp 中间文件，清除掉 journal 文件中冗余的记录。并且会计算出当前 DiskLruCache 总文件的大小。

我们来看看 readJournal() 方法和 processJournal() 方法的源码。

readJournal()
----
``` java
private void readJournal() throws IOException {
  StrictLineReader reader = new StrictLineReader(new FileInputStream(journalFile), Util.US_ASCII);
  try {
    // 先校验一下文件头的各式和数据是否正确
    String magic = reader.readLine();
    String version = reader.readLine();
    String appVersionString = reader.readLine();
    String valueCountString = reader.readLine();
    String blank = reader.readLine();
    if (!MAGIC.equals(magic)
        || !VERSION_1.equals(version)
        || !Integer.toString(appVersion).equals(appVersionString)
        || !Integer.toString(valueCount).equals(valueCountString)
        || !"".equals(blank)) {
      throw new IOException("unexpected journal header: [" + magic + ", " + version + ", "
          + valueCountString + ", " + blank + "]");
    }

    int lineCount = 0;
    while (true) {
      try {
        // 读取每一行，进行恢复数据
        readJournalLine(reader.readLine());
        lineCount++;
      } catch (EOFException endOfJournal) {
        break;
      }
    }
    redundantOpCount = lineCount - lruEntries.size();

    // If we ended on a truncated line, rebuild the journal before appending to it.
    if (reader.hasUnterminatedLine()) {
      rebuildJournal();
    } else {
      journalWriter = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(journalFile, true), Util.US_ASCII));
    }
  } finally {
    Util.closeQuietly(reader);
  }
}
```

readJournal 方法中，每一行恢复数据的操作是在 readJournalLine 中进行的。

readJournalLine(String line)
----
``` java
private void readJournalLine(String line) throws IOException {
  int firstSpace = line.indexOf(' ');
  if (firstSpace == -1) {
    throw new IOException("unexpected journal line: " + line);
  }

  int keyBegin = firstSpace + 1;
  int secondSpace = line.indexOf(' ', keyBegin);
  final String key;
  if (secondSpace == -1) {
    key = line.substring(keyBegin);
    if (firstSpace == REMOVE.length() && line.startsWith(REMOVE)) {
      lruEntries.remove(key);
      return;
    }
  } else {
    key = line.substring(keyBegin, secondSpace);
  }

  Entry entry = lruEntries.get(key);
  if (entry == null) {
    entry = new Entry(key);
    lruEntries.put(key, entry);
  }

  if (secondSpace != -1 && firstSpace == CLEAN.length() && line.startsWith(CLEAN)) {
    String[] parts = line.substring(secondSpace + 1).split(" ");
    entry.readable = true;
    entry.currentEditor = null;
    entry.setLengths(parts);
  } else if (secondSpace == -1 && firstSpace == DIRTY.length() && line.startsWith(DIRTY)) {
    entry.currentEditor = new Editor(entry);
  } else if (secondSpace == -1 && firstSpace == READ.length() && line.startsWith(READ)) {
    // This work was already done by calling lruEntries.get().
  } else {
    throw new IOException("unexpected journal line: " + line);
  }
}
```

readJournalLine 方法中，会判断每一行开头是 CLEAN DIRTY REMOVE READ 中的哪一种，然后分别进行不同的操作。具体在这里就不详细讲了。

然后我们再来看看 processJournal 。

processJournal()
-----
``` java
private void processJournal() throws IOException {
  // 删除 journal.tmp 文件
  deleteIfExists(journalFileTmp);
  for (Iterator<Entry> i = lruEntries.values().iterator(); i.hasNext(); ) {
    Entry entry = i.next();
    if (entry.currentEditor == null) {
      for (int t = 0; t < valueCount; t++) {
        // 计算 DiskLruCache 缓存的总大小
        size += entry.lengths[t];
      }
    } else {
      // 如果是冗余的数据，直接删除
      entry.currentEditor = null;
      for (int t = 0; t < valueCount; t++) {
        deleteIfExists(entry.getCleanFile(t));
        deleteIfExists(entry.getDirtyFile(t));
      }
      i.remove();
    }
  }
}
```

另外需要注意的是，当我们首次调用 DiskLruCache.open 方法时，磁盘上是没有任何 journal 文件的，因此会执行 rebuildJournal() 来创建 journal 文件。

rebuildJournal()
----
``` java
private synchronized void rebuildJournal() throws IOException {
  if (journalWriter != null) {
    journalWriter.close();
  }

  Writer writer = new BufferedWriter(
      new OutputStreamWriter(new FileOutputStream(journalFileTmp), Util.US_ASCII));
  try {
    // 写入文件头
    writer.write(MAGIC);
    writer.write("\n");
    writer.write(VERSION_1);
    writer.write("\n");
    writer.write(Integer.toString(appVersion));
    writer.write("\n");
    writer.write(Integer.toString(valueCount));
    writer.write("\n");
    writer.write("\n");

    for (Entry entry : lruEntries.values()) {
      if (entry.currentEditor != null) {
        writer.write(DIRTY + ' ' + entry.key + '\n');
      } else {
        writer.write(CLEAN + ' ' + entry.key + entry.getLengths() + '\n');
      }
    }
  } finally {
    writer.close();
  }

  if (journalFile.exists()) {
    renameTo(journalFile, journalFileBackup, true);
  }
  renameTo(journalFileTmp, journalFile, false);
  journalFileBackup.delete();

  journalWriter = new BufferedWriter(
      new OutputStreamWriter(new FileOutputStream(journalFile, true), Util.US_ASCII));
}
```

get()
=====
``` java
public synchronized Snapshot get(String key) throws IOException {
  checkNotClosed();
  validateKey(key);
  Entry entry = lruEntries.get(key);
  if (entry == null) {
    return null;
  }

  if (!entry.readable) {
    return null;
  }

  // Open all streams eagerly to guarantee that we see a single published
  // snapshot. If we opened streams lazily then the streams could come
  // from different edits.
  InputStream[] ins = new InputStream[valueCount];
  try {
    for (int i = 0; i < valueCount; i++) {
      ins[i] = new FileInputStream(entry.getCleanFile(i));
    }
  } catch (FileNotFoundException e) {
    // A file must have been deleted manually!
    for (int i = 0; i < valueCount; i++) {
      if (ins[i] != null) {
        Util.closeQuietly(ins[i]);
      } else {
        break;
      }
    }
    return null;
  }

  redundantOpCount++;
  journalWriter.append(READ + ' ' + key + '\n');
  if (journalRebuildRequired()) {
    executorService.submit(cleanupCallable);
  }

  return new Snapshot(key, entry.sequenceNumber, ins, entry.lengths);
}
```

get()的方法内部就是获取到指定的 Entry，拿着 Entry 的 cleanFile 生成 InputStream ，封装到Snapshot 返回。

journalRebuildRequired() 表示是否要清理日志，如果需要就利用 cleanupCallable 清理。

edit()
====
``` java
public Editor edit(String key) throws IOException {
  return edit(key, ANY_SEQUENCE_NUMBER);
}

private synchronized Editor edit(String key, long expectedSequenceNumber) throws IOException {
  checkNotClosed();
  validateKey(key);
  Entry entry = lruEntries.get(key);
  if (expectedSequenceNumber != ANY_SEQUENCE_NUMBER && (entry == null
      || entry.sequenceNumber != expectedSequenceNumber)) {
    return null; // Snapshot is stale.
  }
  if (entry == null) {
    entry = new Entry(key);
    lruEntries.put(key, entry);
  } else if (entry.currentEditor != null) {
    return null; // Another edit is in progress.
  }

  Editor editor = new Editor(entry);
  entry.currentEditor = editor;

  // Flush the journal before creating files to prevent file leaks.
  journalWriter.write(DIRTY + ' ' + key + '\n');
  journalWriter.flush();
  return editor;
}
```

edit 方法的逻辑也是十分清晰的，加入 lruEntries ，生成Editor，向 journal 文件写入 DIRTY 记录。

后续 Editor 会获取 Entry 的 DirtyFile 生成一个 OutputStream 提供给外部写入。


remove(String key)
====
``` java
public synchronized boolean remove(String key) throws IOException {
  checkNotClosed();
  validateKey(key);
  Entry entry = lruEntries.get(key);
  if (entry == null || entry.currentEditor != null) {
    return false;
  }

  for (int i = 0; i < valueCount; i++) {
    File file = entry.getCleanFile(i);
    if (file.exists() && !file.delete()) {
      throw new IOException("failed to delete " + file);
    }
    size -= entry.lengths[i];
    entry.lengths[i] = 0;
  }

  redundantOpCount++;
  journalWriter.append(REMOVE + ' ' + key + '\n');
  lruEntries.remove(key);

  if (journalRebuildRequired()) {
    executorService.submit(cleanupCallable);
  }

  return true;
}
```

删除 key 对应的所有文件，然后把操作记录到 journal 文件中。

commit()
====
``` java
public void commit() throws IOException {
  if (hasErrors) {
    completeEdit(this, false);
    remove(entry.key); // The previous entry is stale.
  } else {
    completeEdit(this, true);
  }
  committed = true;
}

private synchronized void completeEdit(Editor editor, boolean success) throws IOException {
  Entry entry = editor.entry;
  if (entry.currentEditor != editor) {
    throw new IllegalStateException();
  }

  // If this edit is creating the entry for the first time, every index must have a value.
  // 下面是editor和entry的dirtyFile做检查
  if (success && !entry.readable) {
    for (int i = 0; i < valueCount; i++) {
      if (!editor.written[i]) {
        editor.abort();
        throw new IllegalStateException("Newly created entry didn't create value for index " + i);
      }
      if (!entry.getDirtyFile(i).exists()) {
        editor.abort();
        return;
      }
    }
  }

	// 将entry的DirtyFile重命名为CleanFile，计算总的size大小，表示写成功
  for (int i = 0; i < valueCount; i++) {
    File dirty = entry.getDirtyFile(i);
    if (success) {
      if (dirty.exists()) {
        File clean = entry.getCleanFile(i);
        dirty.renameTo(clean);
        long oldLength = entry.lengths[i];
        long newLength = clean.length();
        entry.lengths[i] = newLength;
        size = size - oldLength + newLength;
      }
    } else {
      deleteIfExists(dirty);
    }
  }

  redundantOpCount++;
  // 将entry.currentEditor 赋空，表示写完成了。不处于写状态
  entry.currentEditor = null;
  // 成功则将entry设置可读，增加一个CLEAN记录，否则失败了就移除掉entry，增加一个REMOVE记录
  if (entry.readable | success) {
    entry.readable = true;
    journalWriter.write(CLEAN + ' ' + entry.key + entry.getLengths() + '\n');
    if (success) {
      entry.sequenceNumber = nextSequenceNumber++;
    }
  } else {
    lruEntries.remove(entry.key);
    journalWriter.write(REMOVE + ' ' + entry.key + '\n');
  }
  journalWriter.flush();
	// 如果超过了容量限制，走清理工作
  if (size > maxSize || journalRebuildRequired()) {
    executorService.submit(cleanupCallable);
  }
}
```

最后
===
DiskLruCache 核心思想就是利用 LinkedHashMap 来做到 LRU，然后每个 Entry 中做到读写分离，互不影响。最后就是把操作的记录完整地写入文件中，进行持久化，做到下次使用时恢复数据到内存中。


