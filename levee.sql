CREATE TABLE "trackers" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  "name" TEXT,
  "url" TEXT,
  "user" TEXT,
  "password" TEXT,
  "category" TEXT
);

CREATE TABLE "invitations" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  "token" TEXT,
  "created_at" TIMESTAMP
);

CREATE TABLE "users" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  "email" TEXT,
  "username" TEXT,
  "password" TEXT,
  "token" TEXT,
  "roles" TEXT
);
