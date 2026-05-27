package com.studyhub.aistudyhubbe.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageUsageService {

    public static final double STORAGE_LIMIT_GB = 5.0;
    private static final long CACHE_TTL_MS = 30_000L;

    private final Path uploadRoot;
    private volatile long cachedUsedBytes = 0L;
    private volatile long cachedAtMs = 0L;

    public StorageUsageService(@Value("${app.storage.upload-root}") String uploadRoot) {
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    public long calculateUsedBytes() {
        long now = System.currentTimeMillis();
        if (now - cachedAtMs < CACHE_TTL_MS) {
            return cachedUsedBytes;
        }

        cachedUsedBytes = calculateUsedBytesFromDisk();
        cachedAtMs = now;
        return cachedUsedBytes;
    }

    private long calculateUsedBytesFromDisk() {
        if (!Files.exists(uploadRoot)) {
            return 0L;
        }

        try (Stream<Path> paths = Files.walk(uploadRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .mapToLong(this::fileSize)
                    .sum();
        } catch (IOException ex) {
            return 0L;
        }
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0L;
        }
    }
}
