import { createHash, randomBytes } from 'node:crypto';
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

/**
 * Minimal persistent store. It keeps only what billing and quotas need:
 * a hash of each device token, the plan, and usage counters. No user content is ever stored.
 */
export class Store {
  constructor(file) {
    this.file = file;
    this.data = { devices: {} };
    if (file) {
      try {
        this.data = JSON.parse(readFileSync(file, 'utf8'));
      } catch {
        /* start empty */
      }
    }
  }

  static hash(token) {
    return createHash('sha256').update(token).digest('hex');
  }

  save() {
    if (!this.file) return;
    mkdirSync(dirname(this.file), { recursive: true });
    const tmp = `${this.file}.tmp`;
    writeFileSync(tmp, JSON.stringify(this.data));
    renameSync(tmp, this.file);
  }

  createDevice() {
    const token = randomBytes(32).toString('base64url');
    this.data.devices[Store.hash(token)] = { plan: 'free', period: null, used: 0, bonus: 0, createdAt: Date.now() };
    this.save();
    return token;
  }

  device(token) {
    if (typeof token !== 'string' || token.length < 20) return null;
    return this.data.devices[Store.hash(token)] ?? null;
  }

  deleteDevice(token) {
    const key = Store.hash(token);
    const existed = key in this.data.devices;
    delete this.data.devices[key];
    this.save();
    return existed;
  }
}
