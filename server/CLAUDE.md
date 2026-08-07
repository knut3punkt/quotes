## Database

PostgreSQL is used for the server database.

For local database schema changes and migrations, use the development
credentials stored in `.env.claude`.

Use the development database account for CREATE, ALTER, DROP, and migrations.

Keep all schema changes reproducible in version-controlled migration files.
Do not make undocumented ad-hoc schema changes.