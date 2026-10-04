// Applies db/schema.sql. Uses the direct (non-pooled) connection.
// Usage: npm run db:migrate
import { readFile } from "node:fs/promises";
import postgres from "postgres";

const url = process.env.DATABASE_URL;
if (!url) {
  console.error("DATABASE_URL is not set (put it in .env or .env.local)");
  process.exit(1);
}

const sql = postgres(url, { max: 1, onnotice: () => {} });
try {
  const schema = await readFile(new URL("./schema.sql", import.meta.url), "utf8");
  await sql.unsafe(schema);
  const tables = await sql`
    select table_name from information_schema.tables
    where table_schema = 'public' and table_name like 'comment%'
    order by table_name`;
  console.log("Schema applied. Tables:", tables.map((t) => t.table_name).join(", "));
} finally {
  await sql.end();
}
