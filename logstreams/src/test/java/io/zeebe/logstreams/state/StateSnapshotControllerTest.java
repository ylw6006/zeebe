/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreInfo;
import io.zeebe.distributedlog.restore.snapshot.impl.NullSnapshotRestoreInfo;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StateSnapshotControllerTest {
  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateSnapshotController snapshotController;
  private StateStorage storage;

  @Before
  public void setup() throws IOException {
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    storage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    snapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage, 2);

    autoCloseableRule.manage(snapshotController);
  }

  @Test
  public void shouldThrowExceptionOnTakeSnapshotIfClosed() {
    // given

    // then
    assertThat(snapshotController.isDbOpened()).isFalse();
    assertThatThrownBy(() -> snapshotController.takeSnapshot(1))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldTakeSnapshot() throws Exception {
    // given
    final String key = "test";
    final int value = 3;
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    snapshotController.takeSnapshot(1);
    snapshotController.close();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldOpenNewDatabaseIfNoSnapshotsToRecoverFrom() throws Exception {
    // given

    // when
    final long lowerBoundSnapshotPosition = snapshotController.recover();

    // then
    assertThat(lowerBoundSnapshotPosition).isEqualTo(-1);
  }

  @Test
  public void shouldRemovePreExistingDatabaseOnRecover() throws Exception {
    // given
    final String key = "test";
    final int value = 1;
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    snapshotController.close();
    final long lowerBound = snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(lowerBound).isEqualTo(-1);
    assertThat(wrapper.mayExist(key)).isFalse();
  }

  @Test
  public void shouldRecoverFromLatestSnapshot() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(snapshotController.openDb());

    wrapper.putInt("x", 1);
    snapshotController.takeSnapshot(1);

    wrapper.putInt("x", 2);
    snapshotController.takeSnapshot(2);

    wrapper.putInt("x", 3);
    snapshotController.takeSnapshot(3);

    snapshotController.close();

    // when
    final long lowerBound = snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(lowerBound).isEqualTo(3);
    assertThat(wrapper.getInt("x")).isEqualTo(3);
  }

  @Test
  public void shouldEnsureMaxSnapshotCount() throws Exception {
    // given
    snapshotController.openDb();
    snapshotController.takeSnapshot(16);
    snapshotController.takeSnapshot(2322);
    snapshotController.takeSnapshot(131);
    snapshotController.takeSnapshot(45);
    snapshotController.takeSnapshot(34);

    // when
    snapshotController.ensureMaxSnapshotCount();

    // then
    assertThat(storage.list()).hasSize(2);
    assertThat(storage.list()).extracting(f -> f.getName()).containsOnly("2322", "131");
    final long latestLowerBound = snapshotController.recover();
    assertThat(latestLowerBound).isEqualTo(2322);
  }

  @Test
  public void shouldCleanUpOrphanedTmpSnapshots() throws Exception {
    // given
    snapshotController.openDb();
    snapshotController.takeSnapshot(16);
    snapshotController.takeSnapshot(2322);
    snapshotController.takeSnapshot(131);
    createSnapshotDirectory("18-tmp");
    createSnapshotDirectory("1-tmp");
    createSnapshotDirectory("132-tmp");

    // when
    snapshotController.ensureMaxSnapshotCount();

    // then
    assertThat(storage.getSnapshotsDirectory().listFiles())
        .extracting(File::getName)
        .containsOnly("2322", "131", "132-tmp");

    final long latestLowerBound = snapshotController.recover();
    assertThat(latestLowerBound).isEqualTo(2322);
  }

  @Test
  public void shouldRecoverFromLatestNotCorruptedSnapshot() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(snapshotController.openDb());

    wrapper.putInt("x", 1);
    snapshotController.takeSnapshot(1);

    wrapper.putInt("x", 2);
    snapshotController.takeSnapshot(2);

    snapshotController.close();
    corruptSnapshot(2);

    // when
    final long lowerBound = snapshotController.recover();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(lowerBound).isEqualTo(1);
    assertThat(wrapper.getInt("x")).isEqualTo(1);
  }

  @Test
  public void shouldFailToRecoverIfAllSnapshotsAreCorrupted() throws Exception {
    // given two snapshots
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt("x", 1);

    snapshotController.takeSnapshot(1);
    snapshotController.close();
    corruptSnapshot(1);

    // when/then
    assertThatThrownBy(() -> snapshotController.recover())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to recover from snapshots");
  }

  @Test
  public void shouldGetValidSnapshotCount() {
    // given
    snapshotController.openDb();

    assertThat(snapshotController.getValidSnapshotsCount()).isEqualTo(0);

    snapshotController.takeSnapshot(1L);
    snapshotController.takeSnapshot(3L);
    snapshotController.takeSnapshot(5L);
    snapshotController.takeTempSnapshot();

    // when/then
    assertThat(snapshotController.getValidSnapshotsCount()).isEqualTo(3);
  }

  @Test
  public void shouldGetLastValidSnapshot() {
    // given
    snapshotController.openDb();

    assertThat(snapshotController.getLastValidSnapshotPosition()).isEqualTo(-1L);

    snapshotController.takeSnapshot(1L);
    snapshotController.takeSnapshot(3L);
    snapshotController.takeSnapshot(5L);
    snapshotController.takeTempSnapshot();

    // when/then
    assertThat(snapshotController.getLastValidSnapshotPosition()).isEqualTo(5L);
  }

  @Test
  public void shouldUpdateRestoreInfoWhenForcingSnapshot() {
    // given
    snapshotController.openDb();

    // when
    snapshotController.takeSnapshot(1L);
    final File snapshotDir = storage.getSnapshotDirectoryFor(1L);

    // then
    final SnapshotRestoreInfo restoreInfo = snapshotController.getLatestSnapshotRestoreInfo();
    assertThat(restoreInfo.getSnapshotId()).isEqualTo(1L);
    assertThat(restoreInfo.getNumChunks())
        .isEqualTo(snapshotDir.listFiles().length)
        .isGreaterThan(0);
  }

  @Test
  public void shouldUpdateRestoreInfoAfterMovingValidSnapshot() throws IOException {
    // given
    snapshotController.openDb();
    snapshotController.takeTempSnapshot();

    // when
    snapshotController.moveValidSnapshot(1L);
    final File snapshotDir = snapshotController.getLastValidSnapshotDirectory();

    // then
    final SnapshotRestoreInfo restoreInfo = snapshotController.getLatestSnapshotRestoreInfo();
    assertThat(restoreInfo.getSnapshotId()).isEqualTo(1L);
    assertThat(restoreInfo.getNumChunks())
        .isEqualTo(snapshotDir.listFiles().length)
        .isGreaterThan(0);
  }

  @Test
  public void shouldReturnNullRestoreInfoIfNoSnapshot() {
    // given
    snapshotController.openDb();

    // when/then
    final SnapshotRestoreInfo restoreInfo = snapshotController.getLatestSnapshotRestoreInfo();
    assertThat(restoreInfo).isEqualToComparingFieldByField(new NullSnapshotRestoreInfo());
  }

  @Test
  public void shouldReturnLastRestoreInfoAfterRestart() throws Exception {
    // given
    final ZeebeDb zeebeDb = snapshotController.openDb();
    writeToDatabase(zeebeDb);
    snapshotController.takeSnapshot(1L);
    final File snapshotDir = snapshotController.getLastValidSnapshotDirectory();

    // when
    snapshotController.close();
    snapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage, 2);

    // then
    final SnapshotRestoreInfo restoreInfo = snapshotController.getLatestSnapshotRestoreInfo();
    assertThat(restoreInfo.getSnapshotId()).isEqualTo(1L);
    assertThat(restoreInfo.getNumChunks())
        .isEqualTo(snapshotDir.listFiles().length)
        .isGreaterThan(0);
  }

  @Test
  public void shouldUpdateRestoreInfoIfRecoverFindsCorruptSnapshot() throws Exception {
    // given
    final ZeebeDb zeebeDb = snapshotController.openDb();
    writeToDatabase(zeebeDb);
    snapshotController.takeSnapshot(1L);
    final File snapshotDir = snapshotController.getLastValidSnapshotDirectory();
    writeToDatabase(zeebeDb);
    snapshotController.takeSnapshot(2L);
    corruptSnapshot(2L);

    // when
    snapshotController.close();
    snapshotController.recover();
    snapshotController.openDb();

    // then
    final SnapshotRestoreInfo restoreInfo = snapshotController.getLatestSnapshotRestoreInfo();
    assertThat(restoreInfo.getSnapshotId()).isEqualTo(1L);
    assertThat(restoreInfo.getNumChunks())
        .isEqualTo(snapshotDir.listFiles().length)
        .isGreaterThan(0);
  }

  private void writeToDatabase(final ZeebeDb db) {
    final String key = "test";
    final int value = 1;
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(db);
    wrapper.putInt(key, value);
  }

  private void corruptSnapshot(long position) throws IOException {
    final File snapshot = storage.getSnapshotDirectoryFor(position);
    assertThat(snapshot).isNotNull();

    final File[] files = snapshot.listFiles((dir, name) -> name.endsWith(".sst"));
    assertThat(files).hasSizeGreaterThan(0);

    Arrays.sort(files, Comparator.reverseOrder());
    final File file = files[0];

    Files.write(file.toPath(), "<--corrupted-->".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
  }

  private File createSnapshotDirectory(final String name) {
    final File directory = new File(storage.getSnapshotsDirectory(), name);
    directory.mkdir();

    return directory;
  }
}
