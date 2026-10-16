package theevgeny.zendb;

import theevgeny.zendb.Types.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class ZenDB {

    private final String name;
    private final Path directory;
    private final NMap root;
    private final WALManager walManager;

    public ZenDB(String name, String path) {
        this.name = name;
        this.directory = Paths.get(path);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create DB directory", e);
        }

        ConcurrentHashMap<Object, Object> rootBacking = loadRoot();
        this.walManager = new WALManager(this, directory.resolve(name + ".wal"));
        this.root = new NMap(this, new ArrayList<>(), rootBacking);
        walManager.replay(root);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Object, Object> loadRoot() {
        Path dbFile = directory.resolve(name + ".db");
        if (!Files.exists(dbFile)) return new ConcurrentHashMap<>();
        try {
            byte[] data = Files.readAllBytes(dbFile);
            Object decoded = Serializer.decode(data);
            if (decoded instanceof ConcurrentHashMap<?, ?> m) {
                return (ConcurrentHashMap<Object, Object>) m;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + dbFile, e);
        }
        return new ConcurrentHashMap<>();
    }

    public ZenSection getRootSection() { return new ZenSection(root); }

    /**
     * Atomic save:
     *   1. Write full state to name.db.tmp, fsync.
     *   2. Move name.db -> name.db.old (if exists).
     *   3. Move name.db.tmp -> name.db.
     *   4. Delete name.db.old.
     *   5. Clear WAL.
     */
    public synchronized void save() {
        try {
            byte[] data = Serializer.encode(root.getBacking());

            Path dbFile  = directory.resolve(name + ".db");
            Path tmpFile = directory.resolve(name + ".db.tmp");
            Path oldFile = directory.resolve(name + ".db.old");

            try (FileOutputStream fos = new FileOutputStream(tmpFile.toFile())) {
                fos.write(data);
                fos.getFD().sync();
            }

            if (Files.exists(dbFile)) {
                Files.move(dbFile, oldFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmpFile, dbFile, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(oldFile);

            walManager.clear();
        } catch (IOException e) {
            throw new RuntimeException("save() failed", e);
        }
    }

    public void close() { walManager.close(); }

    public WALManager getWalManager() { return walManager; }
    public NMap getRoot() { return root; }
    public String getName() { return name; }
}