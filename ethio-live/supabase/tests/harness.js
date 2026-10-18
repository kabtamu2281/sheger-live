// Test harness: builds a throwaway database from the real migrations and lets tests act as
// a signed-in user (role authenticated + JWT claims), the service role, or the superuser.
import pg from 'pg';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';

const here = path.dirname(fileURLToPath(import.meta.url));
const migrationsDir = path.join(here, '..', 'migrations');
const base = {
  host: process.env.PGHOST ?? '/tmp',
  port: Number(process.env.PGPORT ?? 54329),
  user: process.env.PGUSER ?? 'postgres',
  password: process.env.PGPASSWORD,
};

export async function createTestDb(name) {
  const dbName = `el_${name}_${process.pid}`;
  const admin = new pg.Client({ ...base, database: 'postgres' });
  await admin.connect();
  await admin.query(`drop database if exists ${dbName}`);
  await admin.query(`create database ${dbName}`);
  await admin.end();

  const pool = new pg.Pool({ ...base, database: dbName, max: 30 });
  const files = [path.join(here, 'supabase_stub.sql'),
    ...fs.readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort().map((f) => path.join(migrationsDir, f))];
  for (const f of files) await pool.query(fs.readFileSync(f, 'utf8'));
  return new TestDb(pool, dbName);
}

class TestDb {
  constructor(pool, name) { this.pool = pool; this.name = name; this.counter = 0; }

  async close() {
    await this.pool.end();
    const admin = new pg.Client({ ...base, database: 'postgres' });
    await admin.connect();
    await admin.query(`drop database if exists ${this.name}`);
    await admin.end();
  }

  /** Superuser query (test setup only). */
  async sql(text, params = []) { return (await this.pool.query(text, params)).rows; }

  /** Runs one statement as an authenticated app user, inside its own transaction like PostgREST does. */
  async as(uid, text, params = [], { aal = 'aal1' } = {}) {
    const client = await this.pool.connect();
    try {
      await client.query('begin');
      await client.query(`select set_config('request.jwt.claim.sub', $1, true),
                                 set_config('request.jwt.claims', $2, true),
                                 set_config('role', 'authenticated', true)`,
        [uid, JSON.stringify({ sub: uid, role: 'authenticated', aal })]);
      const res = await client.query(text, params);
      await client.query('commit');
      return res.rows;
    } catch (e) {
      await client.query('rollback').catch(() => {});
      throw e;
    } finally {
      client.release();
    }
  }

  /** Calls a function as the user and returns the single value. */
  async call(uid, fn, args = [], opts) {
    const placeholders = args.map((_, i) => `$${i + 1}`).join(', ');
    const rows = await this.as(uid, `select ${fn}(${placeholders}) as v`, args, opts);
    return rows[0].v;
  }

  /** Runs as the service role (what edge functions use). */
  async service(text, params = []) {
    const client = await this.pool.connect();
    try {
      await client.query('begin');
      await client.query(`select set_config('role', 'service_role', true)`);
      const res = await client.query(text, params);
      await client.query('commit');
      return res.rows;
    } catch (e) {
      await client.query('rollback').catch(() => {});
      throw e;
    } finally {
      client.release();
    }
  }

  /** Creates an auth user and completes app signup. */
  async newUser({ username, adult = true, birthDate } = {}) {
    this.counter += 1;
    const name = username ?? `user${this.counter}_${crypto.randomBytes(2).toString('hex')}`;
    const [{ id }] = await this.sql('insert into auth.users (email) values ($1) returning id', [`${name}@example.test`]);
    const dob = birthDate ?? (adult ? '1995-05-05' : `${new Date().getFullYear() - 16}-01-01`);
    await this.as(id, 'select complete_signup($1, $2, $3::date, $4, $5)', [name, name, dob, 'en', '2026-10']);
    return id;
  }

  /** Buys coins through the real order + service-role credit path. */
  async fund(uid, packageId = 'coins_100') {
    const [order] = await this.as(uid, `select * from create_payment_order($1, 'chapa')`, [packageId]);
    await this.service('select credit_payment_order($1, $2, $3, $4)',
      [order.id, `test-${crypto.randomUUID()}`, order.amount_minor, order.currency]);
    return order;
  }

  async wallet(uid) { return this.call(uid, 'my_wallet'); }

  /** Host starts and confirms a live room. */
  async goLive(uid, { mode = 'video', seats = 4 } = {}) {
    const [room] = await this.as(uid, `select * from start_live($1::room_mode, 'Test live', 'music', 'en', 'public', '{}', $2, null)`, [mode, seats]);
    await this.as(uid, 'select confirm_live($1)', [room.id]);
    return room.id;
  }
}

export const key = () => crypto.randomUUID();

/** Asserts that a promise rejects with a database error whose message contains `code`. */
export async function rejects(promise, code) {
  try {
    await promise;
  } catch (e) {
    if (!String(e.message).includes(code)) {
      throw new Error(`expected error containing "${code}" but got "${e.message}"`);
    }
    return e;
  }
  throw new Error(`expected error "${code}" but the call succeeded`);
}
