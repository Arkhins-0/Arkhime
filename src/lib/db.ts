import postgres from "postgres";

// The pooled URL suits serverless functions; fall back to the direct one.
const url = process.env.DATABASE_URL_POOLED ?? process.env.DATABASE_URL;

declare global {
  var __arkhimeSql: postgres.Sql | undefined;
}

function create() {
  if (!url) throw new Error("DATABASE_URL_POOLED or DATABASE_URL must be set");
  // prepare: false is required behind PgBouncer-style poolers (Neon's -pooler host)
  return postgres(url, { max: 5, idle_timeout: 20, prepare: false });
}

// Reuse one client per server instance (and across hot reloads in dev)
export function db(): postgres.Sql {
  globalThis.__arkhimeSql ??= create();
  return globalThis.__arkhimeSql;
}
