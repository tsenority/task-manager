# Authentication API

- `POST /api/auth/register` accepts `username`, `email`, and `password`, and returns `201 Created` with only the new user's id, username, and email.
- `POST /api/auth/login` accepts `username` and `password`, and returns `200 OK` with the same safe user fields. Invalid credentials return a generic `401 Unauthorized`.
- Usernames are trimmed but not case-normalized. Username lookup and uniqueness follow the configured database collation, so case sensitivity can vary by database configuration.
- Emails are trimmed and lowercased with `Locale.ROOT` before validation, storage, and duplicate checks.
- Registration passwords must be at least 8 Unicode characters and no more than 72 UTF-8 bytes. Passwords are never trimmed or normalized; BCrypt hashes are stored and never returned.
- Login verifies credentials only; it does not create a session or issue a token. Existing task endpoints remain unsecured by this feature.
